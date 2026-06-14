package com.carocall.gitmobile.data.model

data class CommitInfo(
    val id: String,
    val author: String,
    val message: String,
    val time: Long,
    val isRemote: Boolean = false
)
