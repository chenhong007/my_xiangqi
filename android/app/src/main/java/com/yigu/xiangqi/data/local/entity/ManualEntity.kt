package com.yigu.xiangqi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manuals")
data class ManualEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val totalGames: Int,
    val addTime: Long = 0L,
    val lastAccessTime: Long = 0L,
    val viewCount: Int = 0,
    val isPinned: Boolean = false,
)
