package com.yigu.xiangqi.data.local.dao

import androidx.room.Dao
import androidx.room.Upsert
import androidx.room.Query
import com.yigu.xiangqi.data.local.entity.ManualEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualDao {

    @Query("SELECT * FROM manuals ORDER BY name")
    fun getAll(): Flow<List<ManualEntity>>

    @Query("SELECT * FROM manuals WHERE id = :id")
    suspend fun getById(id: String): ManualEntity?

    @Query("SELECT COUNT(*) FROM manuals")
    suspend fun count(): Int

    @Query("""
        SELECT m.id, COUNT(g.id) as gameCount 
        FROM manuals m LEFT JOIN games g ON g.manualId = m.id 
        GROUP BY m.id
    """)
    suspend fun getAllWithGameCount(): List<ManualGameCount>

    @androidx.room.Upsert
    suspend fun insertAll(manuals: List<ManualEntity>)

    @Query("UPDATE manuals SET viewCount = viewCount + 1, lastAccessTime = :time WHERE id = :id")
    suspend fun recordAccess(id: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE manuals SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinned(id: String, isPinned: Boolean)
}

data class ManualGameCount(
    val id: String,
    val gameCount: Int,
)
