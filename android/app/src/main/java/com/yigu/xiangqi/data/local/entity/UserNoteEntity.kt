package com.yigu.xiangqi.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_notes",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class UserNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val gameId: String,
    val stepIndex: Int,
    val branchPath: String? = null,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
)
