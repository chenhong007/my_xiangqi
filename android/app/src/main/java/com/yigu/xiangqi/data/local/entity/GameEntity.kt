package com.yigu.xiangqi.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "games",
    foreignKeys = [
        ForeignKey(
            entity = ManualEntity::class,
            parentColumns = ["id"],
            childColumns = ["manualId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("manualId")],
)
data class GameEntity(
    @PrimaryKey val id: String,
    val manualId: String,
    val title: String,
    val round: String,
    val result: String,
    val redPlayer: String,
    val blackPlayer: String,
    val initBoardJson: String,
    val movesJson: String,
    val variationsJson: String,
    val commentsJson: String,
    val hasVariations: Boolean,
    val hasComments: Boolean,
    val moveCount: Int,
    val sortOrder: Int = 0,
)
