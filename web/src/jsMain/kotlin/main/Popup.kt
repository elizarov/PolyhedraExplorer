package polyhedra.web.main

sealed class Popup {
    object Config : Popup()
    object Export : Popup()
    object PrintColor : Popup()
    object Saves : Popup()
    object Seed : Popup()
    object AddTransform : Popup()
    data class ModifyTransform(val index: Int) : Popup()
    data class TransformSettings(val index: Int) : Popup()
    object Faces : Popup()
    object Edges : Popup()
    object Vertices : Popup()
}
