package me.terevo.layout

import kotlin.math.abs

// X-coordinate assignment for a layered graph (priority-method relaxation, the standard
// simplification of Sugiyama coordinate assignment): every group is pulled toward the average
// position of *all* the actual nodes it's connected to in the adjacent generation - not just a
// single tree-parent - so second marriages, cousins and in-laws land close to where they actually
// belong instead of wherever a recursive tree walk happened to leave them.
//
// Neighbors come from explicit node-level adjacency maps rather than the raw TreeGraph, because a
// Parentage edge spanning more than one generation is represented here as a chain through dummy
// waypoint nodes (see WalkerLayoutEngine) - reading straight from TreeGraph would pull the real
// parent and child toward each other's x directly, ignoring whatever sits between them.
internal object CoordinateAssignment {

    // A group that's mutually pulled by another (e.g. two co-parents, each with no neighbor but
    // the other's child/parent) fully swaps positions with it every pass under a plain Jacobi
    // update - a stable 2-cycle that never settles (verified: an even iteration count lands back
    // on the untouched initial layout, an odd count lands one full swap away, neither the correct
    // converged position). Moving only DAMPING of the way from the old position to the neighbor
    // average each pass breaks the cycle: for a mutual pair it reaches the exact midpoint in one
    // pass instead of oscillating around it forever.
    private const val DAMPING: Double = 0.5

    // A pull takes one pass per generation to round-trip from one end of a lineage chain to the
    // other and back, and damping slows that further - a long, thin chain (a single-child lineage
    // many generations deep, linked to another such chain only through a shared marriage-group
    // several generations down, as seen in one real family tree used to verify this) can need far
    // more passes than the generation count to actually settle: too few passes looks converged
    // (each step's movement keeps shrinking) while still hundreds of pixels from the true fixed
    // point, which reads as a node sitting oddly far from both its parent and its own child. This
    // cap only guards against a pathological graph that never settles; it's generous enough to
    // never bind for realistic family trees.
    private const val MAX_ITERATIONS: Int = 500
    private const val CONVERGENCE_THRESHOLD: Double = 0.01

    // Resolving overlaps one row at a time has no notion that "this branch" and "that
    // unrelated branch" are the SAME two blocks at every generation they coexist: each row
    // gets clamped to the spacing floor independently, so the offset between the two
    // blocks can silently differ from row to row - which reads as a lineage zigzagging
    // sideways generation over generation instead of running in a straight line (confirmed
    // against a real family tree: two unrelated in-law lineages sat at exactly the minimum
    // allowed gap at TWO separate generations, with a different actual offset at each,
    // bending an otherwise-straight direct blood line sideways). compactClusters below
    // fixes this by moving each blood-lineage cluster (see LayerOrdering's `cluster` map)
    // as a single rigid block - a simplified form of the subtree-translation classic
    // tree-layout algorithms (Reingold-Tilford, Walker) use - so a cluster's own internal
    // shape, exactly as relaxToConvergence computed it, is never bent by a neighboring
    // row's independent clamp.

