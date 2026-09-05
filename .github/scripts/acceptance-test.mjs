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
    {
        name: "farthest supported icosahedron main-line stellation",
        hash: "#/s(I)t(S~l=10)",
        counts: { faces: 20, edges: 90, vertices: 60 },
        labels: ["Stellated", "Icosahedron"],
    },
    {
        name: "regular compound stellation",
        hash: "#/s(I)t(S~l=3)",
        counts: { faces: 40, edges: 60, vertices: 30 },
        labels: ["Stellated", "Icosahedron", "Five octahedra"],
    },
    {
        name: "compound transformation",
        hash: "#/s(C5C)t(t)",
        counts: { faces: 70, edges: 180, vertices: 120 },
        labels: ["Truncated", "Five cubes"],
    },
    {
        name: "higher-winding resolution",
        hash: "#/s(SA7_3)t(R)",
        counts: { faces: 72, edges: 126, vertices: 56 },
        labels: ["Resolved", "Antiprism 7/3"],
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

let navigationIndex = 0

async function runCase(sessionId, testCase) {
    const url = new URL(applicationUrl)
    // Every case must reload the application, including successive export cases. Changing only
    // the hash is a same-document navigation and does not rerun configuration initialization.
    url.searchParams.set("acceptance", (navigationIndex++).toString())
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

async function runStlExportCase(sessionId, compound = false) {
    const testCase = compound ? {
        name: "compound rims for STL export",
        hash: "#/s(C2T)hf(α)",
        counts: { faces: 8, edges: 12, vertices: 8 },
        labels: ["Two tetrahedra"],
    } : {
        name: "immersed presentation for STL export",
        hash: "#/s(SP5_2)hf(α)",
        counts: { faces: 7, edges: 15, vertices: 10 },
        labels: ["Prism 5/2"],
    }
    await runCase(sessionId, testCase)
    await webdriver("POST", `/session/${sessionId}/execute/sync`, {
        script: `
            window.__acceptedStlDownload = null;
            window.__acceptedStlHref = null;
            window.__acceptedStlSummary = null;
            window.__acceptedStlError = null;
            const originalClick = HTMLAnchorElement.prototype.click;
            HTMLAnchorElement.prototype.click = function() {
                if (this.download?.endsWith(".stl")) {
                    window.__acceptedStlDownload = this.download;
                    window.__acceptedStlHref = this.href;
                    fetch(this.href)
                        .then(response => {
                            if (!response.ok) throw new Error(
                                "download returned HTTP " + response.status
                            );
                            return response.text();
                        })
                        .then(text => {
                            const lines = text.trim().split(/\\r?\\n/);
                            const facetCount = lines.filter(line => line.startsWith("facet normal ")).length;
                            const vertexCount = lines.filter(line => line.startsWith("vertex ")).length;
                            const solidName = lines[0]?.startsWith("solid ") ? lines[0].slice(6) : null;
                            const endName = lines.at(-1)?.startsWith("endsolid ")
                                ? lines.at(-1).slice(9)
                                : null;
                            window.__acceptedStlSummary = {
                                length: text.length,
                                facetCount,
                                vertexCount,
                                solidName,
                                endName,
                                valid: text.length > 0 && facetCount > 0 &&
                                    vertexCount === facetCount * 3 && solidName === endName,
                            };
                        })
                        .catch(error => {
                            window.__acceptedStlError = String(error?.message ?? error);
                        });
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
    let state
    while (Date.now() < deadline) {
        state = await webdriver("POST", `/session/${sessionId}/execute/sync`, {
            script: `
                return {
                    download: window.__acceptedStlDownload,
                    href: window.__acceptedStlHref,
                    summary: window.__acceptedStlSummary,
                    downloadError: window.__acceptedStlError,
                    error: document.querySelector(".save-error")?.textContent?.trim() ?? null,
                    status: document.querySelector(".core-status")?.textContent?.trim() ?? null,
                };
            `,
            args: [],
        })
        if (state.status?.startsWith("Wasm core error:")) throw new Error(state.status)
        if (state.error) throw new Error(`STL export failed: ${state.error}`)
        if (state.downloadError) throw new Error(`Could not inspect STL download: ${state.downloadError}`)
        if (state.download?.endsWith(".stl") && state.summary?.valid) {
            console.log(
                `PASS: immersed STL worker export (${state.download}, ` +
                `${state.summary.facetCount} facets, ${state.summary.length} bytes)`,
            )
            return
        }
        await sleep(250)
    }
    throw new Error(`immersed STL worker export did not complete: ${JSON.stringify(state)}`)
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
    for (const testCase of cases) {
        await runCase(sessionId, testCase)
    }
    await runStlExportCase(sessionId)
    await runStlExportCase(sessionId, true)
    console.log("Production acceptance test passed")
} finally {
    if (sessionId) {
        await webdriver("DELETE", `/session/${sessionId}`).catch(() => {})
    }
}
