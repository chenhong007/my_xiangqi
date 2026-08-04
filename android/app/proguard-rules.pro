# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Gson
-keepattributes Signature
-keep class com.yigu.xiangqi.data.local.entity.** { *; }
-keep class com.yigu.xiangqi.domain.model.** { *; }