    fun assign(
        order: Map<Int, List<Group>>,
        widths: Map<Group, Double>,
        groupOf: Map<NodeId, Group>,
        nodeParentsOf: Map<NodeId, List<NodeId>>,
        nodeChildrenOf: Map<NodeId, List<NodeId>>,
        metrics: NodeMetrics,
        siblingSpacing: Double,
        spouseSpacing: Double,
        cluster: Map<Group, Group>,
        clusterSpacing: Double,
    ): Map<Group, Double> {
        // Two adjacent groups that belong to different family clusters get the ordinary sibling
        // gap plus clusterSpacing on top, so an unrelated branch that merely landed on the same
        // generation reads as a clearly separate block instead of packed as tight as siblings
        // under the same parent - and so a multi-generation edge's dummy waypoint chain has room
        // to settle on a straight line instead of being shoved sideways by a packed-in neighbor.
        fun gapBetween(a: Group, b: Group): Double =
            if ((cluster[a] ?: a) == (cluster[b] ?: b)) siblingSpacing else siblingSpacing + clusterSpacing

        val generations = order.keys.sorted()
        val centers = mutableMapOf<Group, Double>()
        for (generation in generations) {
            var left = 0.0
            var previous: Group? = null
            for (group in order.getValue(generation)) {
                val gap = previous?.let { gapBetween(it, group) } ?: 0.0
                centers[group] = left + gap + widths.getValue(group) / 2.0
                left += gap + widths.getValue(group)
                previous = group
            }
        }
        if (generations.size <= 1) return centers

        // Pulls every group toward the average position of its real neighbors, reading a single
        // start-of-pass snapshot rather than a neighbor row's already-updated value (Gauss-Seidel-
        // style live reads turn each pass into a feedback loop between rows - for one real family
        // tree this measurably diverged without bound). Overlap resolution is deliberately NOT
        // applied inside this pass: two branches connected only through a marriage several
        // generations away form a cycle through that shared couple, and feeding an overlap-
        // *inflated* position back as the next pass's neighbor position closes a genuine positive-
        // feedback loop - each pass's forced spacing correction makes the next pass's pull demand
        // even more spacing, without bound (verified against a real family tree: two in-law
        // lineages, connected only via their children's marriage, drifted apart by several hundred
        // pixels per pass with no sign of settling). Plain averaging has no such loop: it's a
        // convex combination of neighbor positions, so it can never push a value outside the range
        // already present in the graph - overlaps are left for the one-shot declamp pass below to
        // sort out, using the settled, un-inflated positions. Returns the largest single-group move
        // made this pass, so the caller can tell when the system has actually settled.
        fun relax(damping: Double): Double {
            val snapshot = centers.toMap()
            var maxMove = 0.0
            for (generation in generations) {
                for (group in order.getValue(generation)) {
                    // Grouping by the *neighboring* group before averaging - rather than averaging
                    // every individual parent/child NodeId directly - stops a multi-member family
                    // unit on one side (e.g. two co-parents) from outvoting a single member on the
                    // other side (e.g. their one child) just because it happens to have more people
                    // recorded. Every ParentChild edge to that unit still counts (a couple who share
                    // two children is a stronger pull than one who shares one), but the unit itself
                    // only ever contributes ONE vote to this group's average, matching how the
                    // layout actually reads: "the parent generation is over there" and "the child
                    // generation is over there" are two pulls of equal weight, not one pull per
                    // person. Confirmed against a real family tree: without this, a single-child
                    // lineage was dragged 2:1 toward its own (separately, heavily sibling-crowded)
                    // parent couple and away from its own child, producing a visible zigzag right at
                    // that generation - exactly what a user reported as "the branch escapes to the
                    // other side."
                    val neighborsByGroup = group.nodes.flatMap { node ->
                        (nodeParentsOf[node].orEmpty() + nodeChildrenOf[node].orEmpty()).mapNotNull { other ->
                            val otherGroup = groupOf[other] ?: return@mapNotNull null
                            otherGroup to centerOf(other, otherGroup, snapshot, widths, metrics, spouseSpacing)
                        }
                    }.groupBy({ it.first }, { it.second })
                    if (neighborsByGroup.isNotEmpty()) {
                        val pull = neighborsByGroup.values.map { it.average() }.average()
                        val next = snapshot.getValue(group) + damping * (pull - snapshot.getValue(group))
                        maxMove = maxOf(maxMove, abs(next - snapshot.getValue(group)))
                        centers[group] = next
                    }
                }
            }
            return maxMove
        }

        fun relaxToConvergence() {
            var iteration = 0
            while (iteration < MAX_ITERATIONS && relax(DAMPING) >= CONVERGENCE_THRESHOLD) {
                iteration++
            }
            // One final undamped pass closes the last asymptotic sliver a damped approach never
            // quite reaches (e.g. a child centered exactly between two co-parents lands on the
            // exact integer pixel instead of stopping a hair short of it). A single undamped jump
            // from a position this close to the fixed point can't restart the 2-cycle oscillation
            // damping exists to avoid - that requires repeated undamped passes, and this is only
            // ever one.
            relax(damping = 1.0)
        }

        relaxToConvergence()

        // Two clusters that never directly connect at this generation boundary (their only
        // real link is several generations further down, through a shared marriage) can be
        // ordered LEFT-of-the-other in one row and RIGHT-of-the-other in the very next row:
        // crossing-minimization scores each row independently against its own immediate
        // neighbors, so nothing stops it from "freezing" adjacent rows at different sweep
        // iterations that each locally looked best but mutually disagree on which of two
        // unrelated branches goes on which side (confirmed against a real family tree: the
        // in-law branch sat LEFT of the direct bloodline one generation down, and RIGHT of it
        // one generation up, with no edge connecting them directly at either boundary). A
        // single rigid per-cluster offset is only well-defined when every row agrees on
        // left-right cluster order - so canonicalize it first.
        val canonicalOrder = canonicalizeClusterOrder(generations, order, cluster)
        compactClusters(
            generations, canonicalOrder, centers, widths, groupOf, nodeParentsOf, nodeChildrenOf, metrics,
            siblingSpacing, spouseSpacing, cluster, ::gapBetween,
        )

        straightenSingleChildren(
            generations, canonicalOrder, centers, widths, groupOf, nodeParentsOf, nodeChildrenOf, metrics,
            siblingSpacing, spouseSpacing, cluster, ::gapBetween,
        )

        return centers
    }

