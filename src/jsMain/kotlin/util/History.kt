/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

@file:JsModule("history")
@file:JsNonModule
package polyhedra.js.util

external interface HashHistory {
    val location: Location
    fun push(path: String)
}

external interface Location {
    val pathname: String
}

external fun createHashHistory(): HashHistory
