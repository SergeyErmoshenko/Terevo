package me.terevo.layout

import java.io.File
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.hypot

// Lays the tree out with Graphviz `dot`: generations become forced ranks, every couple is
// joined through a point-sized family node on a rank just below the spouses, and children of a
// recorded couple hang from that family node. Graphviz decides the ordering,
// the coordinates and the edge curves; this class only translates to DOT and back.
class GraphvizLayoutEngine(private val dot: DotProcess = DotProcess.locate()) : LayoutEngine {

    override fun layout(request: LayoutRequest): Layout {
        if (request.graph.nodes.isEmpty()) return Layout.EMPTY
        val assignment = GenerationAssigner.assign(request.graph)
        val visible = visibleNodes(request, assignment.generations)
        if (visible.isEmpty()) return Layout.EMPTY

        val scene = DotScene.build(request, assignment.generations, visible)
        val result = GraphvizPlain.parse(dot.run(scene.source), scene.declared)

        val nodes = scene.personNames.mapValues { (id, name) ->
            val center = result.nodeCenters.getValue(name)
            val size = request.metrics.sizeOf(id)
            Rect(center.x - size.width / 2, center.y - size.height / 2, size.width, size.height)
        }
        val edges = scene.paths.mapNotNull { path -> path.resolve(result) }
        val edgeBounds = edges.flatMap { it.segments }.let { points ->
            if (points.isEmpty()) {
                null
            } else {
                val left = points.minOf { it.x }
                val top = points.minOf { it.y }
                Rect(left, top, points.maxOf { it.x } - left, points.maxOf { it.y } - top)
            }
        }
        val nodeBounds = Rect.enclosing(nodes.values)
        return Layout(
            nodes = nodes,
            edges = edges,
            generations = assignment.generations.filterKeys { it in visible },
            bounds = edgeBounds?.union(nodeBounds) ?: nodeBounds,
            mainPersonId = mainPerson(assignment, nodes),
        )
    }

    // DirectionalLayoutEngine mirrors the layout vertically, so the highest generation (the
    // youngest people) ends up on top; the anchor is the leftmost of them in the largest family.
    private fun mainPerson(assignment: GenerationAssignment, nodes: Map<NodeId, Rect>): NodeId? {
        val largest = assignment.components
            .map { component -> component.filter { it in nodes } }
            .filter { it.isNotEmpty() }
            .maxByOrNull { it.size } ?: return null
        val topGeneration = largest.maxOf { assignment.generations.getValue(it) }
        return largest
            .filter { assignment.generations.getValue(it) == topGeneration }
            .minByOrNull { nodes.getValue(it).left }
    }
}

private class DotPath(
    val edge: LayoutEdge,
    val style: EdgeStyle,
    // Each piece is a DOT edge id plus the DOT node the piece should start from.
    val pieces: List<Pair<String, String>>,
) {
    fun resolve(result: DotResult): EdgePath? {
        val points = mutableListOf<Point>()
        for ((id, startName) in pieces) {
            val piece = result.edges[id] ?: return null
            val start = result.nodeCenters[startName] ?: return null
            val oriented = if (distance(piece.last(), start) < distance(piece.first(), start)) piece.asReversed() else piece
            points += if (points.isNotEmpty() && points.last() == oriented.first()) oriented.drop(1) else oriented
        }
        if (points.size < 2) return null
        return EdgePath(edge, points, style)
    }

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
}

