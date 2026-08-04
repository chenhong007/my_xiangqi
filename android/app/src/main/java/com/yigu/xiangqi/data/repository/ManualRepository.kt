package com.yigu.xiangqi.data.repository

import com.yigu.xiangqi.data.local.dao.ManualDao
import com.yigu.xiangqi.data.local.dao.ProgressDao
import com.yigu.xiangqi.data.local.entity.ManualEntity
import com.yigu.xiangqi.domain.model.Manual
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManualRepository @Inject constructor(
    private val manualDao: ManualDao,
) {
    fun getAllManuals(): Flow<List<Manual>> =
        manualDao.getAll().map { list ->
            list.map { it.toDomain() }
        }

    suspend fun getManual(id: String): Manual? =
        manualDao.getById(id)?.toDomain()
        
    suspend fun recordAccess(id: String) {
        manualDao.recordAccess(id)
    }

    suspend fun updatePinned(id: String, isPinned: Boolean) {
        manualDao.updatePinned(id, isPinned)
    }
}

private fun ManualEntity.toDomain() = Manual(
    id = id,
    name = name,
    type = type,
    totalGames = totalGames,
    addTime = addTime,
    lastAccessTime = lastAccessTime,
    viewCount = viewCount,
    isPinned = isPinned,
)
