package com.yigu.xiangqi.domain.sound

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "yigu_settings")
