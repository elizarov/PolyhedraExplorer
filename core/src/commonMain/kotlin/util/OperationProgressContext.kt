/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core.util

fun interface OperationProgressContext {
    // done percent from 0 to 100
    fun reportProgress(done: Int)
}

fun OperationProgressContext.subrange(start: Int, end: Int): OperationProgressContext {
    require(start in 0..100 && end in start..100)
    return OperationProgressContext { done ->
        reportProgress(start + (end - start) * done.coerceIn(0, 100) / 100)
    }
}

fun OperationProgressContext.reportProgress(completed: Int, total: Int) {
    reportProgress(if (total <= 0) 100 else completed.coerceIn(0, total) * 100 / total)
}
