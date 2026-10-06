package com.moko.iweather.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat

/**
 * 全 App 字体：**西文与数字用 SF Pro，汉字用苹方**。
 *
 * ## 字体文件从哪来
 * `res/font/` 下的六个 `.otf` 是 **SF Pro 与苹方的子集**，由
 * `tools/build_fonts.py` 从**本机系统字体**现场生成。
 *
 * 它们**不在版本库里**（见 `.gitignore`），原因见下。
 *
 * ## ⚠️ 授权
 * SF Pro 与苹方的许可**都只覆盖 Apple 平台**，打进 Android 包属于超出授权范围，
 * 更不能公开分发。所以公开仓库里不含字体文件，克隆后自己跑一次生成脚本：
 *
 * ```
 * python tools/build_fonts.py
 * ```
 *
 * ## 为什么按资源名解析，而不是写 `R.font.app_thin`
 * 如果代码里硬引用 `R.font.app_*`，那么**字体文件缺席时整个工程根本编译不过** ——
 * 一个公开仓库不该有这种前置条件。
 *
 * 改成运行时用 `getIdentifier` 按名字找：文件在就用，不在就返回 null、
 * 整体退回系统字体。**两种情况都能编译、都能运行**，只是观感不同。
 *
 * ## 为什么一个字重一个文件
 * Compose 的 `FontFamily` 只能按**字重**选字体，**不支持按字符回退**
 * （那是 CSS `font-family: A, B` 的能力）。SF Pro 里没有汉字，
 * 若只把 SF Pro 设成字体族，中文会退回系统默认字体而不是苹方。
 *
 * 唯一能带自定义回退的 `Typeface.CustomFallbackBuilder` 产出的是
 * `android.graphics.Typeface`，而 Compose 侧包装它的
 * `androidx.compose.ui.text.platform.AndroidTypeface` 是 `internal` 的，
 * App 代码实现不了。所以只能**让字体文件自己同时含西文与汉字**：
 * 生成脚本把两边都转成 TrueType 轮廓后合并。
 */
object AppFonts {

    /** 资源名 -> 字重。名字必须与 `tools/build_fonts.py` 的输出一致。 */
    private val WeightNames: List<Pair<String, FontWeight>> = listOf(
        "app_thin" to FontWeight.Thin,
        "app_light" to FontWeight.Light,
        "app_regular" to FontWeight.Normal,
        "app_medium" to FontWeight.Medium,
        "app_semibold" to FontWeight.SemiBold,
        "app_bold" to FontWeight.Bold
    )

    @Volatile
    private var resolved: FontFamily? = null

    /**
     * 在 `Application.onCreate` 里调一次：解析并**试用加载**字体。
     *
     * 之所以要试加载而不是只看文件在不在：这些字体是手工做轮廓转换 + upem 缩放 + 合并
     * 出来的非标准产物，万一某个字重系统解析不了，界面会在**第一次排版文字时**整体崩掉 ——
     * 那种闪退极难定位。这里提前挡住，加载不了就整体不用。
     */
    fun verify(context: Context) {
        val app = context.applicationContext
        resolved = runCatching {
            val fonts = WeightNames.mapNotNull { (name, weight) ->
                val id = app.resources.getIdentifier(name, "font", app.packageName)
                if (id == 0) return@mapNotNull null
                // 真正解析一次，坏字体在这一步就会被挡下
                if (ResourcesCompat.getFont(app, id) == null) return@mapNotNull null
                Font(id, weight)
            }
            // 六个字重必须全在位，缺一个就整体不用。
            // 只上部分的话，同一段文字换个字重就会突然变成系统字体，
            // 比干脆全用系统字体更难看。
            if (fonts.size == WeightNames.size) FontFamily(fonts) else null
        }.getOrNull()
    }

    /** 拿不到（没生成字体、或系统解析失败）就返回 null，由主题退回系统字体。 */
    fun family(): FontFamily? = resolved
}

/**
 * 把字体族套到 Material 的整套字阶上。
 *
 * `fontWeight` 一律保留：六个字重都已经按 weight 注册进字体族，
 * 系统会自己挑对应的文件，不需要（也不该）让 Android 合成假粗体。
 *
 * 传 null 时原样返回 —— 也就是继续用系统字体。
 */
fun Typography.withAppFontFamily(family: FontFamily? = AppFonts.family()): Typography {
    if (family == null) return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family)
    )
}
