package com.yigu.xiangqi.data.importer

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.yigu.xiangqi.data.local.dao.GameDao
import com.yigu.xiangqi.data.local.dao.ManualDao
import com.yigu.xiangqi.data.local.entity.GameEntity
import com.yigu.xiangqi.data.local.entity.ManualEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 启动时从 assets 导入古谱 JSON 到 Room 数据库。
 * 支持增量导入：检测缺失或数据不完整的古谱并补入。
 */
@Singleton
class DataImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manualDao: ManualDao,
    private val gameDao: GameDao,
    private val gson: Gson,
) {
    suspend fun importIfNeeded() {
        withContext(Dispatchers.IO) {
            val files = context.assets.list("manuals") ?: return@withContext
            val existingManuals = manualDao.getAllWithGameCount()
                .associate { it.id to it.gameCount }
            val prefs = context.getSharedPreferences("data_importer_prefs", Context.MODE_PRIVATE)

            for (fileName in files) {
                if (!fileName.endsWith(".json")) continue
                val manualName = fileName.removeSuffix(".json")
                val assetPath = "manuals/$fileName"
                val existingGameCount = existingManuals[manualName]

                // 读取文件内容以进行比对
                val json = context.assets.open(assetPath).bufferedReader().use { it.readText() }
                val currentHash = "${json.length}_${json.hashCode()}"
                val savedHash = prefs.getString("hash_v2_$manualName", null)

                // 当文件未变动，且已有数据时，跳过导入
                if (savedHash == currentHash && existingGameCount != null && existingGameCount > 0) {
                    // 修复之前可能导入的 totalGames 为 0 或不正确的问题
                    val manual = manualDao.getById(manualName)
                    if (manual != null && manual.totalGames != existingGameCount) {
                        manualDao.insertAll(listOf(manual.copy(totalGames = existingGameCount)))
                        Log.i(TAG, "修复古谱局数统计: $manualName -> $existingGameCount")
                    }
                    continue
                }

                try {
                    importFile(json)
                    prefs.edit().putString("hash_v2_$manualName", currentHash).apply()
                    Log.i(TAG, "导入/更新成功: $manualName")
                } catch (e: Exception) {
                    Log.e(TAG, "导入/更新失败: $manualName", e)
                }
            }
        }
    }

    private suspend fun importFile(json: String) {
        val raw = gson.fromJson<RawManual>(json, RawManual::class.java)

        val manualId = raw.manual
        val actualGameCount = raw.games.size
        
        val existingManual = manualDao.getById(manualId)
        
        manualDao.insertAll(
            listOf(
                ManualEntity(
                    id = manualId,
                    name = raw.manual,
                    type = raw.type,
                    totalGames = actualGameCount,
                    addTime = existingManual?.addTime ?: System.currentTimeMillis(),
                    lastAccessTime = existingManual?.lastAccessTime ?: 0L,
                    viewCount = existingManual?.viewCount ?: 0,
                    isPinned = existingManual?.isPinned ?: false
                )
            )
        )

        val entities = raw.games.mapIndexed { index, game ->
            GameEntity(
                id = "$manualId:${game.id}",
                manualId = manualId,
                title = game.title ?: "",
                round = game.round ?: "",
                result = game.result ?: "",
                redPlayer = game.red_player ?: "",
                blackPlayer = game.black_player ?: "",
                initBoardJson = gson.toJson(game.init_board),
                movesJson = gson.toJson(game.moves),
                variationsJson = gson.toJson(game.variations ?: emptyList<Any>()),
                commentsJson = gson.toJson(game.comments ?: emptyMap<String, String>()),
                hasVariations = !game.variations.isNullOrEmpty(),
                hasComments = !game.comments.isNullOrEmpty(),
                moveCount = game.moves?.size ?: 0,
                sortOrder = index,
            )
        }
        gameDao.insertAll(entities)
    }

    companion object {
        private const val TAG = "DataImporter"
    }
}

/** assets JSON 反序列化用的临时结构 */
private data class RawManual(
    val manual: String,
    val type: String,
    val total: Int,
    val games: List<RawGame>,
)

private data class RawGame(
    val id: String,
    val title: String?,
    val round: String?,
    val result: String?,
    val red_player: String?,
    val black_player: String?,
    val init_board: List<Map<String, Any>>?,
    val moves: List<Map<String, Any>>?,
    val variations: List<Map<String, Any>>?,
    val comments: Map<String, String>?,
)
