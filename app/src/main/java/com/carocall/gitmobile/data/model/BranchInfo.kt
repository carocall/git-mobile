package com.carocall.gitmobile.data.model

data class BranchInfo(
    val name: String,
    val isCurrent: Boolean,
    val isRemote: Boolean,
    val shortHash: String = ""
)
