package com.carocall.gitmobile.data.model

data class RecentFile(
    val path: String,
    val name: String,
    val repoName: String,
    val lastAccessed: Long = System.currentTimeMillis()
)
