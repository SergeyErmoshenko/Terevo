package me.terevo.layout

// Crossing-minimizing ordering within each generation, considering every parent/child
// group-adjacency at once (not just a single "primary" parent). Standard barycenter + transpose
// heuristic, as used by dagre's order.js: alternate top-down and bottom-up sweeps re-sorting each
// row by the average position of its neighbors in the adjacent row, then locally fix remaining
// crossings with adjacent swaps, keeping the best-scoring ordering seen across iterations.
internal object LayerOrdering {

    private const val ITERATIONS: Int = 4

    // Kept well under 1.0 so a member's fractional offset can never reach as far as an adjacent
    // group's own integer position - it only ever needs to break ties among neighbors that already
    // share the same group.
    private const val NODE_FRACTION_SPAN: Double = 0.5

    fun order(
        groups: List<Group>,
        parentGroupsOf: Map<Group, List<Group>>,
        childGroupsOf: Map<Group, List<Group>>,
        widths: Map<Group, Double>,
        siblingSpacing: Double,
        nodeParentsOf: Map<NodeId, List<NodeId>>,
        nodeChildrenOf: Map<NodeId, List<NodeId>>,
        groupOf: Map<NodeId, Group>,
    ): Pair<Map<Int, List<Group>>, Map<Group, Group>> {
        val generations = groups.map { it.generation }.distinct().sorted()
        val (primaryParent, cluster) = primaryParentOf(groups, parentGroupsOf, childGroupsOf)
        var current = initialOrder(groups, primaryParent, widths, siblingSpacing, generations)
        if (generations.size <= 1) return current to cluster

        // Each row is scored, and its best-seen arrangement kept, independently against only its
        // own immediate neighbors - not folded into one whole-layout total. Reordering one row can
        // only ever change crossings against the row directly above and directly below it, so a
        // genuine, cost-free improvement to one row (e.g. straightening a lone in-law connector so
        // it lines up with its one child instead of crossing over an unrelated sibling) must not be
        // discarded just because the SAME sweep iteration happens to slightly worsen some distant,
        // unrelated row while an entire family tree is laid out as one connected component.
        val best = current.toMutableMap()
        val bestRowCrossings =
            generations.associateWith { rowCrossings(it, generations, current, childGroupsOf) }.toMutableMap()

        repeat(ITERATIONS) { iteration ->
            current = sweep(
                current, generations, parentGroupsOf, childGroupsOf,
                nodeParentsOf, nodeChildrenOf, groupOf, cluster, downward = iteration % 2 == 0,
            )
            for (generation in generations) {
                val crossings = rowCrossings(generation, generations, current, childGroupsOf)
                if (crossings <= bestRowCrossings.getValue(generation)) {
                    bestRowCrossings[generation] = crossings
                    best[generation] = current.getValue(generation)
                }
            }
        }
        return best to cluster
    }

    private fun rowCrossings(
        generation: Int,
        generations: List<Int>,
        order: Map<Int, List<Group>>,
        childGroupsOf: Map<Group, List<Group>>,
    ): Int {
        val index = generations.indexOf(generation)
        var total = 0
        if (index > 0) {
            total += crossingsBetween(order.getValue(generations[index - 1]), order.getValue(generation), childGroupsOf)
        }
        if (index < generations.size - 1) {
            total += crossingsBetween(order.getValue(generation), order.getValue(generations[index + 1]), childGroupsOf)
        }
        return total
    }

    // A group's single "primary" parent defines which sibling cluster it belongs to for the rest
    // of ordering, and clusters are keyed not by that immediate parent but by its *root* - the
    // topmost ancestor reached by following primaryParent pointers to the top of the lineage - so
    // that cousins two-or-more generations below a shared grandparent still land in one contiguous
    // cluster instead of being split by whichever immediate parent group they happen to hang off.
    //
    // When a group has more than one parent-group candidate (a married couple whose own parents are
    // both known), the candidate whose *root* carries the larger overall family wins - not whichever
    // candidate's own direct descendant count happens to be bigger. Comparing only the candidates'
    // own subtrees ties whenever each candidate has exactly one child group in common (e.g. two
    // small in-law lineages that both terminate at the same shared grandchild), which then falls
    // through to an arbitrary alphabetical pick that can hand a whole blood lineage's cluster
    // identity to an unrelated one-child marriage-in family. Comparing root sizes instead correctly
    // favors "the family this couple is actually part of" over "whichever side happens to sort
    // first". Groups are processed in ascending generation order so that by the time a group's
    // candidates are compared, each candidate (always a strictly shallower generation) already has
    // its own root resolved.
    private fun primaryParentOf(
        groups: List<Group>,
        parentGroupsOf: Map<Group, List<Group>>,
        childGroupsOf: Map<Group, List<Group>>,
    ): Pair<Map<Group, Group>, Map<Group, Group>> {
        // Counts DISTINCT reachable descendant groups rather than naively summing sizeOf over
        // every entry in childGroupsOf: a married couple's shared child two-plus generations away
        // gets a separate dummy waypoint chain per spouse (one edge per biological parent, as
        // rendering requires), so childGroupsOf can list two different dummy chains that both
        // terminate at the very same descendant subtree. Summing without dedup would count that
        // subtree twice and could inflate a childless in-law couple's size past a genuinely larger
        // family purely from that duplication.
        val subtreeSize = mutableMapOf<Group, Int>()
        fun sizeOf(group: Group): Int = subtreeSize.getOrPut(group) {
            val visited = mutableSetOf<Group>()
            fun visit(g: Group) {
                if (!visited.add(g)) return
                childGroupsOf.getValue(g).forEach(::visit)
            }
            visit(group)
            visited.size
        }

        val primaryParent = mutableMapOf<Group, Group>()
        val root = mutableMapOf<Group, Group>()
        for (group in groups.sortedBy { it.generation }) {
            for (parent in parentGroupsOf.getValue(group)) {
                val parentRoot = root.getValue(parent)
                val current = primaryParent[group]
                val currentRoot = current?.let { root.getValue(it) }
                val better = current == null ||
                    sizeOf(parentRoot) > sizeOf(currentRoot!!) ||
                    (sizeOf(parentRoot) == sizeOf(currentRoot) && parentRoot.order < currentRoot.order)
                if (better) primaryParent[group] = parent
            }
            root[group] = primaryParent[group]?.let { root.getValue(it) } ?: group
        }
        return primaryParent to root
    }

