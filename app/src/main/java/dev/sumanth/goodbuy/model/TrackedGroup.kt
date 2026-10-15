package dev.sumanth.goodbuy.model

import java.util.UUID

data class TrackedGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val productIds: List<String> = emptyList()
)
