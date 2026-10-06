package com.moko.iweather.ui.weather

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.moko.iweather.core.SkyconFamily
import org.libpag.PAGFile
import org.libpag.PAGView

/**
 * PAG（腾讯开源的动效库）在 Compose 里的封装。
 *
 * ## 现状说明（重要）
 * PAG 播放的是 After Effects 通过 PAGExporter 导出的 `.pag` 文件。
 * 我扒了 `Tencent/libpag` 仓库里全部 80 个 `.pag`，**没有一个是天气动效** ——
 * 绝大多数是渲染器的功能测试用例（`TextPath*`、`RootLayer*`、`MultiVideoSequence*`）。
 * 里面只有几个是真正设计过的演示动画（`travel` 43KB、`moonlight` 14KB、`PAG_LOGO` 10.8KB…）。
 *
 * 所以这里的定位是**把管线搭好**：
 *  - [PagAssets] 定义"天气现象 → 素材文件名"的映射；
 *  - 有素材就播 PAG，没素材就返回 null、由调用方继续用程序化粒子；
 *  - 以后设计师出了真天气动效，丢进 `assets/pag/` 再在映射表里加一行就行。
 *
 * ## 为什么要防御性写法
 * - `.pag` 是二进制资产，加载失败的原因很多（文件缺失、版本不兼容、ABI 不匹配）；
 * - PAG 内部是 native 实现，真出问题可能是 `SIGSEGV` —— Kotlin 接不住，
 *   所以**能在 Java 层拦的都要拦**，并且宁可什么都不显示，也不能让整个界面挂掉。
 */
object PagAssets {

    const val DIR = "pag/"

    /**
     * 天气现象 + 昼夜 → `.pag` 文件名。
     *
     * 目前只映射了 `moonlight`（晴朗夜晚）这一个 —— 因为仓库里
     * **确实没有雨、雪、云、雾的动效素材**，我不编造不存在的文件名。
     * 拿到真素材后在这里加行即可，其余现象会自动回退到程序化粒子。
     */
    fun assetFor(family: SkyconFamily, isDaytime: Boolean): String? = when {
        family == SkyconFamily.CLEAR && !isDaytime -> "moonlight.pag"
        else -> null
    }
}

/**
 * 播放天气动效。没有对应素材、或加载失败时**不占位、不显示任何东西**。
 *
 * @return 无
 */
@Composable
fun PagWeatherEffect(
    family: SkyconFamily,
    isDaytime: Boolean,
    modifier: Modifier = Modifier
) {
    val asset = remember(family, isDaytime) { PagAssets.assetFor(family, isDaytime) } ?: return
    val context = LocalContext.current

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx -> createPagView(ctx, PagAssets.DIR + asset) },
        update = { view -> (view as? PAGView)?.runCatching { play() } },
        onRelease = { view ->
            (view as? PAGView)?.runCatching {
                stop()
                freeCache()
            }
        }
    )
}

/**
 * 建 PAGView。任何一步失败都返回一个**透明的空 View**，
 * 而不是抛异常或返回 null（`AndroidView` 的 factory 不允许返回 null）。
 */
private fun createPagView(context: Context, assetPath: String): View = runCatching {
    val file = PAGFile.Load(context.assets, assetPath)
        ?: return@runCatching View(context)
    PAGView(context).apply {
        setRepeatCount(INFINITE)
        // 注意 setComposition 在这个版本里返回 Unit（旧版返回 Boolean），
        // 所以只能靠 PAGFile.Load 是否为 null 判断成败。
        setComposition(file)
        play()
    }
}.getOrElse { View(context) }

/** PAG 的无限循环常量。 */
private const val INFINITE = -1
