package polyhedra.js.main

sealed class Popup {
    object Config : Popup()
    object Export : Popup()
    object Seed : Popup()
    object AddTransform : Popup()
    data class ModifyTransform(val index: Int) : Popup()
    object Faces : Popup()
    object Edges : Popup()
    object Vertices : Popup()
}