    // Pulls a group onto its parents' own center where the row has room for it, so the connector
    // between them is a single straight vertical line instead of stepping sideways.
    //
    // Relaxation averages every neighbor a group has, so a group also pulled on by its own children
    // and its siblings settles near - but rarely exactly on - its parents' center, and the few
    // pixels left over still render as a visible dog-leg right under the card. Widening a gap is
    // never acceptable for this, but where neighbours leave genuine slack the offset is pure noise.
    // Confirmed against a real family tree: an only child sat 30px off its parents' midpoint and
    // its line visibly kinked just below the card.
    //
    // Only the slack already present is consumed - each group may move at most up to its
    // neighbours' required gaps, so no spacing floor is ever broken and a row that is genuinely
    // packed keeps its step. Any shift is inherited by the group's descendants: moving an only
    // child without moving the children hanging below it just relocates the kink to the next
    // generation and makes a growing sibling block appear to creep sideways (-65px after four
    // children in the reported case).
    private fun straightenSingleChildren(
        generations: List<Int>,
        order: Map<Int, List<Group>>,
        centers: MutableMap<Group, Double>,
        widths: Map<Group, Double>,
        groupOf: Map<NodeId, Group>,
        nodeParentsOf: Map<NodeId, List<NodeId>>,
        nodeChildrenOf: Map<NodeId, List<NodeId>>,
        metrics: NodeMetrics,
        siblingSpacing: Double,
        spouseSpacing: Double,
        cluster: Map<Group, Group>,
        gapBetween: (Group, Group) -> Double,
    ) {
        val inheritedShift = mutableMapOf<Group, Double>()

        // Bracket-reach overhang (see compactClusters) is only fenced off between DIFFERENT
        // clusters there - two full siblings under the very same parents get no such protection,
        // so a sibling with a wide subtree of its own can still be squeezed against its neighbor
        // right here, in this row's own overlap correction. Confirmed against a real family tree:
        // sibling "5"'s whole branch sat pressed up against its neighboring sibling's subtree one
        // generation down, even though neither sibling's own card overlapped anything at their own
        // row.
        //
        // Scoped to SAME-cluster pairs only - cross-cluster spacing is already reach-aware in
        // compactClusters, which additionally balances the deficit a bridging couple absorbs
        // between two lineages by averaging offsets computed from one pre-shift snapshot. Redoing
        // that same reach check here, on top of compactClusters' result and using centers it has
        // since shifted, double-applies the correction and reopens the very imbalance
        // compactClusters' averaging exists to prevent (confirmed: two in-law couples that used to
        // split a 520px unavoidable deficit ~260px each instead landed at 600px apiece once this
        // ran unscoped on their row too).
        //
        // Only reserved when BOTH siblings actually have children of their own: a shallow sibling
        // with no descendants has nothing to collide with, and deliberately tucks in at plain card
        // width (see "a wide subtree tucks its shallow row close to a plain sibling instead of
        // reserving its full width") - reserving room it will never use would just make every tree
        // with any wide branch needlessly sprawl sideways.
        fun hasChildren(group: Group): Boolean = group.nodes.any { nodeChildrenOf[it].orEmpty().isNotEmpty() }

        fun reachAwareGap(left: Group, right: Group): Double =
            if ((cluster[left] ?: left) == (cluster[right] ?: right) && hasChildren(left) && hasChildren(right)) {
                rightReach(left, groupOf, nodeChildrenOf, centers, widths, metrics, spouseSpacing, siblingSpacing) +
                    gapBetween(left, right) +
                    leftReach(right, groupOf, nodeChildrenOf, centers, widths, metrics, spouseSpacing, siblingSpacing)
            } else {
                gapBetween(left, right)
            }
        for (generation in generations) {
            val row = order.getValue(generation)

            // Carry prior straightening down before deciding whether this row has further slack.
            // Siblings inherit the same parent delta, so their whole block moves rigidly instead of
            // drifting relative to the parent as more children are added.
            for (group in row) {
                val parentGroups = group.nodes
                    .flatMap { nodeParentsOf[it].orEmpty() }
                    .mapNotNull { groupOf[it] }
                    .distinct()
                val inherited = parentGroups.mapNotNull(inheritedShift::get).distinct()
                if (inherited.size == 1) {
                    centers[group] = centers.getValue(group) + inherited.single()
                    inheritedShift[group] = inherited.single()
                }
            }

            // Moving a whole inherited block can consume row slack, so re-resolve once before the
            // local straightening below. This keeps the no-overlap guarantee intact.
            //
            // This is also the only place same-cluster sibling overlap gets resolved at all
            // (compactClusters only guarantees no CROSS-cluster overlap) - so any push this
            // produces must be carried down to descendants exactly like the only-child straighten
            // below does, or a wide sibling row shoving its own parent group sideways to make room
            // (e.g. many new children crowding a couple's row) leaves that couple's own child
            // behind, unable to follow: the child's row has no slack of its own to close a gap this
            // large, so the "only child" step below silently fails to catch up (confirmed against
            // the reported bug: a bridging in-law couple's child sat 1300px off its parents after
            // 15 unrelated siblings were added elsewhere in the row above).
            val resolved = resolveOverlaps(row, centers, widths, ::reachAwareGap)
            for (group in row) {
                val delta = resolved.getValue(group) - centers.getValue(group)
                centers[group] = resolved.getValue(group)
                if (delta != 0.0) {
                    inheritedShift[group] = (inheritedShift[group] ?: 0.0) + delta
                }
            }

            for ((index, group) in row.withIndex()) {
                val parentGroups = group.nodes
                    .flatMap { nodeParentsOf[it].orEmpty() }
                    .mapNotNull { groupOf[it] }
                    .distinct()
                if (parentGroups.isEmpty()) continue

                // Only an ONLY child belongs on its parents' center. Siblings belong distributed
                // AROUND that center, so pulling each of them onto it individually is wrong - and
                // because the row is walked left to right, the squeeze is applied unevenly and the
                // whole sibling block creeps sideways. Measured: a block of children drifted
                // further left of its parent with every child added (-16px at two, -44px at three,
                // -65px at four). Relaxation already centers a sibling block correctly, so groups
                // with siblings are left alone here.
                val siblingGroups = parentGroups
                    .flatMap { parent -> parent.nodes.flatMap { nodeChildrenOf[it].orEmpty() } }
                    .mapNotNull { groupOf[it] }
                    .distinct()
                if (siblingGroups.size > 1) continue

                // Where the parents actually are now, which is the line worth lining up with.
                val target = parentGroups.map(centers::getValue).average()
                val current = centers.getValue(group)
                val delta = target - current
                if (kotlin.math.abs(delta) <= CONVERGENCE_THRESHOLD) continue

                val halfWidth = widths.getValue(group) / 2.0
                val room = if (delta > 0) {
                    val right = row.getOrNull(index + 1)
                    if (right == null) {
                        Double.POSITIVE_INFINITY
                    } else {
                        (centers.getValue(right) - widths.getValue(right) / 2.0) -
                            (current + halfWidth) - gapBetween(group, right)
                    }
                } else {
                    val left = row.getOrNull(index - 1)
                    if (left == null) {
                        Double.POSITIVE_INFINITY
                    } else {
                        (current - halfWidth) -
                            (centers.getValue(left) + widths.getValue(left) / 2.0) - gapBetween(left, group)
                    }
                }
                if (room <= CONVERGENCE_THRESHOLD) continue

                val applied = delta.coerceIn(-room, room)
                centers[group] = current + applied
                inheritedShift[group] = (inheritedShift[group] ?: 0.0) + applied
            }
        }
    }

