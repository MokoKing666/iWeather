# ============================================================
#  R8 / ProGuard 规则
#
#  release 开了 isMinifyEnabled + isShrinkResources，
#  下面每一条都是"不加就会在运行时崩"的，不是可选的优化建议。
# ============================================================

# ------------------------------------------------------------ PAG（最关键）

# PAG 是 native 实现（libpag.so），通过 JNI 反射回调 Java 层的类与方法。
# 不给 keep 规则，R8 会把 org.libpag 的成员当成"没被引用"删掉 ——
# 编译期不报错、debug 也不复现（debug 不混淆），
# 只有 release 里播到对应动效时才会在 native 层崩，极难定位。
-keep class org.libpag.** { *; }
-keep class org.extra.** { *; }
-dontwarn org.libpag.**
-dontwarn org.extra.**

# PAG 内部用到 exif（读图资源里的元信息）
-keep class androidx.exifinterface.** { *; }
-dontwarn androidx.exifinterface.**

# ------------------------------------------------------------ kotlinx.serialization

# 编译器为每个 @Serializable 类生成的 $$serializer 必须保留，
# 否则反序列化时找不到序列化器。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.moko.iweather.**$$serializer { *; }
-keepclassmembers class com.moko.iweather.** {
    *** Companion;
}
-keepclasseswithmembers class com.moko.iweather.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 枚举（Skycon / AlertLevel / TemperatureUnit 等）序列化时依赖 valueOf
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ------------------------------------------------------------ osmdroid

-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ------------------------------------------------------------ Glance / 小组件

# 小组件由系统在**别的进程**里按类名实例化，必须保留类名与构造器
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.action.ActionCallback { *; }

# ------------------------------------------------------------ WorkManager

# Worker 由 WorkManager 按类名反射实例化
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# ------------------------------------------------------------ DataStore / 其他

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
