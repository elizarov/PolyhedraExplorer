param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Configuration,

    [Parameter(Position = 1)]
    [string] $Output = "build/rendered/configuration.png",

    [string] $ApplicationUrl,

    [string] $Distribution = "build/dist/browser/development",

    [switch] $NoBuild,

    [int] $Width = 1600,

    [int] $Height = 1200,

    [int] $WaitMilliseconds = 30000,

    [string] $BeforeCaptureScript,

    [string] $Chrome
)

$ErrorActionPreference = "Stop"

function Resolve-Chrome {
    param([string] $Requested)

    $candidates = @(
        $Requested,
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
    ) | Where-Object { $_ }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw "Google Chrome was not found. Pass its executable path with -Chrome."
}

function Receive-WebSocketJson {
    param([Net.WebSockets.ClientWebSocket] $Socket)

    $buffer = New-Object byte[] 65536
    $stream = New-Object IO.MemoryStream
    try {
        do {
            $segment = [ArraySegment[byte]]::new($buffer)
            $received = $Socket.ReceiveAsync(
                $segment,
                [Threading.CancellationToken]::None
            ).GetAwaiter().GetResult()
            if ($received.MessageType -eq [Net.WebSockets.WebSocketMessageType]::Close) {
                throw "Chrome closed its DevTools connection before rendering completed."
            }
            $stream.Write($buffer, 0, $received.Count)
        } while (-not $received.EndOfMessage)
        return [Text.Encoding]::UTF8.GetString($stream.ToArray()) | ConvertFrom-Json
    } finally {
        $stream.Dispose()
    }
}

$script:devToolsCommandId = 0
function Invoke-DevTools {
    param(
        [Net.WebSockets.ClientWebSocket] $Socket,
        [string] $Method,
        [hashtable] $Parameters = @{}
    )

    $id = ++$script:devToolsCommandId
    $payload = @{
        id = $id
        method = $Method
        params = $Parameters
    } | ConvertTo-Json -Compress -Depth 10
    $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
    $Socket.SendAsync(
        [ArraySegment[byte]]::new($bytes),
        [Net.WebSockets.WebSocketMessageType]::Text,
        $true,
        [Threading.CancellationToken]::None
    ).GetAwaiter().GetResult() | Out-Null
    while ($true) {
        $message = Receive-WebSocketJson $Socket
        if ($message.id -ne $id) { continue }
        if ($message.error) {
            throw "Chrome DevTools $Method failed: $($message.error.message)"
        }
        return $message.result
    }
}

if ($Width -le 0 -or $Height -le 0) {
    throw "Width and Height must be positive."
}
if ($WaitMilliseconds -lt 0) {
    throw "WaitMilliseconds cannot be negative."
}

$root = Split-Path -Parent $PSScriptRoot
$outputPath = if ([IO.Path]::IsPathRooted($Output)) {
    [IO.Path]::GetFullPath($Output)
} else {
    [IO.Path]::GetFullPath((Join-Path $root $Output))
}
[IO.Directory]::CreateDirectory((Split-Path -Parent $outputPath)) | Out-Null

$chromePath = Resolve-Chrome $Chrome
$profile = Join-Path ([IO.Path]::GetTempPath()) ("polyhedra-render-" + [Guid]::NewGuid().ToString("N"))
[IO.Directory]::CreateDirectory($profile) | Out-Null
$serverProcess = $null
$chromeProcess = $null
$socket = $null

