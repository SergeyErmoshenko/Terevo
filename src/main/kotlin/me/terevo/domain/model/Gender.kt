package me.terevo.domain.model

enum class Gender {
    MALE,
    FEMALE,
    UNKNOWN,
}

fun Gender.opposite(): Gender? = when (this) {
    Gender.MALE -> Gender.FEMALE
    Gender.FEMALE -> Gender.MALE
    Gender.UNKNOWN -> null
}
