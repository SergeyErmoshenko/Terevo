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
            generations, canonicalOrder, centers, widths, groupOf, nodeParentsOf, cluster, ::gapBetween,
        )

        // One final safety pass over the offset-adjusted positions for same-cluster sibling
        // overlap (e.g. a wide sub-branch's own row needing extra room from its own
        // sibling) - a purely local correction now, since compactClusters already
        // guarantees no cross-cluster overlap at any row.
        for (generation in generations) {
            val row = canonicalOrder.getValue(generation)
            val resolved = resolveOverlaps(row, centers, widths, ::gapBetween)
            for (group in row) centers[group] = resolved.getValue(group)
        }

        return centers
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
                val requiredGap = widths.getValue(left) / 2.0 + gapBetween(left, right) + widths.getValue(right) / 2.0
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
