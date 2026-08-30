package me.terevo.domain.port

import me.terevo.domain.Outcome

data class UserSettings(
    val recentProjects: List<ProjectLocation> = emptyList(),
) {
    fun withRecent(location: ProjectLocation, limit: Int = RECENT_LIMIT): UserSettings =
        copy(recentProjects = (listOf(location) + recentProjects.filter { it != location }).take(limit))

    fun withoutRecent(location: ProjectLocation): UserSettings =
        copy(recentProjects = recentProjects.filter { it != location })

    companion object {
        const val RECENT_LIMIT: Int = 10

        val EMPTY: UserSettings = UserSettings()
    }
}

interface SettingsStore {
    fun load(): Outcome<UserSettings>

    fun save(settings: UserSettings): Outcome<Unit>
}
