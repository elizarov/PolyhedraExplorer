/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common

fun interface OperationProgressContext {
    // done percent from 0 to 100
    fun reportProgress(done: Int)
}