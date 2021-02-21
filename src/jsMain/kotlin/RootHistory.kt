package polyhedra.js

import kotlinx.serialization.*
import polyhedra.common.*
import polyhedra.js.util.*

@Serializable
data class RootPaneData(
    // Polyhedra
    @SerialName("s")
    override var seed: Seed = Seed.Tetrahedron,
    @SerialName("t")
    override var transforms: List<Transform> = emptyList(),
    // View
    @SerialName("bs")
    override var baseScale: Scale = Scale.Circumradius,
    @SerialName("vs")
    override var viewScale: Double = 0.0,
    @SerialName("e")
    override var expand: Double = 0.0,
    @SerialName("ra")
    override var rotationAngle: Double = 60.0,
    @SerialName("r")
    override var rotate: Boolean = true,
    // Style
    @SerialName("tt")
    override var transparent: Double = 0.0,
    @SerialName("d")
    override var display: Display = Display.Full,
    // Lighting
    @SerialName("al")
    override var ambientLight: Double = 0.25,
    @SerialName("pt")
    override var pointLight: Double = 1.0,
    @SerialName("sl")
    override var specularLight: Double = 1.0,
    @SerialName("sp")
    override var specularPower: Double= 30.0,
) : RootPaneState

fun RootPaneState.assign(other: RootPaneState) {
    // Polyhedra
    seed = other.seed
    transforms = other.transforms
    // View
    baseScale = other.baseScale
    viewScale = other.viewScale
    expand = other.expand
    rotationAngle = other.rotationAngle
    rotate = other.rotate
    // Style
    transparent = other.transparent
    display = other.display
    // Lighting
    ambientLight = other.ambientLight
    pointLight = other.pointLight
    specularLight = other.specularLight
    specularPower = other.specularPower
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

