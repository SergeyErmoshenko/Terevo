package me.terevo.layout

import me.terevo.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalkerLayoutEngineTest {
    private val engine = WalkerLayoutEngine()
    private val metrics = NodeMetrics(emptyMap(), Size(NODE_WIDTH, NODE_HEIGHT))

    @Test
    fun `golden singleton`() {
        assertGolden(
            graphOf(listOf("person")),
            "person=0,0",
        )
    }

    @Test
    fun `golden married pair`() {
        assertGolden(
            graphOf(listOf("first", "second"), listOf(union("first", "second"))),
            "first=0,0|second=192,0",
        )
    }

    @Test
    fun `golden three generations`() {
        assertGolden(
            graphOf(
                listOf("grandparent", "parent", "child"),
                listOf(parentage("grandparent", "parent"), parentage("parent", "child")),
            ),
            "child=0,448|grandparent=0,0|parent=0,224",
        )
    }

    @Test
    fun `golden remarriage keeps partners in one compound group`() {
        assertGolden(
            graphOf(
                listOf("person", "first", "second", "childA", "childB"),
                listOf(
                    union("person", "first"),
                    union("person", "second"),
                    parentage("person", "childA"),
                    parentage("first", "childA"),
                    parentage("person", "childB"),
                    parentage("second", "childB"),
                ),
            ),
            "childA=92,224|childB=292,224|first=0,0|person=192,0|second=384,0",
        )
    }

    @Test
    fun `golden unmarried co-parents are grouped adjacent to their shared child`() {
        assertGolden(
            graphOf(
                listOf("father", "mother", "child"),
                listOf(parentage("father", "child"), parentage("mother", "child")),
            ),
            "child=96,224|father=0,0|mother=192,0",
        )
    }

    @Test
    fun `golden adoption uses the same geometry as parentage`() {
        assertGolden(
            graphOf(listOf("parent", "child"), listOf(parentage("parent", "child", biological = false))),
            "child=0,224|parent=0,0",
        )
    }

    @Test
    fun `widest root subtree is balanced toward the center instead of alphabetical order`() {
        val graph = graphOf(
            listOf("a_wide", "c1", "c2", "c3", "m_narrow1", "n1", "z_narrow2", "n2"),
            listOf(
                parentage("a_wide", "c1"),
                parentage("a_wide", "c2"),
                parentage("a_wide", "c3"),
                parentage("m_narrow1", "n1"),
                parentage("z_narrow2", "n2"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val wideCenter = layout.rectOf(nodeId("a_wide"))!!.centerX
        val narrow1Center = layout.rectOf(nodeId("m_narrow1"))!!.centerX
        val narrow2Center = layout.rectOf(nodeId("z_narrow2"))!!.centerX

        assertTrue(wideCenter in minOf(narrow1Center, narrow2Center)..maxOf(narrow1Center, narrow2Center))
        assertNoOverlaps(layout)
    }

    @Test
    fun `a narrow branch nested under a common ancestor is balanced among its siblings, not shoved to alphabetical order`() {
        val graph = graphOf(
            listOf("top", "wide", "w1", "w2", "w3", "narrow_a", "narrow_z"),
            listOf(
                parentage("top", "wide"),
                parentage("top", "narrow_a"),
                parentage("top", "narrow_z"),
                parentage("wide", "w1"),
                parentage("wide", "w2"),
                parentage("wide", "w3"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val wideCenter = layout.rectOf(nodeId("wide"))!!.centerX
        val narrowACenter = layout.rectOf(nodeId("narrow_a"))!!.centerX
        val narrowZCenter = layout.rectOf(nodeId("narrow_z"))!!.centerX

        assertTrue(wideCenter in minOf(narrowACenter, narrowZCenter)..maxOf(narrowACenter, narrowZCenter))
        assertNoOverlaps(layout)
    }

    @Test
    fun `unmarried co-parents with asymmetric ancestor depth are placed side by side, not stacked`() {
        val graph = graphOf(
            nodes = listOf("grandfather", "father", "mother", "child"),
            edges = listOf(
                parentage("grandfather", "father"),
                parentage("father", "child"),
                parentage("mother", "child"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val fatherRect = layout.rectOf(nodeId("father"))!!
        val motherRect = layout.rectOf(nodeId("mother"))!!
        assertEquals(fatherRect.top, motherRect.top)
        assertFalse(fatherRect.intersects(motherRect))
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `a couple whose two members each bring their own parents keeps both ancestor lineages anchored above the couple`() {
        val graph = graphOf(
            nodes = listOf("fatherGrandfather", "motherGrandfather", "father", "mother", "child"),
            edges = listOf(
                parentage("fatherGrandfather", "father"),
                parentage("motherGrandfather", "mother"),
                union("father", "mother"),
                parentage("father", "child"),
                parentage("mother", "child"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val fatherCenter = layout.rectOf(nodeId("father"))!!.centerX
        val motherCenter = layout.rectOf(nodeId("mother"))!!.centerX
        val fatherGrandfatherCenter = layout.rectOf(nodeId("fatherGrandfather"))!!.centerX
        val motherGrandfatherCenter = layout.rectOf(nodeId("motherGrandfather"))!!.centerX

        // The two grandfathers are unrelated lineages that merely land on the same generation, so
        // clusterSpacing is free to push them wider than the couple's own narrow footprint below -
        // that's the intended fix for "unrelated branches read as too close together". What must
        // still hold is that each grandfather stays on his own side and closer to his own child than
        // to the in-law child, instead of drifting past the midpoint.
        assertTrue(fatherGrandfatherCenter < motherGrandfatherCenter)
        assertTrue(
            kotlin.math.abs(fatherGrandfatherCenter - fatherCenter) < kotlin.math.abs(fatherGrandfatherCenter - motherCenter),
        )
        assertTrue(
            kotlin.math.abs(motherGrandfatherCenter - motherCenter) < kotlin.math.abs(motherGrandfatherCenter - fatherCenter),
        )
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `a wide subtree tucks its shallow row close to a plain sibling instead of reserving its full width`() {
        val graph = graphOf(
            nodes = listOf("top", "a", "b", "w1", "w2", "w3"),
            edges = listOf(
                parentage("top", "a"),
                parentage("top", "b"),
                parentage("b", "w1"),
                parentage("b", "w2"),
                parentage("b", "w3"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val aRect = layout.rectOf(nodeId("a"))!!
        val bRect = layout.rectOf(nodeId("b"))!!

        assertTrue(kotlin.math.abs(40.0 - (aRect.left - bRect.right)) < 0.01)
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `a marriage between descendants of two separate branches pulls both ancestor lineages toward the couple`() {
        val graph = graphOf(
            nodes = listOf(
                "ancestorA", "ancestorB",
                "parentA1", "parentA2", "parentB1", "parentB2",
                "cousinA", "cousinB",
            ),
            edges = listOf(
                parentage("ancestorA", "parentA1"),
                parentage("ancestorA", "parentA2"),
                parentage("ancestorB", "parentB1"),
                parentage("ancestorB", "parentB2"),
                parentage("parentA1", "cousinA"),
                parentage("parentB1", "cousinB"),
                union("cousinA", "cousinB"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val cousinACenter = layout.rectOf(nodeId("cousinA"))!!.centerX
        val cousinBCenter = layout.rectOf(nodeId("cousinB"))!!.centerX
        val parentA1Center = layout.rectOf(nodeId("parentA1"))!!.centerX
        val parentB1Center = layout.rectOf(nodeId("parentB1"))!!.centerX
        val bracket = minOf(parentA1Center, parentB1Center)..maxOf(parentA1Center, parentB1Center)

        // The married couple should end up sandwiched between the two ancestor lineages it
        // connects, not off to one side with one branch stranded wherever its own unrelated
        // subtree happened to place it.
        assertTrue(cousinACenter in bracket)
        assertTrue(cousinBCenter in bracket)
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `a partner-alignment pull on one spouse also raises their own tightly-linked parent`() {
        // "q" has one shallow child "c2", who later has a child "gc" with a much deeper partner
        // "z". Aligning co-parents "c2" and "z" to the same generation pulls "c2" several
        // generations deeper than "q". Reproduction of a real reported bug: GenerationAssigner.relax
        // used to only cascade that pull to "c2"'s own descendants, never back up to "q", so "q"'s
        // own direct-parent edge to "c2" ended up spanning every generation in between and rendered
        // as a multi-bend dummy-waypoint zigzag instead of a straight one-hop connector. Since "q"
        // was tightly (no slack) one generation above "c2"'s old position, it must be pulled down
        // right along with "c2" so the edge between them stays a plain single hop.
        val edge = parentage("q", "c2")
        val graph = graphOf(
            nodes = listOf("q", "c2", "z", "zAncestor1", "zAncestor2", "zAncestor3", "zAncestor4", "gc"),
            edges = listOf(
                edge,
                parentage("zAncestor1", "zAncestor2"),
                parentage("zAncestor2", "zAncestor3"),
                parentage("zAncestor3", "zAncestor4"),
                parentage("zAncestor4", "z"),
                parentage("c2", "gc"),
                parentage("z", "gc"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))
        val path = layout.edges.single { it.edge == edge }

        assertEquals(4, layout.generations.getValue(nodeId("c2")))
        assertEquals(3, layout.generations.getValue(nodeId("q")))
        // A plain single-generation edge always renders as the standard 4-point parent-bus-child
        // bracket (see EdgeRouter.routeParentage) - more than 4 would mean it's still bending
        // through leftover dummy waypoints from a multi-generation gap.
        assertEquals(4, path.segments.size, "expected a plain single-hop bracket, got ${path.segments}")
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `a remarried couple's grandchild stays clustered with its blood family, not an unrelated one`() {
        // Anonymized reproduction of a real reported bug (node names are meaningless placeholders,
        // but the edge topology is copied verbatim from the real family tree that triggered it - a
        // hand-crafted minimal graph didn't create enough crossing-minimization pressure to
        // reproduce the failure, this shape reliably does). "p26" has three children including
        // "p25" and "p30". "p25" marries "p24"+"p27"'s child "p28", whose grandparents "p37"+"p38"
        // are an unrelated in-law couple reached through a multi-generation edge (rendered via
        // dummy waypoints). A third, unrelated lineage ("p22" -> "p21" -> "p16"/"p17"/"p19", where
        // "p19" also married into an 8-child family) lands on the same generation as "p24"/"p27"
        // and "p30"'s children "p32"/"p33"/"p34". Before the fix, that unrelated lineage's
        // descendants got sorted in between "p24" and "p32"/"p33"/"p34", slicing "p26"'s blood
        // family in half.
        val graph = graphOf(
            nodes = listOf(
                "p00", "p01", "p02", "p03", "p04",
                "p05", "p06", "p07",
                "p08", "p09", "p10", "p11", "p12", "p13", "p14", "p15",
                "p16", "p17", "p18", "p19", "p20",
                "p21", "p22",
                "p23", "p24", "p25", "p26",
                "p27", "p28", "p29",
                "p30", "p31", "p32", "p33", "p34", "p35", "p36",
                "p37", "p38", "p39",
            ),
            edges = listOf(
                parentage("p01", "p00"),
                parentage("p02", "p00"),
                union("p02", "p01"),
                parentage("p04", "p01"),
                parentage("p03", "p02"),
                parentage("p05", "p03"),
                parentage("p09", "p06"),
                parentage("p07", "p06"),
                parentage("p09", "p05"),
                parentage("p07", "p05"),
                union("p09", "p07"),
                parentage("p18", "p08"), parentage("p19", "p08"),
                parentage("p18", "p09"), parentage("p19", "p09"),
                parentage("p18", "p10"), parentage("p19", "p10"),
                parentage("p18", "p11"), parentage("p19", "p11"),
                parentage("p18", "p12"), parentage("p19", "p12"),
                parentage("p18", "p13"), parentage("p19", "p13"),
                parentage("p18", "p14"), parentage("p19", "p14"),
                parentage("p18", "p15"), parentage("p19", "p15"),
                union("p19", "p18"),
                union("p13", "p29"),
                parentage("p21", "p19"),
                parentage("p21", "p17"),
                parentage("p21", "p16"),
                union("p17", "p20"),
                parentage("p22", "p21"),
                parentage("p24", "p23"),
                parentage("p27", "p23"),
                union("p24", "p27"),
                parentage("p25", "p24"),
                parentage("p28", "p24"),
                union("p25", "p28"),
                parentage("p26", "p25"),
                parentage("p23", "p04"),
                parentage("p37", "p27"),
                parentage("p38", "p27"),
                union("p37", "p38"),
                parentage("p39", "p28"),
                parentage("p26", "p30"),
                parentage("p26", "p35"),
                parentage("p26", "p36"),
                union("p31", "p30"),
                parentage("p30", "p32"),
                parentage("p30", "p33"),
                parentage("p30", "p34"),
                parentage("p31", "p32"),
                parentage("p31", "p33"),
                parentage("p31", "p34"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val cousinALeft = layout.rectOf(nodeId("p24"))!!.left
        val cousinBLeft = layout.rectOf(nodeId("p32"))!!.left
        val bloodFamilyBracket = minOf(cousinALeft, cousinBLeft)..maxOf(cousinALeft, cousinBLeft)

        for (interloper in listOf("p16", "p17", "p18", "p19", "p20")) {
            val left = layout.rectOf(nodeId(interloper))!!.left
            assertFalse(
                left in bloodFamilyBracket,
                "unrelated $interloper ($left) landed between the blood-family cousins " +
                    "p24 ($cousinALeft) and p32 ($cousinBLeft)",
            )
        }
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `descendants of a couple bridging two lineages move with it instead of being left behind`() {
        // "husband" and "wife" come from two different lineages, so their merged group bridges two
        // clusters and gets shifted to sit between them (see compactClusters). Their own child
        // "descendant" must travel with that shift.
        //
        // Shifting the couple alone tore it away from the generation below, which stayed at its
        // cluster's unshifted offset - the child landed 164px off its own parents' midpoint, which
        // is the "a shove on one row never reaches the descendants who never saw it" failure the
        // header comment in CoordinateAssignment warns about. Reported as the youngest descendant
        // visibly sliding sideways out from under her parents.
        val graph = graphOf(
            nodes = listOf("hisParent", "herParent", "husband", "wife", "descendant"),
            edges = listOf(
                parentage("hisParent", "husband"),
                parentage("herParent", "wife"),
                union("husband", "wife"),
                parentage("husband", "descendant"),
                parentage("wife", "descendant"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val coupleMidpoint = (
            layout.rectOf(nodeId("husband"))!!.centerX + layout.rectOf(nodeId("wife"))!!.centerX
            ) / 2.0
        val descendantCenter = layout.rectOf(nodeId("descendant"))!!.centerX
        val drift = kotlin.math.abs(descendantCenter - coupleMidpoint)

        assertTrue(
            drift <= 1.0,
            "the only child of a lineage-bridging couple sits ${drift}px off their midpoint " +
                "(child $descendantCenter vs midpoint $coupleMidpoint) - it was left behind when " +
                "the couple was shifted between clusters",
        )
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `two in-law couples share the unavoidable drift instead of one taking all of it`() {
        // A couple ("husband"+"wife") where BOTH members have their own parents recorded:
        // "hisFather"+"hisMother" above him, "herFather"+"herMother" above her. Each grandparent
        // couple is the root of its own cluster, tied to the tree only through their one child.
        //
        // Some drift here is geometry, not a defect: two 352px-wide couple boxes in different
        // clusters must be at least (176 + siblingSpacing + clusterSpacing + 176) apart, while the
        // two children they sit above share ONE box and are only (NODE_WIDTH + spouseSpacing)
        // apart. The difference cannot be removed by any placement - it can only be distributed.
        //
        // What matters is that neither side absorbs all of it: one couple sitting exactly above its
        // child while the other takes the entire deficit drags that second couple's connector back
        // across the whole row. This asserts the deficit is shared roughly evenly.
        val graph = graphOf(
            nodes = listOf(
                "hisFather", "hisMother",
                "herFather", "herMother",
                "husband", "wife",
                "child",
            ),
            edges = listOf(
                union("hisFather", "hisMother"),
                parentage("hisFather", "husband"),
                parentage("hisMother", "husband"),

                union("herFather", "herMother"),
                parentage("herFather", "wife"),
                parentage("herMother", "wife"),

                union("husband", "wife"),
                parentage("husband", "child"),
                parentage("wife", "child"),
            ),
        )

        val options = LayoutOptions()
        val layout = engine.layout(LayoutRequest(graph, metrics, options))

        val coupleWidth = 2 * NODE_WIDTH + options.spouseSpacing
        val unavoidable =
            (coupleWidth + options.siblingSpacing + options.clusterSpacing) - (NODE_WIDTH + options.spouseSpacing)

        val drifts = listOf(
            Triple("hisFather", "hisMother", "husband"),
            Triple("herFather", "herMother", "wife"),
        ).map { (father, mother, ownChild) ->
            val midpoint = (
                layout.rectOf(nodeId(father))!!.centerX + layout.rectOf(nodeId(mother))!!.centerX
                ) / 2.0
            kotlin.math.abs(midpoint - layout.rectOf(nodeId(ownChild))!!.centerX)
        }

        // Neither couple may carry substantially more than its half of the unavoidable deficit.
        val fairShare = unavoidable / 2.0 + NODE_WIDTH / 2.0
        assertTrue(
            drifts.all { it <= fairShare },
            "expected the unavoidable ${unavoidable}px deficit to be shared (about " +
                "${unavoidable / 2.0}px each), but the two in-law couples drifted $drifts - " +
                "one of them absorbed nearly all of it and its connector runs back across the row",
        )
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics, options)))
    }

    @Test
    fun `a spouse faces the side their own family is on so the parentage edge does not cross their partner`() {
        // Anonymized reproduction of a real reported bug. "husband" and "wife" are merged into one
        // couple box by spouseGroups, which orders the members by name - so whether a member ends up
        // on the left or right of the box was unrelated to where their own relatives sit.
        //
        // Here "wife" is the one who married in: her parents and four siblings form a whole branch
        // off to one side, while "husband"'s own lineage descends on the other. When the by-name
        // order put "wife" on the side facing away from her family, her parentage edge had to reach
        // back across "husband"'s box and over his descending line - which is exactly the tangle
        // reported on a real tree ("the branches got mixed up at that generation").
        val graph = graphOf(
            nodes = listOf(
                "husbandParent",
                "husband", "wife", "husbandSibling",
                "child",
                "wifeFather", "wifeMother",
                "wifeSibA", "wifeSibB", "wifeSibC", "wifeSibD",
            ),
            edges = listOf(
                parentage("husbandParent", "husband"),
                parentage("husbandParent", "husbandSibling"),
                union("husband", "wife"),
                parentage("husband", "child"),
                parentage("wife", "child"),

                union("wifeFather", "wifeMother"),
                parentage("wifeFather", "wife"),
                parentage("wifeMother", "wife"),
                parentage("wifeFather", "wifeSibA"),
                parentage("wifeMother", "wifeSibA"),
                parentage("wifeFather", "wifeSibB"),
                parentage("wifeMother", "wifeSibB"),
                parentage("wifeFather", "wifeSibC"),
                parentage("wifeMother", "wifeSibC"),
                parentage("wifeFather", "wifeSibD"),
                parentage("wifeMother", "wifeSibD"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val husbandCenter = layout.rectOf(nodeId("husband"))!!.centerX
        val wifeCenter = layout.rectOf(nodeId("wife"))!!.centerX
        val wifeParentsCenter = (
            layout.rectOf(nodeId("wifeFather"))!!.centerX + layout.rectOf(nodeId("wifeMother"))!!.centerX
            ) / 2.0

        // Whichever side the wife's parents ended up on, she must be the member of the couple box
        // on that same side - otherwise her parentage edge crosses back over her husband.
        val parentsAreRight = wifeParentsCenter > husbandCenter
        assertTrue(
            if (parentsAreRight) wifeCenter > husbandCenter else wifeCenter < husbandCenter,
            "wife ($wifeCenter) is on the wrong side of husband ($husbandCenter): her own parents " +
                "are at $wifeParentsCenter, so her parentage edge has to cross his box",
        )
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `the parent couple carrying the lineage stays above their own child instead of sliding to the row edge`() {
        // Anonymized reproduction of a real reported bug, edge topology copied from the family tree
        // that triggered it. "ancestor"+"ancestorSpouse" have five children; "parent" is the one who
        // carries the line onward. "parent" marries "parentSpouse" (merged into ONE group box by
        // spouseGroups) and they have "heir" and "heirSibling"; "heir" continues to "descendant".
        // "parent"'s four childless siblings share its row.
        //
        // Two independent defects put that couple near the left edge of their row while their own
        // child sat mid-row, so the direct blood line visibly jogged sideways at that generation
        // (reported as "the branch went left, then right, and everything got mixed up"):
        //  - crossingsBetween counted inversions among edges leaving the SAME parent group. Those
        //    edges share an endpoint and cannot cross, so the score tracked the order relations
        //    happened to be recorded in, and `order` discarded correct rows for lower phantom
        //    scores (a lone parent of five children scored 6 "crossings" one way and 5 the other).
        //  - sweep's barycenter compared a real neighbor-row position against an own-row index
        //    fallback - two different coordinate scales - so an only-child lineage sorted to the
        //    row's edge purely because the two rows had different lengths.
        val graph = graphOf(
            nodes = listOf(
                "ancestor", "ancestorSpouse",
                "parent", "parentSpouse",
                "parentSibA", "parentSibB", "parentSibC", "parentSibD",
                "heir", "heirSibling",
                "descendant",
            ),
            edges = listOf(
                union("ancestor", "ancestorSpouse"),

                parentage("ancestor", "parent"),
                parentage("ancestorSpouse", "parent"),
                parentage("ancestor", "parentSibA"),
                parentage("ancestorSpouse", "parentSibA"),
                parentage("ancestor", "parentSibB"),
                parentage("ancestorSpouse", "parentSibB"),
                parentage("ancestor", "parentSibC"),
                parentage("ancestorSpouse", "parentSibC"),
                parentage("ancestor", "parentSibD"),
                parentage("ancestorSpouse", "parentSibD"),
                union("parent", "parentSpouse"),

                parentage("parent", "heir"),
                parentage("parentSpouse", "heir"),
                parentage("parent", "heirSibling"),
                parentage("parentSpouse", "heirSibling"),

                parentage("heir", "descendant"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val parentRect = layout.rectOf(nodeId("parent"))!!
        val coupleCenter = (parentRect.centerX + layout.rectOf(nodeId("parentSpouse"))!!.centerX) / 2.0
        val childrenCenter = (
            layout.rectOf(nodeId("heir"))!!.centerX + layout.rectOf(nodeId("heirSibling"))!!.centerX
            ) / 2.0

        // The couple carrying the lineage must sit above their own children rather than be pushed
        // aside by childless siblings. Half a node of slack absorbs ordinary relaxation give-and-take.
        assertTrue(
            kotlin.math.abs(coupleCenter - childrenCenter) <= NODE_WIDTH / 2.0,
            "the lineage-carrying couple (center $coupleCenter) drifted from their own children " +
                "(center $childrenCenter) - the blood line jogs sideways at this generation",
        )
        // And the childless siblings should end up spread on both sides, not all stacked on one.
        val siblingsLeft = listOf("parentSibA", "parentSibB", "parentSibC", "parentSibD")
            .count { layout.rectOf(nodeId(it))!!.centerX < parentRect.centerX }
        assertTrue(
            siblingsLeft in 1..3,
            "expected the childless siblings spread on both sides of the lineage-carrying couple, " +
                "but $siblingsLeft of 4 are to its left",
        )
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `golden disconnected components are spaced horizontally`() {
        assertGolden(
            graphOf(listOf("a", "b", "c"), listOf(parentage("a", "b"))),
            "a=0,0|b=0,224|c=208,0",
        )
    }

    @Test
    fun `golden two hundred siblings do not overlap`() {
        val children = (0 until 200).map { "child${it.toString().padStart(3, '0')}" }
        val graph = graphOf(
            nodes = listOf("parent") + children,
            edges = children.map { parentage("parent", it) },
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val minLeft = children.minOf { layout.rectOf(nodeId(it))!!.left }
        val maxRight = children.maxOf { layout.rectOf(nodeId(it))!!.right }
        assertEquals(Rect(19_900.0, 0.0, 160.0, 64.0), layout.rectOf(nodeId("parent")))
        assertEquals(0.0, minLeft)
        assertEquals(39_960.0, maxRight)
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `golden twenty generations stay vertically aligned`() {
        val nodes = (0 until 20).map { "person${it.toString().padStart(2, '0')}" }
        val graph = graphOf(
            nodes = nodes,
            edges = nodes.zipWithNext().map { (parent, child) -> parentage(parent, child) },
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        assertEquals(Rect(0.0, 0.0, 160.0, 64.0), layout.rectOf(nodeId("person00")))
        assertEquals(Rect(0.0, 4_256.0, 160.0, 64.0), layout.rectOf(nodeId("person19")))
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `spacing options control node distances`() {
        val graph = graphOf(listOf("parent", "a", "b"), listOf(parentage("parent", "a"), parentage("parent", "b")))
        val options = LayoutOptions(siblingSpacing = 50.0, generationSpacing = 120.0)

        val layout = engine.layout(LayoutRequest(graph, metrics, options))

        assertEquals(50.0, layout.rectOf(nodeId("b"))!!.left - layout.rectOf(nodeId("a"))!!.right)
        assertEquals(184.0, layout.rectOf(nodeId("a"))!!.top)
    }

    private fun assertGolden(graph: TreeGraph, expected: String) {
        val request = LayoutRequest(graph, metrics)
        val first = engine.layout(request)
        val second = engine.layout(request)

        assertEquals(expected, coordinates(first))
        assertEquals(first, second)
        assertNoOverlaps(first)
    }

    private fun coordinates(layout: Layout): String = layout.nodes.entries
        .sortedBy { it.key.value }
        .joinToString("|") { (id, rect) -> "${id.value}=${rect.left.toInt()},${rect.top.toInt()}" }

    private fun assertNoOverlaps(layout: Layout) {
        val rects = layout.nodes.values.toList()
        for (first in rects.indices) {
            for (second in first + 1 until rects.size) {
                assertFalse(rects[first].intersects(rects[second]), "${rects[first]} overlaps ${rects[second]}")
            }
        }
        assertTrue(layout.bounds.width >= 0.0)
    }
}
