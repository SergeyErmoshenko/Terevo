package me.terevo.kinship

import me.terevo.domain.model.Gender

object KinshipTerms {

    private const val GREAT_PREFIX: String = "пра"

    // How many "пра" are spelled out before switching to the counted form.
    private const val MAX_SPELLED_OUT_GREAT_PREFIXES: Int = 2

    fun spouseTerm(gender: Gender): String = when (gender) {
        Gender.MALE -> "Муж"
        Gender.FEMALE -> "Жена"
        Gender.UNKNOWN -> "Супруг"
    }

    fun bloodTerm(stepsFromEgo: Int, stepsFromTarget: Int, gender: Gender): String = when {
        stepsFromTarget == 0 -> ancestorTerm(stepsFromEgo, gender)
        stepsFromEgo == 0 -> descendantTerm(stepsFromTarget, gender)
        else -> collateralTerm(stepsFromEgo, stepsFromTarget, gender)
    }

    fun inLawViaOwnSpouse(egoGender: Gender, stepsFromSpouse: Int, stepsFromTarget: Int, targetGender: Gender): String? =
        when {
            stepsFromSpouse == 1 && stepsFromTarget == 0 -> parentInLawTerm(egoGender, targetGender)
            stepsFromSpouse == 1 && stepsFromTarget == 1 -> siblingInLawTerm(egoGender, targetGender)
            else -> null
        }

    fun childOrSiblingSpouseTerm(targetGender: Gender): String = when (targetGender) {
        Gender.MALE -> "Зять"
        Gender.FEMALE -> "Невестка"
        Gender.UNKNOWN -> "Родственник по браку"
    }

    fun fallbackTerm(totalSteps: Int): String = "родственник в $totalSteps-м колене"

    private fun ancestorTerm(distance: Int, gender: Gender): String = when (distance) {
        1 -> when (gender) {
            Gender.MALE -> "Папа"
            Gender.FEMALE -> "Мама"
            Gender.UNKNOWN -> "Родитель"
        }
        2 -> grandparentTerm(gender)
        else -> withGreatPrefix(distance - 2, grandparentTerm(gender))
    }

    private fun descendantTerm(distance: Int, gender: Gender): String = when (distance) {
        1 -> when (gender) {
            Gender.MALE -> "Сын"
            Gender.FEMALE -> "Дочь"
            Gender.UNKNOWN -> "Ребёнок"
        }
        2 -> grandchildTerm(gender)
        else -> withGreatPrefix(distance - 2, grandchildTerm(gender))
    }

    // Beyond two repetitions the spelled-out prefix stops being readable - "прапрапрапрадедушка"
    // can't be counted at a glance - so the count is written out instead: "пра(4)дедушка". Two or
    // fewer stay spelled out, since "прабабушка" and "прапрабабушка" are the familiar forms.
    private fun withGreatPrefix(repetitions: Int, base: String): String {
        val prefix = if (repetitions <= MAX_SPELLED_OUT_GREAT_PREFIXES) {
            GREAT_PREFIX.repeat(repetitions)
        } else {
            "$GREAT_PREFIX($repetitions)"
        }
        return (prefix + base.lowercase()).replaceFirstChar(Char::uppercase)
    }

    private fun collateralTerm(stepsFromEgo: Int, stepsFromTarget: Int, gender: Gender): String {
        val degree = minOf(stepsFromEgo, stepsFromTarget) - 1
        val removal = kotlin.math.abs(stepsFromEgo - stepsFromTarget)
        val egoCloser = stepsFromEgo < stepsFromTarget
        return when {
            degree == 0 && removal == 0 -> siblingTerm(gender)
            degree == 0 && removal == 1 && egoCloser -> niblingTerm(gender)
            degree == 0 && removal == 1 -> uncleTerm(gender)
            degree == 0 && removal == 2 && egoCloser -> grandNiblingTerm(gender)
            degree == 0 && removal == 2 -> grandUncleTerm(gender)
            degree == 1 && removal == 0 -> cousinTerm(gender, "Двоюродный", "Двоюродная")
            degree == 1 && removal == 1 && egoCloser -> cousinNiblingTerm(gender)
            degree == 1 && removal == 1 -> cousinUncleTerm(gender)
            degree == 2 && removal == 0 -> cousinTerm(gender, "Троюродный", "Троюродная")
            else -> fallbackTerm(stepsFromEgo + stepsFromTarget)
        }
    }

