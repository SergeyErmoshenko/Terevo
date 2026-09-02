package me.terevo.kinship

import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Gender
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.MarriageStatus
import me.terevo.domain.model.ParentKind
import me.terevo.domain.model.PersonId

sealed interface KinshipResult {
    data object SamePerson : KinshipResult
    data class Blood(val term: String, val stepsFromA: Int, val stepsFromB: Int, val viaNonBiological: Boolean) : KinshipResult
    data class InLaw(val term: String, val connector: PersonId) : KinshipResult
    data object Unrelated : KinshipResult
}

object KinshipCalculator {

    fun resolve(tree: FamilyTree, personA: PersonId, personB: PersonId): KinshipResult {
        if (personA == personB) return KinshipResult.SamePerson
        val ego = tree.person(personA) ?: return KinshipResult.Unrelated
        val target = tree.person(personB) ?: return KinshipResult.Unrelated

        if (personB in tree.spousesOf(personA)) {
            return KinshipResult.InLaw(KinshipTerms.spouseTerm(target.gender), personB)
        }

        nearestCommonAncestor(tree, personA, personB)?.let { ancestor ->
            val term = KinshipTerms.bloodTerm(ancestor.stepsFromA, ancestor.stepsFromB, target.gender)
            val wrapped = wrapNonBiological(term, ancestor.nonBiologicalKind, target.gender)
            return KinshipResult.Blood(
                wrapped,
                ancestor.stepsFromA,
                ancestor.stepsFromB,
                ancestor.nonBiologicalKind != ParentKind.BIOLOGICAL,
            )
        }

        for (spouse in tree.marriedSpousesOf(personA)) {
            val ancestor = nearestCommonAncestor(tree, spouse, personB) ?: continue
            val term = KinshipTerms.inLawViaOwnSpouse(ego.gender, ancestor.stepsFromA, ancestor.stepsFromB, target.gender)
            if (term != null) return KinshipResult.InLaw(term, spouse)
        }

        val closeRelatives = tree.childrenOf(personA) + tree.parentsOf(personA) + siblingsOf(tree, personA)
        for (relative in closeRelatives) {
            if (personB in tree.spousesOf(relative)) {
                return KinshipResult.InLaw(KinshipTerms.childOrSiblingSpouseTerm(target.gender), relative)
            }
        }

        return KinshipResult.Unrelated
    }

    private fun siblingsOf(tree: FamilyTree, person: PersonId): List<PersonId> =
        tree.parentsOf(person).flatMap(tree::childrenOf).distinct().filter { it != person }

    private data class CommonAncestorMatch(
        val id: PersonId,
        val stepsFromA: Int,
        val stepsFromB: Int,
        val nonBiologicalKind: ParentKind,
    )

    private data class AncestorFrontierNode(val distance: Int, val nonBiologicalKind: ParentKind)

    private fun nearestCommonAncestor(tree: FamilyTree, a: PersonId, b: PersonId): CommonAncestorMatch? {
        val fromA = ancestorFrontiers(tree, a)
        val fromB = ancestorFrontiers(tree, b)
        val common = fromA.keys.intersect(fromB.keys)
        val best = common.minWithOrNull(
            compareBy({ fromA.getValue(it).distance + fromB.getValue(it).distance }, { it.value }),
        ) ?: return null
        val nodeA = fromA.getValue(best)
        val nodeB = fromB.getValue(best)
        val kind = if (nodeA.nonBiologicalKind != ParentKind.BIOLOGICAL) nodeA.nonBiologicalKind else nodeB.nonBiologicalKind
        return CommonAncestorMatch(best, nodeA.distance, nodeB.distance, kind)
    }

    private fun ancestorFrontiers(tree: FamilyTree, start: PersonId): Map<PersonId, AncestorFrontierNode> {
        val visited = mutableMapOf(start to AncestorFrontierNode(0, ParentKind.BIOLOGICAL))
        var frontier = listOf(start)
        var distance = 0
        while (frontier.isNotEmpty()) {
            distance++
            val next = mutableListOf<PersonId>()
            for (current in frontier) {
                val currentKind = visited.getValue(current).nonBiologicalKind
                for (link in tree.parentLinksOf(current)) {
                    if (link.parent in visited) continue
                    val kind = if (currentKind != ParentKind.BIOLOGICAL) currentKind else link.kind
                    visited[link.parent] = AncestorFrontierNode(distance, kind)
                    next.add(link.parent)
                }
            }
            frontier = next
        }
        return visited
    }

    private fun FamilyTree.marriedSpousesOf(person: PersonId): List<PersonId> =
        relationsOf(person).filterIsInstance<Marriage>()
            .filter { it.status == MarriageStatus.MARRIED || it.status == MarriageStatus.PARTNERS }
            .mapNotNull { it.spouseOf(person) }

    private fun wrapNonBiological(term: String, kind: ParentKind, gender: Gender): String = when (kind) {
        ParentKind.STEP -> "${if (gender == Gender.FEMALE) "сводная" else "сводный"} ${term.replaceFirstChar(Char::lowercase)}"
        ParentKind.ADOPTIVE, ParentKind.FOSTER ->
            "${if (gender == Gender.FEMALE) "приёмная" else "приёмный"} ${term.replaceFirstChar(Char::lowercase)}"
        ParentKind.BIOLOGICAL -> term
    }
}
