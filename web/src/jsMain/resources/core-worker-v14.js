let coreModulePromise

self.onmessage = async event => {
    let request
    try {
        request = JSON.parse(event.data)
        coreModulePromise ??= import("./core-v14/PolyhedraExplorer-core.mjs")
        const core = await coreModulePromise
        let response
        if (request.kind === "stl") {
            response = await core.convertStlJson(request.requestJson, done => {
                self.postMessage(JSON.stringify({ id: request.id, type: "progress", done }))
            })
        } else {
            response = await core.evaluateCoreJson(request.requestJson, (transformIndex, done) => {
                self.postMessage(JSON.stringify({ id: request.id, type: "progress", transformIndex, done }))
            })
        }
        self.postMessage(JSON.stringify({
            id: request.id,
            type: "success",
            responseJson: String(response),
        }))
    } catch (cause) {
        const error = cause instanceof Error ? cause.stack || cause.message : String(cause)
        self.postMessage(JSON.stringify({
            id: request?.id ?? -1,
            type: "failure",
            error,
        }))
    }
}
