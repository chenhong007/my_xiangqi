package com.yigu.xiangqi.domain.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yigu.xiangqi.ui.board.MoveHighlightStyle
import com.yigu.xiangqi.ui.board.PieceDropEffectType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DisplayMode(val displayName: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("经典深色"),
    EYE_CARE("护眼模式")
}

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "ui_preferences")

class UIPreferencesManager(private val context: Context) {
    
    companion object {
        private val PIECE_DROP_EFFECT_KEY = stringPreferencesKey("piece_drop_effect")
        private val AUTO_PLAY_SPEED_KEY = longPreferencesKey("auto_play_speed_ms")
        private val HIGHLIGHT_STYLE_KEY = stringPreferencesKey("move_highlight_style")
        private val GUESS_MODE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("guess_mode")
        private val GUESS_RESPONSE_DELAY_KEY = longPreferencesKey("guess_response_delay_ms")
        private val DISPLAY_MODE_KEY = stringPreferencesKey("display_mode")
        const val DEFAULT_AUTO_PLAY_SPEED_MS = 2000L
        const val DEFAULT_GUESS_RESPONSE_DELAY_MS = 500L
    }
    
    val guessResponseDelayMs: Flow<Long> = context.preferencesDataStore.data
        .map { preferences ->
            preferences[GUESS_RESPONSE_DELAY_KEY] ?: DEFAULT_GUESS_RESPONSE_DELAY_MS
        }
        
    suspend fun setGuessResponseDelay(delayMs: Long) {
        context.preferencesDataStore.edit { preferences ->
            preferences[GUESS_RESPONSE_DELAY_KEY] = delayMs
        }
    }
    
    val guessMode: Flow<Boolean> = context.preferencesDataStore.data
        .map { preferences ->
            preferences[GUESS_MODE_KEY] ?: false
        }
        
    suspend fun setGuessMode(enabled: Boolean) {
        context.preferencesDataStore.edit { preferences ->
            preferences[GUESS_MODE_KEY] = enabled
        }
    }
    
    val pieceDropEffect: Flow<PieceDropEffectType> = context.preferencesDataStore.data
        .map { preferences ->
            val effectName = preferences[PIECE_DROP_EFFECT_KEY] ?: PieceDropEffectType.TIANTIAN_XIANGQI.name
            try {
                PieceDropEffectType.valueOf(effectName)
            } catch (e: Exception) {
                PieceDropEffectType.TIANTIAN_XIANGQI
            }
        }
    
    suspend fun setPieceDropEffect(effect: PieceDropEffectType) {
        context.preferencesDataStore.edit { preferences ->
            preferences[PIECE_DROP_EFFECT_KEY] = effect.name
        }
    }

    val highlightStyle: Flow<MoveHighlightStyle> = context.preferencesDataStore.data
        .map { preferences ->
            val name = preferences[HIGHLIGHT_STYLE_KEY] ?: MoveHighlightStyle.TIANTIAN.name
            try {
                MoveHighlightStyle.valueOf(name)
            } catch (e: Exception) {
                MoveHighlightStyle.TIANTIAN
            }
        }

    suspend fun setHighlightStyle(style: MoveHighlightStyle) {
        context.preferencesDataStore.edit { preferences ->
            preferences[HIGHLIGHT_STYLE_KEY] = style.name
        }
    }

    val autoPlaySpeedMs: Flow<Long> = context.preferencesDataStore.data
        .map { preferences ->
            preferences[AUTO_PLAY_SPEED_KEY] ?: DEFAULT_AUTO_PLAY_SPEED_MS
        }

    suspend fun setAutoPlaySpeed(speedMs: Long) {
        context.preferencesDataStore.edit { preferences ->
            preferences[AUTO_PLAY_SPEED_KEY] = speedMs
        }
    }

    val displayMode: Flow<DisplayMode> = context.preferencesDataStore.data
        .map { preferences ->
            val name = preferences[DISPLAY_MODE_KEY] ?: DisplayMode.SYSTEM.name
            try {
                DisplayMode.valueOf(name)
            } catch (e: Exception) {
                DisplayMode.SYSTEM
            }
        }

    suspend fun setDisplayMode(mode: DisplayMode) {
        context.preferencesDataStore.edit { preferences ->
            preferences[DISPLAY_MODE_KEY] = mode.name
        }
    }
}
