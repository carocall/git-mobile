package com.carocall.gitmobile.data.model

data class LocalRepo(
    val path: String,
    val name: String,
    val alias: String = "",
    val lastOpened: Long = System.currentTimeMillis(),
    val isStarred: Boolean = false
) {
    val displayName: String get() = alias.ifBlank { name }
}
