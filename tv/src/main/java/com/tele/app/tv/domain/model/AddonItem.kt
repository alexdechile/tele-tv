package com.tele.app.tv.domain.model

data class AddonItem(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val infoHash: String = "",
    val fileIndex: Int = 0
)
