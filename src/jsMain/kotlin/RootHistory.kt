package polyhedra.js

import kotlinx.serialization.*
import polyhedra.common.*
import polyhedra.js.util.*

@Serializable
data class RootPaneData(
    @SerialName("s")
    override var seed: Seed = Seed.Tetrahedron,
    @SerialName("t")
    override var transforms: List<Transform> = emptyList(),
    @SerialName("bs")
    override var baseScale: Scale = Scale.Circumradius,
    @SerialName("ra")
    override var rotationAngle: Double = 60.0,
    @SerialName("r")
    override var rotate: Boolean = true,
    @SerialName("vs")
    override var viewScale: Double = 0.0,
    @SerialName("e")
    override var expand: Double = 0.0,
    @SerialName("d")
    override var display: Display = Display.Full
) : RootPaneState

fun RootPaneState.assign(other: RootPaneState) {
    seed = other.seed
    transforms = other.transforms
    baseScale = other.baseScale
    rotationAngle = other.rotationAngle
    rotate = other.rotate
    viewScale = other.viewScale
    expand = other.expand
    display = other.display
}

fun RootPaneData(other: RootPaneState) = RootPaneData().apply { assign(other) }

private val history = createHashHistory()

fun loadRootState(): RootPaneState {
    val str = history.location.pathname.substringAfter('/', "")
    return URLDecoder(str).decodeSerializableValue(RootPaneData.serializer())
}

fun pushRootState(state: RootPaneState) {
    val data = RootPaneData(state)
    val encoder = URLEncoder()
    encoder.encodeSerializableValue(RootPaneData.serializer(), data)
    history.push("/$encoder")
}

