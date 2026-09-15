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

    // A row that's force-separated by resolveOverlaps (two unrelated branches landing on the same
    // generation, one wider than the other below it) only pushes the groups actually *on* that
    // row - it has no way to also carry that shove down into a pushed group's own descendants, who
    // never see it and settle to their own equilibrium as if the push never happened. Left as a
    // single one-shot pass, that reads as a lineage bending one way, jogging sideways at the forced
    // row, then continuing to bend back toward its own natural line - a visible zigzag confirmed
    // against a real family tree (a thin single-child lineage forced rightward past a sibling
    // branch's wide subtree, whose own child then settled back to the left of it). Repeating the
    // full relax-then-declamp cycle lets a later pass's relax phase pull each pushed group's
    // neighbors toward its now-separated position, carrying the correction outward each round -
    // this is the classical Sugiyama priority-method refinement (repeated sweeps of averaging plus
    // per-row conflict resolution), not the same failure mode as declamping *inside* every single
    // averaging pass: here a full damped-to-convergence relax happens between declamps, so a
    // shared marriage group several generations away only receives one bounded correction per
    // round instead of having every single micro-step's clamp fed straight back into the pull that
    // produced it. Bounded well below MAX_ITERATIONS since each round only needs to propagate one
    // row's worth of correction outward, not reach a fresh fixed point from scratch.
    private const val MAX_DECLAMP_ROUNDS: Int = 8

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
                    val neighborXs = group.nodes.flatMap { node ->
                        (nodeParentsOf[node].orEmpty() + nodeChildrenOf[node].orEmpty()).mapNotNull { other ->
                            val otherGroup = groupOf[other] ?: return@mapNotNull null
                            centerOf(other, otherGroup, snapshot, widths, metrics, spouseSpacing)
                        }
                    }
                    if (neighborXs.isNotEmpty()) {
                        val next = snapshot.getValue(group) + damping * (neighborXs.average() - snapshot.getValue(group))
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

        // Turns the settled (possibly still overlapping) positions into a legal, order-preserving,
        // non-overlapping layout for every row at once - order is exactly what LayerOrdering
        // decided, so this only ever spaces groups apart, never reorders them. Returns the largest
        // single-group move this declamp made, so the caller can tell when further rounds would be
        // pointless.
        fun declampAll(): Double {
            var maxMove = 0.0
            for (generation in generations) {
                val row = order.getValue(generation)
                val resolved = resolveOverlaps(row, centers, widths, ::gapBetween)
                for (group in row) {
                    maxMove = maxOf(maxMove, abs(resolved.getValue(group) - centers.getValue(group)))
                    centers[group] = resolved.getValue(group)
                }
            }
            return maxMove
        }

        var round = 0
        while (true) {
            relaxToConvergence()
            round++
            val moved = declampAll()
            if (round >= MAX_DECLAMP_ROUNDS || moved < CONVERGENCE_THRESHOLD) break
        }

        return centers
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
