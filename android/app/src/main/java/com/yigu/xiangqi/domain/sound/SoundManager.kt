package com.yigu.xiangqi.domain.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yigu.xiangqi.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class SoundType(val label: String, val rawResId: Int) {
    WOOD("木板", R.raw.sound_wood),
    STONE("石板", R.raw.sound_stone),
    JADE("玉石", R.raw.sound_jade),
    METAL("金属", R.raw.sound_metal),
    BAMBOO("竹制", R.raw.sound_bamboo),
    NONE("静音", 0),
}

@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loadedSounds = mutableMapOf<SoundType, Int>()

    private val _currentType = MutableStateFlow(SoundType.WOOD)
    val currentType: StateFlow<SoundType> = _currentType.asStateFlow()

    companion object {
        private val PREF_KEY = stringPreferencesKey("sound_type")
    }

    init {
        SoundType.entries.filter { it != SoundType.NONE }.forEach { type ->
            loadedSounds[type] = soundPool.load(context, type.rawResId, 1)
        }
        scope.launch {
            context.dataStore.data.map { prefs ->
                prefs[PREF_KEY]?.let { name ->
                    try { SoundType.valueOf(name) } catch (_: Exception) { SoundType.WOOD }
                } ?: SoundType.WOOD
            }.collect { _currentType.value = it }
        }
    }

    fun play() {
        val type = _currentType.value
        if (type == SoundType.NONE) return
        val soundId = loadedSounds[type] ?: return
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun setSoundType(type: SoundType) {
        _currentType.value = type
        scope.launch {
            context.dataStore.edit { prefs -> prefs[PREF_KEY] = type.name }
        }
        if (type != SoundType.NONE) play()
    }

    fun release() {
        soundPool.release()
    }
}
