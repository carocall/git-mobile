package com.carocall.gitmobile.data.model

import java.util.UUID

data class GitAccount(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val username: String,
    val token: String
)
