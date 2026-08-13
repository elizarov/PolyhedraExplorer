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
    {
        name: "Kepler-Poinsot construction",
        hash: "#/s(D)t(S,G)",
        counts: { faces: 12, edges: 30, vertices: 20 },
        labels: ["Greatened", "Stellated", "Dodecahedron"],
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

async function runStlExportCase(sessionId) {
    const testCase = {
        name: "immersed STL worker export",
        hash: "#/s(SP5_2)hf(α)",
        counts: { faces: 7, edges: 15, vertices: 10 },
        labels: ["Prism 5/2"],
    }
    await runCase(sessionId, testCase, cases.length)
    await webdriver("POST", `/session/${sessionId}/execute/sync`, {
        script: `
            window.__acceptedStlDownload = null;
            window.__acceptedStlHref = null;
            const originalClick = HTMLAnchorElement.prototype.click;
            HTMLAnchorElement.prototype.click = function() {
                if (this.download?.endsWith(".stl")) {
                    window.__acceptedStlDownload = this.download;
                    window.__acceptedStlHref = this.href;
                    return;
                }
                return originalClick.call(this);
            };
            document.dispatchEvent(new KeyboardEvent("keydown", { key: "x", code: "KeyX", bubbles: true }));
        `,
        args: [],
    })

    const controlDeadline = Date.now() + 5_000
    let started = false
    while (!started && Date.now() < controlDeadline) {
        started = await webdriver("POST", `/session/${sessionId}/execute/sync`, {
            script: `
                const button = [...document.querySelectorAll("button")]
                    .find(candidate => candidate.textContent.trim() === "Export to STL");
                if (!button) return false;
                button.click();
                return true;
            `,
            args: [],
        })
        if (!started) await sleep(50)
    }
    if (!started) throw new Error("STL export control did not open")

    const deadline = Date.now() + 30_000
    while (Date.now() < deadline) {
        const state = await webdriver("POST", `/session/${sessionId}/execute/sync`, {
            script: `
                return {
                    download: window.__acceptedStlDownload,
                    href: window.__acceptedStlHref,
                    error: document.querySelector(".save-error")?.textContent?.trim() ?? null,
                    status: document.querySelector(".core-status")?.textContent?.trim() ?? null,
                };
            `,
            args: [],
        })
        if (state.status?.startsWith("Wasm core error:")) throw new Error(state.status)
        if (state.error) throw new Error(`STL export failed: ${state.error}`)
        if (state.download && state.href?.startsWith("data:text/plain;charset=utf-8,solid%20")) {
            console.log(`PASS: ${testCase.name} (${state.download})`)
            return
        }
        await sleep(250)
    }
    throw new Error("immersed STL worker export did not complete")
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
    await runStlExportCase(sessionId)
    console.log("Production acceptance test passed")
} finally {
    if (sessionId) {
        await webdriver("DELETE", `/session/${sessionId}`).catch(() => {})
    }
}
