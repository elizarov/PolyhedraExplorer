const webdriverUrl = process.env.WEBDRIVER_URL ?? "http://127.0.0.1:9515"
const applicationUrl = process.env.APPLICATION_URL ?? "http://127.0.0.1:8765/"

const cases = [
    {
        name: "seed construction",
        hash: "",
        counts: { faces: 4, edges: 6, vertices: 4 },
        labels: ["Tetrahedron"],
    },
    {
        name: "primitive transformation",
        hash: "#/s(I)t(t)",
        counts: { faces: 32, edges: 90, vertices: 60 },
        labels: ["Truncated", "Icosahedron"],
    },
    {
        name: "canonicalization",
        hash: "#/s(I)t(t,o)",
        counts: { faces: 32, edges: 90, vertices: 60 },
        labels: ["Canonical", "Truncated", "Icosahedron"],
    },
]

const sleep = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds))

async function webdriver(method, path, body) {
    const response = await fetch(`${webdriverUrl}${path}`, {
        method,
        headers: body === undefined ? undefined : { "content-type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
    })
    const payload = await response.json().catch(() => null)
    if (!response.ok || payload?.value?.error) {
        throw new Error(`WebDriver ${method} ${path} failed: ${JSON.stringify(payload)}`)
    }
    return payload?.value
}

async function waitForWebDriver() {
    const deadline = Date.now() + 15_000
    while (Date.now() < deadline) {
        try {
            if ((await webdriver("GET", "/status"))?.ready) return
        } catch (_) {
            // ChromeDriver may still be starting.
        }
        await sleep(250)
    }
    throw new Error("ChromeDriver did not become ready")
}

function countPattern(label, count) {
    return `${label}:\\s*${count}(?:\\/\\d+)?(?:\\s|$)`
}

async function runCase(sessionId, testCase, index) {
    const url = new URL(applicationUrl)
    url.searchParams.set("acceptance", index.toString())
    url.hash = testCase.hash
    await webdriver("POST", `/session/${sessionId}/url`, { url: url.toString() })

    const deadline = Date.now() + 30_000
    let state
    while (Date.now() < deadline) {
        state = await webdriver("POST", `/session/${sessionId}/execute/sync`, {
            script: `
                const status = document.querySelector(".core-status")?.textContent?.trim() ?? null;
                const canvas = document.querySelector("canvas");
                const text = document.body?.innerText ?? "";
                const patterns = arguments[0].patterns.map(pattern => new RegExp(pattern));
                return {
                    status,
                    hasCanvas: canvas !== null && canvas.width > 0 && canvas.height > 0,
                    hasCounts: patterns.every(pattern => pattern.test(text)),
                    hasLabels: arguments[0].labels.every(label => text.includes(label)),
                    body: text.slice(0, 1000),
                };
            `,
            args: [{
                patterns: [
                    countPattern("F", testCase.counts.faces),
                    countPattern("E", testCase.counts.edges),
                    countPattern("V", testCase.counts.vertices),
                ],
                labels: testCase.labels,
            }],
        })
        if (state.status?.startsWith("Wasm core error:")) throw new Error(state.status)
        if (state.status === null && state.hasCanvas && state.hasCounts && state.hasLabels) {
            console.log(`PASS: ${testCase.name}`)
            return
        }
        await sleep(250)
    }
    throw new Error(`${testCase.name} did not become ready: ${JSON.stringify(state)}`)
}

let sessionId
try {
    await waitForWebDriver()
    const chromeOptions = {
        args: ["--headless=new", "--no-sandbox", "--disable-dev-shm-usage"],
    }
    if (process.env.CHROME_BIN) chromeOptions.binary = process.env.CHROME_BIN

    const session = await webdriver("POST", "/session", {
        capabilities: {
            alwaysMatch: {
                browserName: "chrome",
                "goog:chromeOptions": chromeOptions,
            },
        },
    })
    sessionId = session.sessionId
    for (const [index, testCase] of cases.entries()) {
        await runCase(sessionId, testCase, index)
    }
    console.log("Production acceptance test passed")
} finally {
    if (sessionId) {
        await webdriver("DELETE", `/session/${sessionId}`).catch(() => {})
    }
}