try {
    if (-not $ApplicationUrl) {
        if (-not $NoBuild) {
            & (Join-Path $root "gradlew.bat") browserDevelopmentDistribution
            if ($LASTEXITCODE -ne 0) {
                throw "The development distribution build failed with code $LASTEXITCODE."
            }
        }
        $distributionPath = if ([IO.Path]::IsPathRooted($Distribution)) {
            [IO.Path]::GetFullPath($Distribution)
        } else {
            [IO.Path]::GetFullPath((Join-Path $root $Distribution))
        }
        if (-not (Test-Path -LiteralPath (Join-Path $distributionPath "index.html") -PathType Leaf)) {
            throw "No browser distribution was found at $distributionPath. Remove -NoBuild or pass -ApplicationUrl."
        }
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
        $listener.Start()
        $port = ([Net.IPEndPoint] $listener.LocalEndpoint).Port
        $listener.Stop()
        $python = (Get-Command python -ErrorAction Stop).Source
        $serverProcess = Start-Process -FilePath $python -ArgumentList @(
            "-m", "http.server", $port, "--bind", "127.0.0.1", "--directory", "`"$distributionPath`""
        ) -WindowStyle Hidden -PassThru
        $ApplicationUrl = "http://127.0.0.1:$port/"
        $deadline = [DateTime]::UtcNow.AddSeconds(10)
        $response = $null
        while ([DateTime]::UtcNow -lt $deadline) {
            try {
                $response = Invoke-WebRequest -UseBasicParsing -Uri $ApplicationUrl -TimeoutSec 1
                if ($response.StatusCode -eq 200) { break }
            } catch {
                Start-Sleep -Milliseconds 100
            }
        }
        if (-not $response -or $response.StatusCode -ne 200) {
            throw "The temporary renderer server did not start at $ApplicationUrl."
        }
    }

    $base = [UriBuilder]::new($ApplicationUrl)
    $base.Fragment = "/$Configuration"
    $url = $base.Uri.AbsoluteUri
    $debugListener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $debugListener.Start()
    $debugPort = ([Net.IPEndPoint] $debugListener.LocalEndpoint).Port
    $debugListener.Stop()
    $arguments = @(
        "--headless=new",
        "--disable-gpu-sandbox",
        "--hide-scrollbars",
        "--force-device-scale-factor=1",
        "--remote-allow-origins=*",
        "--remote-debugging-port=$debugPort",
        "--user-data-dir=`"$profile`"",
        "--window-size=$Width,$Height",
        $url
    )
    $chromeProcess = Start-Process -FilePath $chromePath -ArgumentList $arguments `
        -WindowStyle Hidden -PassThru

    $deadline = [DateTime]::UtcNow.AddMilliseconds($WaitMilliseconds)
    $page = $null
    while ([DateTime]::UtcNow -lt $deadline -and -not $page) {
        try {
            $pages = Invoke-RestMethod -Uri "http://127.0.0.1:$debugPort/json" -TimeoutSec 1
            $page = @($pages) |
                Where-Object { $_.type -eq "page" } |
                Select-Object -First 1
        } catch {
            Start-Sleep -Milliseconds 100
        }
    }
    if (-not $page) {
        throw "Chrome DevTools did not become ready within $WaitMilliseconds ms."
    }

    $socket = [Net.WebSockets.ClientWebSocket]::new()
    $socket.ConnectAsync(
        [Uri] $page.webSocketDebuggerUrl,
        [Threading.CancellationToken]::None
    ).GetAwaiter().GetResult() | Out-Null
    Invoke-DevTools $socket "Page.enable" | Out-Null
    Invoke-DevTools $socket "Runtime.enable" | Out-Null
    Invoke-DevTools $socket "Emulation.setDeviceMetricsOverride" @{
        width = $Width
        height = $Height
        deviceScaleFactor = 1
        mobile = $false
    } | Out-Null

    $state = $null
    while ([DateTime]::UtcNow -lt $deadline) {
        $evaluation = Invoke-DevTools $socket "Runtime.evaluate" @{
            expression = @"
(() => {
    const status = document.querySelector('.core-status')?.textContent?.trim() ?? null;
    const canvas = document.querySelector('canvas');
    return {
        ready: document.readyState === 'complete' && status === null &&
            canvas !== null && canvas.width === $Width && canvas.height === $Height,
        status,
        canvasWidth: canvas?.width ?? 0,
        canvasHeight: canvas?.height ?? 0,
    };
})()
"@
            returnByValue = $true
        }
        $state = $evaluation.result.value
        if ($state.status -and $state.status.StartsWith("Wasm core error:")) {
            throw $state.status
        }
        if ($state.ready) { break }
        Start-Sleep -Milliseconds 100
    }
    if (-not $state.ready) {
        throw "The scene did not become ready within $WaitMilliseconds ms: $($state | ConvertTo-Json -Compress)"
    }

    if ($BeforeCaptureScript) {
        $evaluation = Invoke-DevTools $socket "Runtime.evaluate" @{
            expression = $BeforeCaptureScript
            returnByValue = $true
        }
        if ($evaluation.exceptionDetails) {
            throw "BeforeCaptureScript failed: $($evaluation.exceptionDetails.text)"
        }
        Start-Sleep -Milliseconds 250
    }

    $capture = Invoke-DevTools $socket "Page.captureScreenshot" @{
        format = "png"
        fromSurface = $true
        captureBeyondViewport = $false
    }
    [IO.File]::WriteAllBytes($outputPath, [Convert]::FromBase64String($capture.data))
    $file = Get-Item -LiteralPath $outputPath
    Write-Output ("Rendered {0} ({1} bytes)" -f $file.FullName, $file.Length)
} finally {
    if ($socket) {
        try {
            if ($socket.State -eq [Net.WebSockets.WebSocketState]::Open) {
                $socket.CloseAsync(
                    [Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
                    "render complete",
                    [Threading.CancellationToken]::None
                ).GetAwaiter().GetResult() | Out-Null
            }
        } finally {
            $socket.Dispose()
        }
    }
    if ($chromeProcess -and -not $chromeProcess.HasExited) {
        Stop-Process -Id $chromeProcess.Id -Force
        $chromeProcess.WaitForExit()
    }
    if ($serverProcess -and -not $serverProcess.HasExited) {
        Stop-Process -Id $serverProcess.Id -Force
        $serverProcess.WaitForExit()
    }
    if (Test-Path -LiteralPath $profile) {
        Remove-Item -LiteralPath $profile -Recurse -Force
    }
}