    // Reorders each row's groups so that whenever two groups belong to different clusters,
    // their left-right order agrees with every other row's order for that same cluster
    // pair - resolving the inconsistency described above. Grouping by cluster and sorting
    // the blocks (rather than sorting individual groups) keeps each cluster's own internal
    // order exactly as crossing-minimization produced it; only the relative placement of
    // whole unrelated blocks can change.
    //
    // Rather than sorting every row by some global summary (e.g. each cluster's average
    // center across all rows), which would happily "fix" a pair that was never actually in
    // conflict just because a third, unrelated row leans the other way, this walks rows top
    // to bottom (ancestors first) and grows one running left-right cluster order: the first
    // row two clusters both appear in decides their relative order for every row after; a
    // cluster seen for the first time is slotted into the running order at the position its
    // own row's crossing-minimized order already implied, relative to whichever
    // already-known clusters share that row. Two clusters that never appear in the same row
    // as each other never get an order imposed between them - harmless, since compactClusters
    // never needs one (a difference constraint only comes from two clusters actually being
    // adjacent somewhere).
    private fun canonicalizeClusterOrder(
        generations: List<Int>,
        order: Map<Int, List<Group>>,
        cluster: Map<Group, Group>,
    ): Map<Int, List<Group>> {
        fun clusterOf(group: Group): Group = cluster[group] ?: group
        val globalOrder = mutableListOf<Group>()
        val result = mutableMapOf<Int, List<Group>>()
        for (generation in generations) {
            val row = order.getValue(generation)
            val blocks = row.groupBy(::clusterOf)
            val rowClusterSequence = row.map(::clusterOf).distinct()

            for (clusterId in rowClusterSequence) {
                if (clusterId in globalOrder) continue
                val precedingKnown = rowClusterSequence
                    .takeWhile { it != clusterId }
                    .lastOrNull { it in globalOrder }
                val insertAt = precedingKnown?.let { globalOrder.indexOf(it) + 1 } ?: 0
                globalOrder.add(insertAt, clusterId)
            }

            result[generation] = blocks.keys
                .sortedBy { globalOrder.indexOf(it) }
                .flatMap { blocks.getValue(it) }
        }
        return result
    }

