package com.yigu.xiangqi.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_progress",
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
data class UserProgressEntity(
    @PrimaryKey val gameId: String,
    val status: String = "NOT_STARTED",
    val currentStep: Int = 0,
    val currentBranch: String? = null,
    val guessCorrect: Int = 0,
    val guessTotal: Int = 0,
    val lastStudiedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val viewCount: Int = 0,
    val completionCount: Int = 0,
    val firstStudiedAt: Long? = null,
)