    // Seeds each generation with the same width-balanced sibling arrangement the old tree-based
    // placement used (widest branch centered under its parent, narrower ones alternating either
    // side by remaining width) rather than plain alphabetical order, so ties left unresolved by the
    // barycenter sweeps below (e.g. several children of one parent all sharing that parent's
    // position) still land in a sensible spot instead of falling back to alphabetical order.
    private fun initialOrder(
        groups: List<Group>,
        primaryParent: Map<Group, Group>,
        widths: Map<Group, Double>,
        siblingSpacing: Double,
        generations: List<Int>,
    ): Map<Int, List<Group>> {
        val childrenOf = groups.groupBy { primaryParent[it] }
        val subtreeWidth = mutableMapOf<Group, Double>()
        fun widthOf(group: Group): Double = subtreeWidth.getOrPut(group) {
            val children = childrenOf[group].orEmpty()
            if (children.isEmpty()) {
                widths.getValue(group)
            } else {
                children.sumOf(::widthOf) + siblingSpacing * (children.size - 1)
            }
        }

        val order = mutableMapOf<Int, List<Group>>()
        var previousRow = emptyList<Group>()
        for (generation in generations) {
            val row = groups.filter { it.generation == generation }
            order[generation] = if (previousRow.isEmpty()) {
                balancedOrder(row, ::widthOf) { it.order }
            } else {
                val clustered = previousRow.flatMap { parent ->
                    balancedOrder(row.filter { primaryParent[it] == parent }, ::widthOf) { it.order }
                }
                val orphans = balancedOrder(row.filter { primaryParent[it] !in previousRow }, ::widthOf) { it.order }
                clustered + orphans
            }
            previousRow = order.getValue(generation)
        }
        return order
    }

    private fun sweep(
        order: Map<Int, List<Group>>,
        generations: List<Int>,
        parentGroupsOf: Map<Group, List<Group>>,
        childGroupsOf: Map<Group, List<Group>>,
        nodeParentsOf: Map<NodeId, List<NodeId>>,
        nodeChildrenOf: Map<NodeId, List<NodeId>>,
        groupOf: Map<NodeId, Group>,
        cluster: Map<Group, Group>,
        downward: Boolean,
    ): Map<Int, List<Group>> {
        val visitOrder = if (downward) generations else generations.asReversed()
        val primaryNeighbors = if (downward) parentGroupsOf else childGroupsOf
        val primaryNodeNeighbors = if (downward) nodeParentsOf else nodeChildrenOf
        val result = order.toMutableMap()
        for (index in 1 until visitOrder.size) {
            val generation = visitOrder[index]
            val primaryGeneration = visitOrder[index - 1]
            val primaryPosition = positionIndex(result.getValue(primaryGeneration))
            val row = result.getValue(generation)
            val currentPosition = positionIndex(row)

            // A group merges several people (e.g. spouses) into one box, so a plain group-to-group
            // barycenter can't tell apart a parent group whose edge actually lands on the LEFT
            // member of that box from one landing on the RIGHT member - both see the exact same
            // group position and tie. That tie silently freezes whichever order the two parent
            // groups started in (e.g. from initial seeding), which can leave a grandparent on the
            // wrong side of the row relative to which of their own children they actually connect
            // to. Resolving each neighbor to a fractional position - the group's row index plus a
            // sub-unit offset for where that specific node sits within the group's own internal
            // member order - breaks the tie in the direction that actually matches the real edge.
            fun nodeFraction(node: NodeId): Double? {
                val group = groupOf[node] ?: return null
                val position = primaryPosition[group] ?: return null
                val members = group.nodes
                val memberIndex = members.indexOf(node)
                if (memberIndex < 0 || members.size <= 1) return position.toDouble()
                return position + (memberIndex.toDouble() / (members.size - 1)) * NODE_FRACTION_SPAN
            }

            fun barycenter(group: Group): Double {
                val positions = group.nodes.flatMap { node -> primaryNodeNeighbors[node].orEmpty() }
                    .mapNotNull(::nodeFraction)
                return if (positions.isEmpty()) currentPosition.getValue(group).toDouble() else positions.average()
            }

            // Groups sharing the same primary parent form one sibling cluster that must stay
            // contiguous: only whole clusters are reordered relative to each other (by the
            // cluster's own average barycenter), and members are only reordered within their own
            // cluster. Without this, two otherwise-unrelated family branches that happen to land
            // on the same generation can get sliced apart and braided together whenever that
            // marginally reduces the raw crossing count - technically fewer crossings, but visually
            // unreadable since it destroys the contiguity of each family's own sibling group.
            val clusters = row.groupBy { cluster[it] ?: it }
            val clusterOrder = clusters.keys.sortedWith(
                compareBy { key -> clusters.getValue(key).map(::barycenter).average() },
            )
            val reordered = clusterOrder.flatMap { key -> clusters.getValue(key).sortedWith(compareBy(::barycenter)) }

            val genIndex = generations.indexOf(generation)
            val above = generations.getOrNull(genIndex - 1)?.let { result.getValue(it) to parentGroupsOf }
            val below = generations.getOrNull(genIndex + 1)?.let { result.getValue(it) to childGroupsOf }
            result[generation] = transpose(reordered, above, below, cluster)
        }
        return result
    }

