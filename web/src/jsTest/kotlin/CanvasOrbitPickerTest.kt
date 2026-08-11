package polyhedra.web

import polyhedra.core.poly.*
import polyhedra.model.poly.*
import polyhedra.model.util.Vec3
import polyhedra.web.poly.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CanvasOrbitPickerTest {
    @Test
    fun picksFrontFaceAndItsEdgeAndVertexOrbits() {
        val poly = cubeWithUniqueKinds()
        val view = defaultView()
        val picker = CanvasOrbitPicker(poly, view, 400, 400, expand = 0.0, excludedFaces = emptySet())

        val face = poly.fs.single { it.z > 0.5 }
        assertEquals(face.kind, picker.hitFace(200.0, 200.0))
        val edgeStart = picker.project(face[0], face)!!
        val edgeEnd = picker.project(face[1], face)!!
        val edgeKind = face.directedEdgeAt(0).normalizedDirection().kind
        val vertexPoint = picker.project(face[0])!!

        assertEquals(
            edgeKind,
            picker.hitEdge((edgeStart.x + edgeEnd.x) / 2.0, (edgeStart.y + edgeEnd.y) / 2.0),
        )
        assertEquals(face[0].kind, picker.hitVertex(vertexPoint.x, vertexPoint.y))
    }

    @Test
    fun fullFrontFaceRemainsAPickingSurfaceRegardlessOfRenderedVisibility() {
        val poly = cubeWithUniqueKinds()
        val view = defaultView()
        // Render visibility is intentionally not an input to the picker.
        val picker = CanvasOrbitPicker(poly, view, 400, 400, expand = 0.0, excludedFaces = emptySet())
        val face = poly.fs.single { it.z > 0.5 }
        val center = Vec3(
            face.fvs.sumOf { it.x } / face.size,
            face.fvs.sumOf { it.y } / face.size,
            face.fvs.sumOf { it.z } / face.size,
        )
        val nearCorner = Vec3(
            center.x * 0.15 + face[0].x * 0.85,
            center.y * 0.15 + face[0].y * 0.85,
            center.z * 0.15 + face[0].z * 0.85,
        )
        val centerScreen = picker.project(center, face)!!
        val cornerScreen = picker.project(nearCorner, face)!!

        assertEquals(face.kind, picker.hitFace(centerScreen.x, centerScreen.y))
        assertEquals(face.kind, picker.hitFace(cornerScreen.x, cornerScreen.y))
    }

    @Test
    fun picksTheFaceAtItsRenderedAnimationPosition() {
        val previous = cubeWithUniqueKinds(offsetX = -1.4)
        val target = cubeWithUniqueKinds(offsetX = 1.4)
        val animation = TransformAnimationStep(
            duration = 1.0,
            prev = TransformKeyframe(previous, 0.0),
            target = TransformKeyframe(target, 1.0),
        ).also { it.update(0.5) }
        val picker = CanvasOrbitPicker(
            target,
            defaultView(),
            400,
            400,
            expand = 0.0,
            excludedFaces = emptySet(),
            animation = animation,
        )
        val targetFace = target.fs.single { it.z > 0.5 }
        val previousFace = previous.fs[targetFace.id]
        val renderedCenter = Vec3(
            targetFace.fvs.indices.sumOf { (targetFace[it].x + previousFace[it].x) / 2.0 } / targetFace.size,
            targetFace.fvs.indices.sumOf { (targetFace[it].y + previousFace[it].y) / 2.0 } / targetFace.size,
            targetFace.fvs.indices.sumOf { (targetFace[it].z + previousFace[it].z) / 2.0 } / targetFace.size,
        )
        val screen = picker.project(renderedCenter)!!

        assertEquals(targetFace.kind, picker.hitFace(screen.x, screen.y))
    }

    @Test
    fun concaveFaceNotchIsNotPickable() {
        val poly = concavePrismFixture()
        val picker = CanvasOrbitPicker(
            poly,
            defaultView(),
            400,
            400,
            expand = 0.0,
            excludedFaces = emptySet(),
        )
        val top = poly.fs.first()
        val inside = Vec3(-1.0, 0.0, 1.0)
        val notch = Vec3(0.5, 0.0, 1.0)
        val insideScreen = picker.project(inside, top)!!
        val notchScreen = picker.project(notch, top)!!

        assertEquals(top.kind, picker.hitFace(insideScreen.x, insideScreen.y))
        assertNull(picker.hitFace(notchScreen.x, notchScreen.y))
    }

    private fun defaultView(): ViewContext {
        val params = ViewParams("", null)
        val view = ViewContext(params)
        view.performUpdate(null, 0.0)
        view.initProjection(400, 400)
        return view
    }

    private fun cubeWithUniqueKinds(offsetX: Double = 0.0): Polyhedron {
        val source = Seed.Cube.poly
        return polyhedron {
            val vertices = source.vs.map {
                vertex(Vec3(it.x + offsetX, it.y, it.z), VertexKind(it.id))
            }
            for (face in source.fs) {
                face(face.fvs.map { vertices[it.id] }, FaceKind(face.id))
            }
        }
    }

}
