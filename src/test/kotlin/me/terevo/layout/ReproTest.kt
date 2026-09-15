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

        val violettaId = "48c81d1b-60bc-4be9-b97b-af83401e41f8"
        val exported = me.terevo.export.ExportTreeMapper.map(
            tree,
            me.terevo.layout.LayoutOptions(
                root = NodeId(violettaId),
                mode = me.terevo.layout.LayoutMode.ANCESTORS,
                depth = 6,
            ),
        )
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

        driver.close()
    }
}