    // Moves each blood-lineage cluster (LayerOrdering's `cluster` map: every group's
    // topmost ancestor group) as a single rigid block, instead of resolving overlaps one
    // row at a time. For every row, an adjacent pair of groups from two different clusters
    // yields a difference constraint - "the right cluster's offset must be at least this
    // much more than the left cluster's offset" - and the tightest constraint per cluster
    // pair (across every generation they coexist in) wins. Solved as a longest-path
    // relaxation over the (small - a few dozen at most) cluster graph: `order` here is
    // expected to already be canonicalizeClusterOrder's output, so every row agrees on
    // cluster left-right order and this graph is a genuine DAG, settling in at most one
    // pass per cluster.
    private fun compactClusters(
        generations: List<Int>,
        order: Map<Int, List<Group>>,
        centers: MutableMap<Group, Double>,
        widths: Map<Group, Double>,
        groupOf: Map<NodeId, Group>,
        nodeParentsOf: Map<NodeId, List<NodeId>>,
        nodeChildrenOf: Map<NodeId, List<NodeId>>,
        metrics: NodeMetrics,
        siblingSpacing: Double,
        spouseSpacing: Double,
        cluster: Map<Group, Group>,
        gapBetween: (Group, Group) -> Double,
    ) {
        val relaxed = centers.toMap()
        fun clusterOf(group: Group): Group = cluster[group] ?: group

        // constraint(a -> b): offset(b) - offset(a) >= weight
        val constraints = mutableMapOf<Pair<Group, Group>, Double>()
        for (generation in generations) {
            val row = order.getValue(generation)
            for (i in 0 until row.size - 1) {
                val left = row[i]
                val right = row[i + 1]
                val leftCluster = clusterOf(left)
                val rightCluster = clusterOf(right)
                if (leftCluster == rightCluster) continue
                val requiredGap = rightReach(
                    left, groupOf, nodeChildrenOf, relaxed, widths, metrics, spouseSpacing, siblingSpacing,
                ) +
                    gapBetween(left, right) +
                    leftReach(right, groupOf, nodeChildrenOf, relaxed, widths, metrics, spouseSpacing, siblingSpacing)
                val weight = requiredGap - (relaxed.getValue(right) - relaxed.getValue(left))
                val key = leftCluster to rightCluster
                constraints[key] = maxOf(constraints[key] ?: Double.NEGATIVE_INFINITY, weight)
            }
        }

        val clusters = (order.values.flatten().map(::clusterOf)).distinct()
        val offset = clusters.associateWith { 0.0 }.toMutableMap()
        var pass = 0
        while (pass < clusters.size) {
            var changed = false
            for ((pair, weight) in constraints) {
                val (from, to) = pair
                val candidate = offset.getValue(from) + weight
                if (candidate > offset.getValue(to)) {
                    offset[to] = candidate
                    changed = true
                }
            }
            pass++
            if (!changed) break
        }

        // A married couple is merged into ONE group, but LayerOrdering can only put that group in a
        // single cluster - so a couple bridging two lineages is rigidly welded to whichever side
        // won, and inherits that side's offset alone. When separating the two lineages costs more
        // room than the couple's own box provides (two in-law couples must sit clusterSpacing
        // apart, while the two children they each sit above share one box only spouseSpacing wide),
        // the whole unavoidable deficit lands on the losing side: its parents sat 520px from their
        // own child while the winning side's sat exactly above theirs, dragging that connector back
        // across the full row - which is what made the two lines above a couple overlap into one
        // thick line on a real family tree.
        //
        // The deficit itself is geometry and cannot be removed, only shared. A group whose members'
        // parents live in several clusters is therefore shifted to the AVERAGE of those clusters'
        // offsets instead of just its own, which centers it between the lineages pulling on it and
        // splits the deficit evenly.
        //
        // That shift must then carry DOWN to the group's own descendants. Moving a bridging couple
        // alone tears it away from the children below it, which still sat at their cluster's
        // unshifted offset - the couple's own child ended up 164px off the couple's midpoint, the
        // same "a shove on one row never reaches the descendants who never saw it" failure the
        // header comment above warns about. Descendants therefore inherit their parents' shift, so
        // the moved block stays rigid relative to everything hanging off it.
        val shift = mutableMapOf<Group, Double>()
        for (generation in generations) {
            for (group in order.getValue(generation)) {
                val ownCluster = clusterOf(group)
                val parentGroups = group.nodes
                    .flatMap { nodeParentsOf[it].orEmpty() }
                    .mapNotNull { groupOf[it] }
                    .distinct()
                val parentClusters = parentGroups.map(::clusterOf).distinct()
                shift[group] = if (parentClusters.size > 1) {
                    parentClusters.map(offset::getValue).average() - offset.getValue(ownCluster)
                } else {
                    val inherited = parentGroups
                        .filter { clusterOf(it) == ownCluster }
                        .mapNotNull { shift[it] }
                    if (inherited.isEmpty()) 0.0 else inherited.average()
                }
            }
        }

        for (group in centers.keys.toList()) {
            centers[group] =
                relaxed.getValue(group) + offset.getValue(clusterOf(group)) + (shift[group] ?: 0.0)
        }
    }

