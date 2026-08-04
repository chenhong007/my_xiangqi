package com.yigu.xiangqi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.yigu.xiangqi.domain.preferences.DisplayMode
import com.yigu.xiangqi.domain.preferences.UIPreferencesManager
import com.yigu.xiangqi.ui.YiGuMainScreen
import com.yigu.xiangqi.ui.theme.YiGuTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var uiPreferencesManager: UIPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val displayMode by uiPreferencesManager.displayMode.collectAsState(initial = DisplayMode.SYSTEM)
            
            YiGuTheme(displayMode = displayMode) {
                YiGuMainScreen()
            }
        }
    }
}
