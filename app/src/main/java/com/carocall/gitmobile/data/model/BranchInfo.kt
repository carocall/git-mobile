package com.carocall.gitmobile.data.model

enum class BranchType {
    LOCAL, REMOTE, TAG
}

data class BranchInfo(
    val fullRefName: String,
    val displayName: String,
    val type: BranchType,
    val isCurrent: Boolean,
    val shortHash: String = "",
    val isTracked: Boolean = false // 是否已被本地追踪
)
