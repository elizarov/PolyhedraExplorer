/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("PackageDirectoryMismatch")

package polyhedra.common.util

import kotlinx.coroutines.*

actual fun <T> runNonCancellable(block: suspend () -> T): T =
    runBlocking {
        block()
    }