private class DotScene(
    val source: String,
    val personNames: Map<NodeId, String>,
    val paths: List<DotPath>,
    val declared: List<DeclaredEdge>,
) {
    companion object {
        private const val POINTS_PER_INCH = 72.0
        private const val POINT_SIZE = 0.01
        private const val MARRIAGE_WEIGHT = 10
        private const val FAMILY_CHILD_WEIGHT = 2

        fun build(request: LayoutRequest, generations: Map<NodeId, Int>, visible: Set<NodeId>): DotScene {
            val graph = request.graph
            val options = request.options
            val persons = graph.sortedNodeIds().filter { it in visible }
            val personNames = persons.withIndex().associate { (index, id) -> id to "n$index" }
            val rows = sortedMapOf<Int, MutableList<String>>()
            val familyRows = mutableMapOf<Int, MutableList<String>>()
            persons.forEach { rows.getOrPut(generations.getValue(it)) { mutableListOf() } += personNames.getValue(it) }

            val nodeLines = mutableListOf<String>()
            val edgeLines = mutableListOf<String>()
            val paths = mutableListOf<DotPath>()
            val declared = mutableListOf<DeclaredEdge>()
            fun edge(tail: String, head: String, attributes: String): String {
                val id = "e${declared.size}"
                declared += DeclaredEdge(id, tail, head)
                edgeLines += "$tail -> $head [$attributes];"
                return id
            }

            for (id in persons) {
                val size = request.metrics.sizeOf(id)
                nodeLines += "${personNames.getValue(id)} [width=${inches(size.width)} height=${inches(size.height)}];"
            }

            // One family node per distinct couple whose partners share a row. It sits on its own
            // rank just below the couple, so both spouses' lines meet there and Graphviz is free to
            // put either spouse on either side (a same-rank A -> F -> B chain would pin the order).
            val familyOf = mutableMapOf<Set<NodeId>, String>()
            for (union in graph.unions) {
                if (union.first !in visible || union.second !in visible) continue
                val pair = setOf(union.first, union.second)
                val first = personNames.getValue(union.first)
                val second = personNames.getValue(union.second)
                val style = if (union.dissolved) EdgeStyle.DISSOLVED_MARRIAGE else EdgeStyle.MARRIAGE
                val generation = generations.getValue(union.first)
                if (generation != generations.getValue(union.second)) {
                    paths += DotPath(union, style, listOf(edge(first, second, "constraint=false") to first))
                    continue
                }
                val family = familyOf.getOrPut(pair) {
                    val name = "f${familyOf.size}"
                    nodeLines += "$name [shape=point width=$POINT_SIZE height=$POINT_SIZE];"
                    familyRows.getOrPut(generation) { mutableListOf() } += name
                    name
                }
                val fromFirst = edge(first, family, "weight=$MARRIAGE_WEIGHT tailport=s")
                val fromSecond = edge(second, family, "weight=$MARRIAGE_WEIGHT tailport=s")
                paths += DotPath(union, style, listOf(fromFirst to first, fromSecond to family))
            }

            // A child whose two parents form a known couple (with the same kind of parentage)
            // hangs from that couple's family node; every other parentage is drawn directly.
            val parentagesByChild = graph.parentages
                .filter { it.parent in visible && it.child in visible }
                .groupBy { it.child }
            for ((child, parentages) in parentagesByChild) {
                val childName = personNames.getValue(child)
                val remaining = parentages.toMutableList()
                for (first in parentages) {
                    if (first !in remaining) continue
                    val partner = remaining.firstOrNull { other ->
                        other !== first &&
                            other.biological == first.biological &&
                            setOf(first.parent, other.parent) in familyOf
                    } ?: continue
                    remaining -= first
                    remaining -= partner
                    val family = familyOf.getValue(setOf(first.parent, partner.parent))
                    val id = edge(family, childName, "weight=$FAMILY_CHILD_WEIGHT headport=n")
                    listOf(first, partner).forEach { parentage ->
                        paths += DotPath(parentage, parentageStyle(parentage), listOf(id to family))
                    }
                }
                for (parentage in remaining) {
                    val parentName = personNames.getValue(parentage.parent)
                    val id = edge(parentName, childName, "tailport=s headport=n")
                    paths += DotPath(parentage, parentageStyle(parentage), listOf(id to parentName))
                }
            }

            // An invisible chain of anchors pins every generation (and the family-node rank below
            // it) to its own rank, so rows line up across the whole canvas even between unrelated
            // families.
            val rankLines = mutableListOf<String>()
            val anchors = rows.keys.flatMap { generation ->
                listOf(rows.getValue(generation), familyRows[generation].orEmpty())
            }.withIndex().map { (index, members) ->
                val anchor = "g$index"
                nodeLines += "$anchor [shape=point width=$POINT_SIZE height=$POINT_SIZE style=invis];"
                rankLines += "{rank=same; ${(listOf(anchor) + members).joinToString("; ")}; }"
                anchor
            }
            anchors.zipWithNext { upper, lower -> edgeLines += "$upper -> $lower [style=invis weight=0];" }

            // Mincross ignores edge weights, so nothing else keeps a couple side by side. An
            // invisible cluster keeps its members contiguous without fixing which spouse is on the
            // left; a person can belong to one cluster only, so only single-marriage couples get one.
            val visiblePartners = persons.associateWith { id -> graph.partners(id).filter { it in visible } }
            val clusterLines = familyOf.keys
                .filter { pair -> pair.all { visiblePartners.getValue(it).size == 1 } }
                .withIndex()
                .map { (index, pair) ->
                    "subgraph cluster_c$index { peripheries=0 margin=0; " +
                        pair.joinToString("; ") { personNames.getValue(it) } + "; }"
                }

            val source = buildString {
                appendLine("digraph tree {")
                appendLine(
                    "graph [rankdir=TB newrank=true nodesep=${inches(options.siblingSpacing)} " +
                        "ranksep=${inches(options.generationSpacing / 2)}];",
                )
                appendLine("node [shape=box fixedsize=true label=\"\"];")
                appendLine("edge [dir=none];")
                nodeLines.forEach(::appendLine)
                rankLines.forEach(::appendLine)
                clusterLines.forEach(::appendLine)
                edgeLines.forEach(::appendLine)
                appendLine("}")
            }
            return DotScene(source, personNames, paths, declared)
        }

        private fun inches(pixels: Double): String = String.format(Locale.ROOT, "%.4f", pixels / POINTS_PER_INCH)

        private fun parentageStyle(parentage: LayoutEdge.Parentage): EdgeStyle =
            if (parentage.biological) EdgeStyle.BIOLOGICAL else EdgeStyle.NON_BIOLOGICAL
    }
}

