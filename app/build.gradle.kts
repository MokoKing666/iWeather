import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 密钥读取顺序：
//   1) local.properties 里的 caiyun.token / caiyun.appKey / caiyun.appSecret / amap.key（推荐，不进版本库）
//   2) gradle.properties 或 -P 命令行参数
//   3) 环境变量 CAIYUN_TOKEN / CAIYUN_APP_KEY / CAIYUN_APP_SECRET / AMAP_KEY
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { stream -> load(stream) }
}

fun readSecret(vararg keys: String): String {
    for (key in keys) {
        val fromLocal = localProperties.getProperty(key)
        if (!fromLocal.isNullOrBlank()) return fromLocal
        val fromGradle = project.findProperty(key) as String?
        if (!fromGradle.isNullOrBlank()) return fromGradle
        val fromEnv = System.getenv(key.replace('.', '_').uppercase())
        if (!fromEnv.isNullOrBlank()) return fromEnv
    }
    return ""
}

fun String.asBuildConfigLiteral(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.moko.iweather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.moko.iweather"
        minSdk = 35
        targetSdk = 35
        versionCode = 107
        versionName = "1.0.7"
        vectorDrawables { useSupportLibrary = true }

        // PAG 自带 5 个 ABI 的 .so（共 16MB）。armeabi 是 ARMv5，Android 15 上早已绝迹；
        // x86/x86_64 只在模拟器上用。只留这两个真机 ABI，APK 少 5MB。
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        buildConfigField("String", "CAIYUN_TOKEN", readSecret("caiyun.token").asBuildConfigLiteral())
        buildConfigField("String", "CAIYUN_APP_KEY", readSecret("caiyun.appKey").asBuildConfigLiteral())
        buildConfigField("String", "CAIYUN_APP_SECRET", readSecret("caiyun.appSecret").asBuildConfigLiteral())
        buildConfigField("String", "AMAP_KEY", readSecret("amap.key").asBuildConfigLiteral())
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // 开 R8：未混淆时 dex 有 30MB 左右（Compose + osmdroid），混淆压缩后能降到 10MB 内。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    androidResources {
        // 字体不做压缩：Android 可以直接 mmap 资源，省一次解压和一份常驻内存。
        // 字体共约 29MB，压缩收益很小，但解压开销很明显。
        noCompress += listOf("otf", "ttf")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.glance:glance-appwidget:1.1.1")

    // 腾讯 PAG 动效库（Apache 2.0）：播放 After Effects 导出的 .pag 文件
    implementation("com.tencent.tav:libpag:4.5.98")

    // 后台任务编排：定时检查天气并推送通知（通知需要它，普通协程做不了）
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // 日志门面：release 里自动静默，debug 里带类名输出。当前几乎没有日志，出问题无从查起
    implementation("com.jakewharton.timber:timber:5.0.1")

    // 开源地图（OpenStreetMap 数据，不需要 API Key），天气地图页用
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    testImplementation("junit:junit:4.13.2")
}
