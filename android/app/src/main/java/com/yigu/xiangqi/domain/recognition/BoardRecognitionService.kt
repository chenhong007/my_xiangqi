package com.yigu.xiangqi.domain.recognition

import android.net.Uri
import com.yigu.xiangqi.domain.model.PiecePosition

interface BoardRecognitionService {
    /**
     * 识别图片中的棋盘，返回棋子位置列表
     */
    suspend fun recognizeBoard(imageUri: Uri): List<PiecePosition>
}
