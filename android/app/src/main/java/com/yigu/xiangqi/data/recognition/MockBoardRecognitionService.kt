package com.yigu.xiangqi.data.recognition

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yigu.xiangqi.data.local.dao.GameDao
import com.yigu.xiangqi.domain.model.PiecePosition
import com.yigu.xiangqi.domain.recognition.BoardRecognitionService
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockBoardRecognitionService @Inject constructor(
    private val gameDao: GameDao,
    private val gson: Gson
) : BoardRecognitionService {

    override suspend fun recognizeBoard(imageUri: Uri): List<PiecePosition> {
        // 模拟网络延迟或AI计算时间
        delay(2000)
        
        // 随机获取一个古谱的初始棋盘作为识别结果，这样保证能搜到相似的
        val randomGame = gameDao.getRandomUnstudied()
        if (randomGame != null) {
            val boardType = object : TypeToken<List<PiecePosition>>() {}.type
            val pieces: List<PiecePosition>? = gson.fromJson(randomGame.initBoardJson, boardType)
            if (pieces != null) {
                // 稍微修改一下，模拟识别误差
                return pieces.shuffled().drop(1)
            }
        }
        
        // 如果数据库为空，返回默认开局
        return listOf(
            PiecePosition("r", 0, 0), PiecePosition("n", 1, 0), PiecePosition("b", 2, 0),
            PiecePosition("a", 3, 0), PiecePosition("k", 4, 0), PiecePosition("a", 5, 0),
            PiecePosition("b", 6, 0), PiecePosition("n", 7, 0), PiecePosition("r", 8, 0),
            PiecePosition("c", 1, 2), PiecePosition("c", 7, 2),
            PiecePosition("p", 0, 3), PiecePosition("p", 2, 3), PiecePosition("p", 4, 3),
            PiecePosition("p", 6, 3), PiecePosition("p", 8, 3),
            
            PiecePosition("R", 0, 9), PiecePosition("N", 1, 9), PiecePosition("B", 2, 9),
            PiecePosition("A", 3, 9), PiecePosition("K", 4, 9), PiecePosition("A", 5, 9),
            PiecePosition("B", 6, 9), PiecePosition("N", 7, 9), PiecePosition("R", 8, 9),
            PiecePosition("C", 1, 7), PiecePosition("C", 7, 7),
            PiecePosition("P", 0, 6), PiecePosition("P", 2, 6), PiecePosition("P", 4, 6),
            PiecePosition("P", 6, 6), PiecePosition("P", 8, 6)
        )
    }
}
