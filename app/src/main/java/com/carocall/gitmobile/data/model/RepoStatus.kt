package com.carocall.gitmobile.data.model

data class GitChange(val path: String, val type: ChangeType) {
    enum class ChangeType { ADDED, MODIFIED, DELETED, UNTRACKED, RENAMED }
}

data class RepoStatus(
    val branch: String = "unknown",
    val staged: List<GitChange> = emptyList(),
    val unstaged: List<GitChange> = emptyList()
) {
    val hasChanges: Boolean get() = staged.isNotEmpty() || unstaged.isNotEmpty()
    
    // 兼容旧代码的辅助方法
    val untracked: Set<String> get() = unstaged.filter { it.type == GitChange.ChangeType.UNTRACKED }.map { it.path }.toSet()
    val modified: Set<String> get() = unstaged.filter { it.type == GitChange.ChangeType.MODIFIED }.map { it.path }.toSet()
    val added: Set<String> get() = staged.filter { it.type == GitChange.ChangeType.ADDED }.map { it.path }.toSet()
    val removed: Set<String> get() = staged.filter { it.type == GitChange.ChangeType.DELETED }.map { it.path }.toSet()
    
    val allChanges: List<Pair<String, String>> get() = 
        staged.map { it.path to "Staged ${it.type.name}" } + 
        unstaged.map { it.path to it.type.name }
}
