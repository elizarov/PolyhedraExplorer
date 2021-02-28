package polyhedra.js.poly

import polyhedra.common.*
import polyhedra.common.util.*
import polyhedra.js.*
import polyhedra.js.params.*
import kotlin.math.*

private const val MAX_DISPLAY_EDGES = (1 shl 15) - 1

class PolyParams(tag: String, val animation: ViewAnimationParams?) : Param.Composite(tag, UpdateType.ValueUpdate) {
    // what to show
    val seed = using(EnumParam("s", Seed.Tetrahedron, Seeds))
    val transforms = using(EnumListParam("t", emptyList(), Transforms))
    val baseScale = using(EnumParam("bs", Scale.Circumradius, Scales))
    // how to show
    val view = using(ViewParams("v", animation))
    val lighting = using(LightingParams("l", animation))

    // computed value of the currently shown polyhedron
    var poly: Polyhedron = Seed.Tetrahedron.poly
        private set
    var polyName: String = ""
        private set
    var transformError: TransformError? = null
        private set

    override fun update() {
        var curPoly = seed.value.poly
        var curPolyName = seed.value.toString()
        var curIndex = 0
        var curMessage: String? = null
        try {
            for (transform in transforms.value) {
                val applicable = transform.isApplicable(curPoly)
                if (applicable != null) {
                    curMessage = applicable
                    break
                }
                val newPoly = curPoly.transformed(transform)
                val nEdges = newPoly.es.size
                if (nEdges > MAX_DISPLAY_EDGES) {
                    curMessage = "Polyhedron is too large to display ($nEdges edges)"
                    break
                }
                newPoly.validateGeometry()
                curPolyName = "$transform $curPolyName"
                curPoly = newPoly
                curIndex++
            }
        } catch (e: Exception) {
            curMessage = "Transform produces invalid polyhedron geometry"
        }
        poly = curPoly.scaled(baseScale.value)
        polyName = curPolyName
        transformError = if (curMessage != null)
            TransformError(curIndex, curMessage) else null
    }
}

data class TransformError(
    val index: Int,
    val message: String
)

// Optionally passed from the outside (not needed in the backend)
class ViewAnimationParams(tag: String) : Param.Composite(tag), ValueAnimationParams, RotationAnimationParams  {
    val animateValueUpdates = using(BooleanParam("u", true))
    val animationDuration = using(DoubleParam("d", 0.5, 0.0, 2.0, 0.1))

    override val animatedRotation: BooleanParam = using(BooleanParam("r", true))
    val rotationSpeed = using(DoubleParam("rs", 1.0, 0.0, 2.0, 0.01))
    val rotationAngle = using(DoubleParam("ra", 60.0, 0.0, 360.0, 1.0))

    override val animateValueUpdatesDuration: Double?
        get() = animationDuration.value.takeIf { animateValueUpdates.value }

    override val animatedRotationAngles: Vec3
        get() {
            val ra = rotationAngle.value * PI / 180
            val rs = rotationSpeed.value
            return Vec3(rs * sin(ra), rs * cos(ra), 0.0)
        }
}

class ViewParams(
    tag: String,
    animation: ViewAnimationParams?,
) : Param.Composite(tag) {
    val rotate = using(RotationParam("r", Quat.ID, animation, animation))
    val scale = using(DoubleParam("s", 0.0, -2.0, 2.0, 0.01, animation))
    val expandFaces = using(DoubleParam("e", 0.0, 0.0, 2.0, 0.01, animation))
    val transparentFaces = using(DoubleParam("t", 0.0, 0.0, 1.0, 0.01, animation))
    val display = using(EnumParam("d", Display.All, Displays))
}

class LightingParams(tag: String, animation: ViewAnimationParams?) : Param.Composite(tag) {
    val ambientLight = using(DoubleParam("a", 0.25, 0.0, 1.0, 0.01, animation))
    val diffuseLight = using(DoubleParam("d", 1.0, 0.0, 1.0, 0.01, animation))
    val specularLight = using(DoubleParam("s", 1.0, 0.0, 1.0, 0.01, animation))
    val specularPower = using(DoubleParam("sp", 30.0, 0.0, 100.0, 1.0, animation))
}