    // Locally fixes remaining crossings by swapping adjacent groups within a row whenever doing so
    // strictly reduces crossings against the neighboring rows above and below - restricted to pairs
    // within the same sibling cluster (see sweep above), so this local cleanup pass can't undo the
    // cluster contiguity the sweep just established.
    private fun transpose(
        row: List<Group>,
        above: Pair<List<Group>, Map<Group, List<Group>>>?,
        below: Pair<List<Group>, Map<Group, List<Group>>>?,
        cluster: Map<Group, Group>,
    ): List<Group> {
        val current = row.toMutableList()
        val abovePosition = above?.let { positionIndex(it.first) }
        val belowPosition = below?.let { positionIndex(it.first) }
        var improved = true
        while (improved) {
            improved = false
            for (i in 0 until current.size - 1) {
                val left = current[i]
                val right = current[i + 1]
                if ((cluster[left] ?: left) != (cluster[right] ?: right)) continue
                var delta = 0
                if (above != null) delta += pairCrossingDelta(left, right, abovePosition!!, above.second)
                if (below != null) delta += pairCrossingDelta(left, right, belowPosition!!, below.second)
                if (delta > 0) {
                    current[i] = right
                    current[i + 1] = left
                    improved = true
                }
            }
        }
        return current
    }

    private fun pairCrossingDelta(
        left: Group,
        right: Group,
        neighborPosition: Map<Group, Int>,
        neighborsOf: Map<Group, List<Group>>,
    ): Int {
        val leftPositions = neighborsOf.getValue(left).mapNotNull(neighborPosition::get)
        val rightPositions = neighborsOf.getValue(right).mapNotNull(neighborPosition::get)
        var same = 0
        var swapped = 0
        for (a in leftPositions) {
            for (b in rightPositions) {
                if (a > b) same++ else if (a < b) swapped++
            }
        }
        return same - swapped
    }

    private fun positionIndex(row: List<Group>): Map<Group, Int> =
        row.withIndex().associate { (index, group) -> group to index }

    private fun crossingsBetween(
        upper: List<Group>,
        lower: List<Group>,
        childGroupsOf: Map<Group, List<Group>>,
    ): Int {
        val lowerPosition = positionIndex(lower)
        val positions = upper.flatMap { group -> childGroupsOf.getValue(group).mapNotNull(lowerPosition::get) }
        return countInversions(positions)
    }

    private fun countInversions(values: List<Int>): Int {
        if (values.size < 2) return 0
        val array = values.toIntArray()
        val buffer = IntArray(array.size)
        return mergeCount(array, buffer, 0, array.size - 1)
    }

    private fun mergeCount(array: IntArray, buffer: IntArray, low: Int, high: Int): Int {
        if (low >= high) return 0
        val mid = (low + high) / 2
        var count = mergeCount(array, buffer, low, mid) + mergeCount(array, buffer, mid + 1, high)
        var i = low
        var j = mid + 1
        var k = low
        while (i <= mid && j <= high) {
            if (array[i] <= array[j]) {
                buffer[k++] = array[i++]
            } else {
                buffer[k++] = array[j++]
                count += mid - i + 1
            }
        }
        while (i <= mid) buffer[k++] = array[i++]
        while (j <= high) buffer[k++] = array[j++]
        for (x in low..high) array[x] = buffer[x]
        return count
    }
}