    // A parent's own card can be far narrower than the bracket EdgeRouter draws to its children:
    // a single parent centered above nine children fans a bus bar out hundreds of pixels either
    // side of its own card edges. Spacing neighbors by card width alone leaves that overhang free
    // to swing into whatever sits next door - confirmed against a real family tree, where a single
    // parent's nine-child bracket swung 837px past its own left edge and crossed straight through
    // a neighboring, unrelated three-child family's own bracket one generation down (the two
    // verticals passed through each other's horizontal bar at points nowhere near either party's
    // own card). Widening the half-extent used for spacing to cover the farthest a group's own
    // children reach - never narrowing it below the card's own half-width - reserves room for that
    // overhang before it can collide with a neighbor. This applies just as much between two full
    // siblings under the same parents as it does between two unrelated families: a sibling with a
    // wide subtree of its own can be squeezed against its neighbor exactly the same way.
    //
    // Reach is measured to the actual CHILD NODE's own edge, not its group's - a child who married
    // in is merged into a couple box together with an in-law spouse who is nobody's descendant
    // here, and charging this parent for that spouse's half of the box as well would reserve space
    // nothing actually needs.
    private fun childEdge(
        child: NodeId,
        sign: Int,
        groupOf: Map<NodeId, Group>,
        centers: Map<Group, Double>,
        widths: Map<Group, Double>,
        metrics: NodeMetrics,
        spouseSpacing: Double,
    ): Double {
        val childGroup = groupOf[child] ?: return Double.NaN
        val center = centerOf(child, childGroup, centers, widths, metrics, spouseSpacing)
        val half = if (childGroup.isDummy) 0.0 else metrics.sizeOf(child).width / 2.0
        return center + sign * half
    }

