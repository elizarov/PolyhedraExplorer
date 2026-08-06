/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.util

import kotlinx.coroutines.runBlocking

actual fun <T> runSynchronously(block: suspend () -> T): T =
    runBlocking {
        block()
    }
