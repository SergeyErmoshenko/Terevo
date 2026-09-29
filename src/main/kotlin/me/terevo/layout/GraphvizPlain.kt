package me.terevo.layout

internal data class DotResult(
    val nodeCenters: Map<String, Point>,
    val edges: Map<String, List<Point>>,
)

// Parses `dot -Tplain` output: inches, y growing upward. Converts to points (1:1 with pixels,
// since node sizes are sent as px / 72) with y growing downward from the top of the drawing.
// Plain output carries no edge ids, so edges are matched back to the ids they were declared with
// by (tail, head) in declaration order; edges sharing both endpoints are interchangeable.
internal object GraphvizPlain {
    private const val POINTS_PER_INCH = 72.0
    private const val SAMPLES_PER_BEZIER = 12

    fun parse(plain: String, declared: List<DeclaredEdge>): DotResult {
        val pending = declared.groupBy({ it.tail to it.head }, { it.id })
            .mapValues { ArrayDeque(it.value) }
        var height = 0.0
        val centers = mutableMapOf<String, Point>()
        val splines = mutableMapOf<String, List<Point>>()
        for (line in plain.lineSequence()) {
            val fields = line.trim().split(Regex("\\s+"))
            when (fields.firstOrNull()) {
                "graph" -> height = fields[3].toDouble()
                "node" -> centers[fields[1]] = point(fields[2], fields[3], height)
                "edge" -> {
                    val id = pending[fields[1] to fields[2]]?.removeFirstOrNull() ?: continue
                    val count = fields[3].toInt()
                    val controls = (0 until count).map { point(fields[4 + 2 * it], fields[5 + 2 * it], height) }
                    splines[id] = sampleBeziers(controls)
                }
            }
        }
        return DotResult(centers, splines)
    }

    private fun point(x: String, y: String, height: Double) =
        Point(x.toDouble() * POINTS_PER_INCH, (height - y.toDouble()) * POINTS_PER_INCH)

    // A spline is 3n+1 control points of a piecewise cubic Bezier.
    private fun sampleBeziers(controls: List<Point>): List<Point> {
        if (controls.size < 4) return controls
        val result = mutableListOf(controls.first())
        var index = 0
        while (index + 3 < controls.size) {
            val (p0, p1, p2, p3) = controls.subList(index, index + 4)
            for (step in 1..SAMPLES_PER_BEZIER) {
                result += cubic(p0, p1, p2, p3, step.toDouble() / SAMPLES_PER_BEZIER)
            }
            index += 3
        }
        return result
    }

    private fun cubic(p0: Point, p1: Point, p2: Point, p3: Point, t: Double): Point {
        val u = 1 - t
        val a = u * u * u
        val b = 3 * u * u * t
        val c = 3 * u * t * t
        val d = t * t * t
        return Point(
            a * p0.x + b * p1.x + c * p2.x + d * p3.x,
            a * p0.y + b * p1.y + c * p2.y + d * p3.y,
        )
    }
}

internal data class DeclaredEdge(val id: String, val tail: String, val head: String)