    private fun grandparentTerm(gender: Gender): String = when (gender) {
        Gender.MALE -> "Дедушка"
        Gender.FEMALE -> "Бабушка"
        Gender.UNKNOWN -> "Прародитель"
    }

    private fun grandchildTerm(gender: Gender): String = when (gender) {
        Gender.MALE -> "Внук"
        Gender.FEMALE -> "Внучка"
        Gender.UNKNOWN -> "Внук или внучка"
    }

    private fun siblingTerm(gender: Gender): String = when (gender) {
        Gender.MALE -> "Брат"
        Gender.FEMALE -> "Сестра"
        Gender.UNKNOWN -> "Брат или сестра"
    }

    private fun uncleTerm(gender: Gender): String = when (gender) {
        Gender.MALE -> "Дядя"
        Gender.FEMALE -> "Тётя"
        Gender.UNKNOWN -> "Дядя или тётя"
    }

    private fun niblingTerm(gender: Gender): String = when (gender) {
        Gender.MALE -> "Племянник"
        Gender.FEMALE -> "Племянница"
        Gender.UNKNOWN -> "Племянник или племянница"
    }

    private fun grandUncleTerm(gender: Gender): String = when (gender) {
        Gender.MALE -> "Двоюродный дедушка"
        Gender.FEMALE -> "Двоюродная бабушка"
        Gender.UNKNOWN -> "Двоюродный дедушка или бабушка"
    }

    private fun grandNiblingTerm(gender: Gender): String = when (gender) {
        Gender.MALE -> "Внучатый племянник"
        Gender.FEMALE -> "Внучатая племянница"
        Gender.UNKNOWN -> "Внучатый племянник или племянница"
    }

    private fun cousinTerm(gender: Gender, malePrefix: String, femalePrefix: String): String = when (gender) {
        Gender.MALE -> "$malePrefix брат"
        Gender.FEMALE -> "$femalePrefix сестра"
        Gender.UNKNOWN -> "$malePrefix родственник"
    }

    private fun cousinUncleTerm(gender: Gender): String = when (gender) {
        Gender.MALE -> "Двоюродный дядя"
        Gender.FEMALE -> "Двоюродная тётя"
        Gender.UNKNOWN -> "Двоюродный дядя или тётя"
    }

    private fun cousinNiblingTerm(gender: Gender): String = when (gender) {
        Gender.MALE -> "Двоюродный племянник"
        Gender.FEMALE -> "Двоюродная племянница"
        Gender.UNKNOWN -> "Двоюродный племянник или племянница"
    }

    private fun parentInLawTerm(egoGender: Gender, targetGender: Gender): String = when (egoGender) {
        Gender.MALE -> when (targetGender) {
            Gender.MALE -> "Тесть"
            Gender.FEMALE -> "Тёща"
            Gender.UNKNOWN -> "Родитель супруги"
        }
        Gender.FEMALE -> when (targetGender) {
            Gender.MALE -> "Свёкор"
            Gender.FEMALE -> "Свекровь"
            Gender.UNKNOWN -> "Родитель супруга"
        }
        Gender.UNKNOWN -> "Родитель супруга или супруги"
    }

    private fun siblingInLawTerm(egoGender: Gender, targetGender: Gender): String = when (egoGender) {
        Gender.MALE -> when (targetGender) {
            Gender.MALE -> "Шурин"
            Gender.FEMALE -> "Свояченица"
            Gender.UNKNOWN -> "Родственник супруги"
        }
        Gender.FEMALE -> when (targetGender) {
            Gender.MALE -> "Деверь"
            Gender.FEMALE -> "Золовка"
            Gender.UNKNOWN -> "Родственник супруга"
        }
        Gender.UNKNOWN -> "Родственник супруга или супруги"
    }
}
