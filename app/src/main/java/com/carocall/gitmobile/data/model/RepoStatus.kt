package com.carocall.gitmobile.data.model

// --- 数据模型 ---

data class RepoStatus(
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

data class CommitInfo(
    val id: String,
    val author: String,
    val message: String,
    val time: Long,
    val isRemote: Boolean = false // 新增：标识是否为云端当前位置
)