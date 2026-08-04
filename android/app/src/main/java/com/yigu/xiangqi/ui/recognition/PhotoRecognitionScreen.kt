package com.yigu.xiangqi.ui.recognition

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.yigu.xiangqi.ui.board.BoardCanvas
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoRecognitionScreen(
    onBack: () -> Unit,
    onGameClick: (String) -> Unit,
    viewModel: PhotoRecognitionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // 用于保存拍照的临时文件 URI
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempImageUri != null) {
            viewModel.processImage(tempImageUri!!)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processImage(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照识谱") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val currentState = state) {
                is RecognitionState.Idle -> {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        "请拍摄或选择一张包含棋盘的图片",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                val tempFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    tempFile
                                )
                                tempImageUri = uri
                                takePictureLauncher.launch(uri)
                            },
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("拍照")
                        }
                        Button(
                            onClick = { pickImageLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("相册")
                        }
                    }
                }

                is RecognitionState.Recognizing -> {
                    ImagePreview(currentState.imageUri)
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在识别棋盘...")
                }

                is RecognitionState.Searching -> {
                    ImagePreview(currentState.imageUri)
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在古谱中检索相似对局...")
                }

                is RecognitionState.Result -> {
                    // 显示识别结果和匹配的古谱
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("识别结果", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.9f)
                        ) {
                            BoardCanvas(
                                pieces = currentState.board,
                                selectedCell = null,
                                lastMoveFrom = null,
                                lastMoveTo = null,
                                validTargets = emptySet(),
                                onCellTap = { _, _ -> }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (currentState.matchGame != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("找到最相似的古谱对局：", style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        currentState.matchGame.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { onGameClick(currentState.matchGame.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("跳转并开始推演")
                                    }
                                }
                            }
                        } else {
                            Text("未找到相似的古谱对局", color = MaterialTheme.colorScheme.error)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.reset() }) {
                            Text("重新识别")
                        }
                    }
                }

                is RecognitionState.Error -> {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        currentState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = { viewModel.reset() }) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(uri: Uri) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        Image(
            painter = rememberAsyncImagePainter(uri),
            contentDescription = "Selected Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
