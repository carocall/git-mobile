package com.carocall.gitmobile.data.model

data class RemoteProfile(
    val name: String,
    val url: String,
    val user: String = "",
    val token: String = ""
)
