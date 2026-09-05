package me.terevo.ui

enum class MainTab {
    TREE,
    PERSONS,
    EVENTS,
    DOCUMENTS,
}

val MainTab.label: String
    get() = when (this) {
        MainTab.TREE -> Strings.TAB_TREE
        MainTab.PERSONS -> Strings.TAB_PERSONS
        MainTab.EVENTS -> Strings.TAB_EVENTS
        MainTab.DOCUMENTS -> Strings.TAB_DOCUMENTS
    }
