package me.terevo.layout

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import me.terevo.domain.Outcome
import me.terevo.persistence.SqlDelightTreeRepository
import me.terevo.persistence.db.TerevoDatabase
import me.terevo.ui.tree.TreeCanvasMapper
import kotlin.test.Test

class ReproTest {
    @Test
    fun `inspect nikolai stepan bracket`() {
        val path = "${System.getenv("TMPDIR")?.trimEnd('/')}/terevo-investigate/project.terevo"
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        val database = TerevoDatabase(driver)
        val repository = SqlDelightTreeRepository(database)
        val loaded = repository.load()
        if (loaded is Outcome.Err) println("REPRO load failed: ${loaded.error}")
        val tree = (loaded as Outcome.Ok).value

        val state = TreeCanvasMapper.map(tree)

        val nikolaiS = NodeId("7290e547-92e8-476f-828c-0a4ac7597476")
        val stepan = NodeId("f3e00bbf-869d-4b2b-ace4-c74b674d1dd2")
        val maria = NodeId("ddcb3075-6e2f-4d5f-a548-b9d0c051df71")
        val ignat = NodeId("3151fa1f-eb1f-4287-a8a0-799f35a323cd")
        val vassa = NodeId("a1b8a008-88f7-42f8-a129-98e66f7ba83a")

        for ((label, id) in listOf(
            "NikolaiS" to nikolaiS, "Stepan" to stepan, "Maria" to maria, "Ignat" to ignat, "Vassa" to vassa,
        )) {
            println("REPRO $label -> ${state.layout.nodes[id]} gen=${state.layout.generations[id]}")
        }

        // Stepan's siblings row: all children of Ignat+Vassa.
        val siblings = tree.relations.values.filterIsInstance<me.terevo.domain.model.ParentChild>()
            .filter { it.parent.toString() == ignat.value || it.parent.toString() == vassa.value }
            .map { it.child.toString() }
            .distinct()
        println("REPRO siblings of Stepan (children of Ignat/Vassa): $siblings")
        for (sib in siblings) {
            val id = NodeId(sib)
            println("REPRO sibling $sib -> ${state.layout.nodes[id]} gen=${state.layout.generations[id]}")
        }

        val nikolaiEdge = state.layout.edges.first {
            it.edge is LayoutEdge.Parentage && (it.edge as LayoutEdge.Parentage).child == nikolaiS
        }
        println("REPRO Nikolai's parentage edge segments: ${nikolaiEdge.segments}")

        val gen5 = state.layout.generations.filterValues { it == 5 }.keys
        val personById = tree.persons
        for (id in gen5.sortedBy { state.layout.nodes.getValue(it).left }) {
            val person = personById[me.terevo.domain.model.PersonId.parse(id.value)]
            println("REPRO gen5 ${state.layout.nodes[id]} $person")
        }

        val gen4 = state.layout.generations.filterValues { it == 4 }.keys
        for (id in gen4.sortedBy { state.layout.nodes.getValue(it).left }) {
            val person = personById[me.terevo.domain.model.PersonId.parse(id.value)]
            println("REPRO gen4 ${state.layout.nodes[id]} $person")
        }

        val nikolaiChildren = tree.relations.values.filterIsInstance<me.terevo.domain.model.ParentChild>()
            .filter { it.parent.toString() == nikolaiS.value }
            .map { it.child.toString() }
            .distinct()
        println("REPRO Nikolai's children: $nikolaiChildren")
        for (childStr in nikolaiChildren) {
            val id = NodeId(childStr)
            val person = personById[me.terevo.domain.model.PersonId.parse(childStr)]
            println("REPRO Nikolai child -> ${state.layout.nodes[id]} gen=${state.layout.generations[id]} $person")

            val grandkids = tree.relations.values.filterIsInstance<me.terevo.domain.model.ParentChild>()
                .filter { it.parent.toString() == childStr }
                .map { it.child.toString() }
                .distinct()
            for (gk in grandkids) {
                val gkId = NodeId(gk)
                val gkPerson = personById[me.terevo.domain.model.PersonId.parse(gk)]
                println("REPRO   grandchild -> ${state.layout.nodes[gkId]} gen=${state.layout.generations[gkId]} $gkPerson")
            }
        }

        val vvId = "88a3fbf3-70ec-40e4-95f0-6327cf541a15"
        val vvChildren = tree.relations.values.filterIsInstance<me.terevo.domain.model.ParentChild>()
            .filter { it.parent.toString() == vvId }
            .map { it.child.toString() }
            .distinct()
        println("REPRO VladimirV's children: $vvChildren")
        for (childStr in vvChildren) {
            val id = NodeId(childStr)
            val person = personById[me.terevo.domain.model.PersonId.parse(childStr)]
            println("REPRO VladimirV child -> ${state.layout.nodes[id]} gen=${state.layout.generations[id]} $person")
        }

        val nikolaiParents = tree.relations.values.filterIsInstance<me.terevo.domain.model.ParentChild>()
            .filter { it.child.toString() == nikolaiS.value }
            .map { it.parent.toString() }
            .distinct()
        println("REPRO Nikolai's parents (all): $nikolaiParents")
        for (p in nikolaiParents) {
            val pId = NodeId(p)
            val pPerson = personById[me.terevo.domain.model.PersonId.parse(p)]
            println("REPRO   Nikolai's parent -> ${state.layout.nodes[pId]} gen=${state.layout.generations[pId]} $pPerson")
        }

        val vladimirNId = "bed88197-eaed-44b9-9b54-a05fc891c718"
        val vladimirNParents = tree.relations.values.filterIsInstance<me.terevo.domain.model.ParentChild>()
            .filter { it.child.toString() == vladimirNId }
            .map { it.parent.toString() }
            .distinct()
        println("REPRO VladimirN's parents (all): $vladimirNParents")
        val vladimirNMarriages = tree.relations.values.filterIsInstance<me.terevo.domain.model.Marriage>()
            .filter { it.spouseA.toString() == vladimirNId || it.spouseB.toString() == vladimirNId }
        println("REPRO VladimirN's marriages: ${vladimirNMarriages.map { it.spouseA to it.spouseB }}")

        val nikolaiMarriages = tree.relations.values.filterIsInstance<me.terevo.domain.model.Marriage>()
            .filter { it.spouseA.toString() == nikolaiS.value || it.spouseB.toString() == nikolaiS.value }
        for (m in nikolaiMarriages) {
            val spouseStr = if (m.spouseA.toString() == nikolaiS.value) m.spouseB.toString() else m.spouseA.toString()
            val spouseId = NodeId(spouseStr)
            val spousePerson = personById[me.terevo.domain.model.PersonId.parse(spouseStr)]
            println("REPRO Nikolai's spouse -> ${state.layout.nodes[spouseId]} gen=${state.layout.generations[spouseId]} $spousePerson")

            val spouseParents = tree.relations.values.filterIsInstance<me.terevo.domain.model.ParentChild>()
                .filter { it.child.toString() == spouseStr }
                .map { it.parent.toString() }
                .distinct()
            for (p in spouseParents) {
                val pId = NodeId(p)
                val pPerson = personById[me.terevo.domain.model.PersonId.parse(p)]
                println("REPRO   Nikolai's spouse's parent -> ${state.layout.nodes[pId]} gen=${state.layout.generations[pId]} $pPerson")
            }
        }

        for ((label, id) in listOf("Ignat" to ignat.value, "Vassa" to vassa.value)) {
            val parents = tree.relations.values.filterIsInstance<me.terevo.domain.model.ParentChild>()
                .filter { it.child.toString() == id }.map { it.parent.toString() }.distinct()
            val children = tree.relations.values.filterIsInstance<me.terevo.domain.model.ParentChild>()
                .filter { it.parent.toString() == id }.map { it.child.toString() }.distinct()
            println("REPRO $label parents=$parents children=$children")
            for (p in parents) {
                val pId = NodeId(p)
                println("REPRO   $label parent -> ${state.layout.nodes[pId]} gen=${state.layout.generations[pId]}")
            }
        }

        val gen3 = state.layout.generations.filterValues { it == 3 }.keys
        for (id in gen3.sortedBy { state.layout.nodes.getValue(it).left }) {
            val person = personById[me.terevo.domain.model.PersonId.parse(id.value)]
            println("REPRO gen3 ${state.layout.nodes[id]} $person")
        }
        val gen2 = state.layout.generations.filterValues { it == 2 }.keys
        for (id in gen2.sortedBy { state.layout.nodes.getValue(it).left }) {
            val person = personById[me.terevo.domain.model.PersonId.parse(id.value)]
            println("REPRO gen2 ${state.layout.nodes[id]} $person")
        }

        // The screenshot's "Главный" (root) label is on Nikolai, NOT Violetta - the real
        // app's selected/root person is Nikolai, viewed in BOTH mode from his perspective.
        val realOptions = me.terevo.layout.LayoutOptions(
            root = nikolaiS,
            mode = me.terevo.layout.LayoutMode.BOTH,
            depth = me.terevo.layout.LayoutOptions.UNLIMITED_DEPTH,
        )
        // Uses the SAME mapper the real app's canvas uses (per-name card widths), not
        // ExportTreeMapper's flat 240x72 card size - the flat size was hiding real
        // overlaps caused by long names getting wider boxes on screen than in export.
        val real = TreeCanvasMapper.map(tree, realOptions)
        val exported = me.terevo.export.ExportTreeMapper.map(tree, realOptions)
        val pdfPath = "${System.getenv("TMPDIR")?.trimEnd('/')}/repro.pdf"
        org.apache.pdfbox.pdmodel.PDDocument().use { document ->
            val font = me.terevo.export.PdfFiles::class.java.getResourceAsStream("/fonts/PTSans-Regular.ttf")
                ?.use { org.apache.pdfbox.pdmodel.font.PDType0Font.load(document, it, true) }
                ?: error("font resource missing")
            val pages = me.terevo.export.TreePagination.paginate(exported.layout, exported.labels, me.terevo.export.PdfFiles.A4_LANDSCAPE)
            me.terevo.export.PdfTreeRenderer.render(document, exported.layout, exported.labels, pages, font)
            document.save(pdfPath)
        }
        val pageList = me.terevo.export.TreePagination.paginate(exported.layout, exported.labels, me.terevo.export.PdfFiles.A4_LANDSCAPE)
        for (page in pageList) {
            println("REPRO page ${page.index} bounds=${page.worldBounds}")
        }
        println("REPRO pdf written to $pdfPath")

        // ANCESTORS-mode layout (matches the real screenshot) - check for tight/overlapping
        // gaps row by row, since the WHOLE_FAMILY `state` above filters differently.
        val ancestorsGenerations = exported.layout.generations.values.distinct().sorted()
        for (gen in ancestorsGenerations) {
            val idsInRow = exported.layout.generations.filterValues { it == gen }.keys
                .sortedBy { exported.layout.nodes.getValue(it).left }
            println("REPRO ANCESTORS gen=$gen row:")
            var previous: Rect? = null
            var previousId: NodeId? = null
            for (id in idsInRow) {
                val rect = exported.layout.nodes.getValue(id)
                val person = personById[me.terevo.domain.model.PersonId.parse(id.value)]
                val gap = previous?.let { rect.left - (it.left + it.width) }
                println("REPRO ANCESTORS   $id $person rect=$rect gapFromPrevious=$gap prev=$previousId")
                previous = rect
                previousId = id
            }
        }

        // Same check, but using the REAL app's per-name card widths (TreeCanvasMapper),
        // not the flat 240x72 export card size - long names get wider boxes on screen.
        val realGenerations = real.layout.generations.values.distinct().sorted()
        for (gen in realGenerations) {
            val idsInRow = real.layout.generations.filterValues { it == gen }.keys
                .sortedBy { real.layout.nodes.getValue(it).left }
            println("REPRO REAL gen=$gen row:")
            var previous: Rect? = null
            var previousId: NodeId? = null
            for (id in idsInRow) {
                val rect = real.layout.nodes.getValue(id)
                val person = personById[me.terevo.domain.model.PersonId.parse(id.value)]
                val gap = previous?.let { rect.left - (it.left + it.width) }
                println("REPRO REAL   $id $person rect=$rect gapFromPrevious=$gap prev=$previousId")
                previous = rect
                previousId = id
            }
        }

        // Check for two DIFFERENT edges' line segments literally coinciding on screen (not just
        // nodes being close) - the user reported connector lines from unrelated branches running
        // right on top of each other, not just crowded boxes.
        data class Seg(val edgeIndex: Int, val a: Point, val b: Point)
        val segs = real.layout.edges.flatMapIndexed { edgeIndex, path ->
            path.segments.zipWithNext { a, b -> Seg(edgeIndex, a, b) }
        }
        fun overlap1d(a1: Double, a2: Double, b1: Double, b2: Double): Double {
            val lo = maxOf(minOf(a1, a2), minOf(b1, b2))
            val hi = minOf(maxOf(a1, a2), maxOf(b1, b2))
            return maxOf(0.0, hi - lo)
        }
        for (i in segs.indices) {
            for (j in i + 1 until segs.size) {
                val s1 = segs[i]
                val s2 = segs[j]
                if (s1.edgeIndex == s2.edgeIndex) continue
                val horiz1 = kotlin.math.abs(s1.a.y - s1.b.y) < 0.5
                val horiz2 = kotlin.math.abs(s2.a.y - s2.b.y) < 0.5
                val vert1 = kotlin.math.abs(s1.a.x - s1.b.x) < 0.5
                val vert2 = kotlin.math.abs(s2.a.x - s2.b.x) < 0.5
                val coincide = when {
                    horiz1 && horiz2 && kotlin.math.abs(s1.a.y - s2.a.y) < 0.5 ->
                        overlap1d(s1.a.x, s1.b.x, s2.a.x, s2.b.x) > 0.5
                    vert1 && vert2 && kotlin.math.abs(s1.a.x - s2.a.x) < 0.5 ->
                        overlap1d(s1.a.y, s1.b.y, s2.a.y, s2.b.y) > 0.5
                    else -> false
                }
                if (coincide) {
                    val e1 = real.layout.edges[s1.edgeIndex].edge
                    val e2 = real.layout.edges[s2.edgeIndex].edge
                    println("REPRO OVERLAP seg1=$e1 ${s1.a}->${s1.b}  seg2=$e2 ${s2.a}->${s2.b}")
                }
            }
        }

        driver.close()
    }
}
