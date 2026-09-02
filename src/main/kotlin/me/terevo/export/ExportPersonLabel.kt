package me.terevo.export

enum class ExportGender {
    MALE,
    FEMALE,
    UNKNOWN,
}

data class ExportPersonLabel(
    val name: String,
    val lifeYears: String,
    val gender: ExportGender,
)
