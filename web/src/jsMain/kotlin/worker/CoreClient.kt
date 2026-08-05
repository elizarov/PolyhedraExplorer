package polyhedra.js.worker

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import polyhedra.core.api.CoreJson
import polyhedra.core.api.CoreRequest
import polyhedra.core.api.CoreResponse
import kotlin.js.Promise

fun evaluateInWasm(
    request: CoreRequest,
    reportProgress: (Int) -> Unit,
    onSuccess: (CoreResponse) -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    val requestJson = CoreJson.encodeToString(request)
    invokeWasmCore(requestJson, reportProgress).then(
        onFulfilled = { responseJson ->
            val response = runCatching { CoreJson.decodeFromString<CoreResponse>(responseJson) }
                .getOrElse { cause ->
                    onFailure(cause)
                    return@then
                }
            runCatching { onSuccess(response) }.onFailure(onFailure)
        },
        onRejected = { cause -> onFailure(cause) },
    )
}

@Suppress("UNUSED_PARAMETER")
private fun invokeWasmCore(
    requestJson: String,
    reportProgress: (Int) -> Unit,
): Promise<String> = js(
    "globalThis['Function'](\"return import('./core/PolyhedraExplorer-core.mjs')\")()" +
        ".then(module => module.evaluateCoreJson(requestJson, reportProgress))" +
        ".then(String)"
)
