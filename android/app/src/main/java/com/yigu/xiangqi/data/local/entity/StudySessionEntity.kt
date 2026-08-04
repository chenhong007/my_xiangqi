package com.yigu.xiangqi.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "study_sessions",
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
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val reachedStep: Int = 0,
    val totalSteps: Int = 0,
    val completed: Boolean = false,
    val mode: String = "REVIEW",
    val guessCorrect: Int = 0,
    val guessTotal: Int = 0,
)