    // A leaf child's ONLY neighbor is its own single parent, so the priority-method relaxation
    // above pulls it to exactly that parent's x - the whole fan of children collapses onto one
    // point before row-overlap-resolution has ever run for their generation, since that step only
    // happens later, when the top-down walk actually reaches their row. Reach measured from these
    // still-collapsed positions reads as near zero, wildly undercounting the spread
    // resolveOverlaps is about to create - confirmed with a synthetic repro: eight childless
    // grandchildren fanned out to +-780px once their own row was resolved, but the reach measured
    // one generation up (while THIS row was being resolved) saw them still stacked on their
    // parent's exact center and reserved almost no room, so a neighboring sibling's single child
    // ended up shifted so far right to clear its own row's minimum spacing that its connector swept
    // straight across that fan's individual drop lines. The total width the children will need once
    // spread out - sum of their own widths plus the ordinary sibling gaps between them - is a floor
    // that doesn't depend on relaxation having already run, and is used here alongside (not instead
    // of) the position-based measurement, which still matters for a group whose children are pulled
    // wider than their own combined width by deeper descendants.
    private fun childrenSpreadHalfWidth(
        group: Group,
        groupOf: Map<NodeId, Group>,
        nodeChildrenOf: Map<NodeId, List<NodeId>>,
        widths: Map<Group, Double>,
        siblingSpacing: Double,
    ): Double {
        val childGroups = group.nodes.flatMap { nodeChildrenOf[it].orEmpty() }
            .mapNotNull(groupOf::get)
            .distinct()
        if (childGroups.size <= 1) return 0.0
        val totalWidth = childGroups.sumOf { widths.getValue(it) } + (childGroups.size - 1) * siblingSpacing
        return totalWidth / 2.0
    }

    private fun rightReach(
        group: Group,
        groupOf: Map<NodeId, Group>,
        nodeChildrenOf: Map<NodeId, List<NodeId>>,
        centers: Map<Group, Double>,
        widths: Map<Group, Double>,
        metrics: NodeMetrics,
        spouseSpacing: Double,
        siblingSpacing: Double,
    ): Double {
        val ownCenter = centers.getValue(group)
        val childReach = group.nodes.flatMap { nodeChildrenOf[it].orEmpty() }
            .distinct()
            .maxOfOrNull { child -> childEdge(child, +1, groupOf, centers, widths, metrics, spouseSpacing) - ownCenter }
            ?: 0.0
        val spreadReach = childrenSpreadHalfWidth(group, groupOf, nodeChildrenOf, widths, siblingSpacing)
        return maxOf(widths.getValue(group) / 2.0, childReach, spreadReach)
    }

    private fun leftReach(
        group: Group,
        groupOf: Map<NodeId, Group>,
        nodeChildrenOf: Map<NodeId, List<NodeId>>,
        centers: Map<Group, Double>,
        widths: Map<Group, Double>,
        metrics: NodeMetrics,
        spouseSpacing: Double,
        siblingSpacing: Double,
    ): Double {
        val ownCenter = centers.getValue(group)
        val childReach = group.nodes.flatMap { nodeChildrenOf[it].orEmpty() }
            .distinct()
            .maxOfOrNull { child -> ownCenter - childEdge(child, -1, groupOf, centers, widths, metrics, spouseSpacing) }
            ?: 0.0
        val spreadReach = childrenSpreadHalfWidth(group, groupOf, nodeChildrenOf, widths, siblingSpacing)
        return maxOf(widths.getValue(group) / 2.0, childReach, spreadReach)
    }

    // A dummy group has no real size and is never made of more than one node, so its center is
    // just the group's own resolved x - nodeCenterX's multi-node offset walk doesn't apply.
    private fun centerOf(
        node: NodeId,
        group: Group,
        centers: Map<Group, Double>,
        widths: Map<Group, Double>,
        metrics: NodeMetrics,
        spacing: Double,
    ): Double =
        if (group.isDummy) centers.getValue(group) else nodeCenterX(node, group, centers, widths, metrics, spacing)
}