class DotProcess(private val executable: String) {

    fun run(source: String): String {
        val process = ProcessBuilder(executable, "-Tplain").start()
        val errors = CompletableFuture.supplyAsync { process.errorStream.readBytes().decodeToString() }
        process.outputStream.use { it.write(source.encodeToByteArray()) }
        val output = process.inputStream.readBytes().decodeToString()
        check(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Graphviz dot did not finish" }
        check(process.exitValue() == 0) { "Graphviz dot failed: ${errors.join()}" }
        return output
    }

    companion object {
        private const val TIMEOUT_SECONDS = 60L
        private const val IMAGE_DIRECTORY = "compose.application.resources.dir"

        // Apps started from Finder get a minimal PATH, so well-known install locations are
        // checked before falling back to a PATH lookup.
        private val KNOWN_LOCATIONS = listOf(
            "/opt/homebrew/bin/dot",
            "/usr/local/bin/dot",
            "/usr/bin/dot",
            "C:\\Program Files\\Graphviz\\bin\\dot.exe",
        )

        fun locate(): DotProcess =
            DotProcess(configured() ?: bundledDotPath(System.getProperty(IMAGE_DIRECTORY)) ?: installed() ?: pathDot())

        private fun configured(): String? =
            System.getProperty("terevo.dot") ?: System.getenv("TEREVO_DOT")

        private fun installed(): String? =
            KNOWN_LOCATIONS.firstOrNull { File(it).canExecute() }
    }
}

private val BUNDLED_DOT_LOCATIONS = listOf(
    "graphviz/bin/dot.exe",
    "graphviz/bin/dot",
    "graphviz/dot.exe",
    "graphviz/dot",
)

private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows")

internal fun bundledDotPath(imageDirectory: String?): String? {
    val resources = imageDirectory?.let(::File) ?: return null
    return BUNDLED_DOT_LOCATIONS
        .map { File(resources, it.replace('/', File.separatorChar)) }
        .firstOrNull { it.isFile }
        ?.absolutePath
}

internal fun pathDot(): String = if (isWindows()) "dot.exe" else "dot"
