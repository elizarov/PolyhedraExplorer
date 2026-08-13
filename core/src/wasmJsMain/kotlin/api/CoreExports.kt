package polyhedra.core.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import polyhedra.model.api.CoreJson
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreStlRequest
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsExport
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

@OptIn(ExperimentalJsExport::class, ExperimentalWasmJsInterop::class)
@JsExport
fun evaluateCoreJson(requestJson: String, reportProgress: (Int, Int) -> Unit): Promise<JsString> = Promise { resolve, reject ->
    suspend {
        val request = CoreJson.decodeFromString<CoreRequest>(requestJson)
        CoreJson.encodeToString(
            evaluateCore(request) { progress ->
                reportProgress(progress.transformIndex, progress.done)
            }
        ).toJsString()
    }.startCoroutine(object : Continuation<JsString> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<JsString>) {
            result.fold(resolve) { cause ->
                reject(cause.stackTraceToString().toJsString())
            }
        }
    })
}

@OptIn(ExperimentalJsExport::class, ExperimentalWasmJsInterop::class)
@JsExport
fun convertStlJson(requestJson: String, reportProgress: (Int) -> Unit): Promise<JsString> = Promise { resolve, reject ->
    suspend {
        val request = CoreJson.decodeFromString<CoreStlRequest>(requestJson)
        CoreJson.encodeToString(convertStl(request, reportProgress)).toJsString()
    }.startCoroutine(object : Continuation<JsString> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<JsString>) {
            result.fold(resolve) { cause ->
                reject(cause.stackTraceToString().toJsString())
            }
        }
    })
}

fun main() = Unit
