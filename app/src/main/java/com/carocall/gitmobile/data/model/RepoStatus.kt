package com.carocall.gitmobile.data.model

data class RepoStatus(
    val branch: String = "unknown",
    val untracked: Set<String> = emptySet(),
    val modified: Set<String> = emptySet(),
    val added: Set<String> = emptySet(),
    val removed: Set<String> = emptySet()
) {
    val hasChanges: Boolean get() = untracked.isNotEmpty() || modified.isNotEmpty() || added.isNotEmpty() || removed.isNotEmpty()
    val allChanges: List<Pair<String, String>> get() =
        untracked.map { it to "Untracked" } + modified.map { it to "Modified" } +
                added.map { it to "Added" } + removed.map { it to "Removed" }
}
