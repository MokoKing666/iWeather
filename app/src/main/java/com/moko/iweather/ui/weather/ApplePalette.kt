package com.moko.iweather.ui.weather

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Color
import com.moko.iweather.core.Skycon
import com.moko.iweather.core.SkyconFamily

/**
 * Apple Weather 风格的背景配色与动画。
 *
 * 关键差异（相比通用天气 App）：Apple 用的是**四段渐变**，从上到下由
 * 深到浅、饱和度递减，过渡很长很柔；夜间的底色是深海军蓝而不是纯黑。
 * 这里的色值按 Apple Weather 各天气现象的实际观感还原。
 */
data class AppleWeatherTheme(
    val family: SkyconFamily,
    val isDaytime: Boolean,
    val colors: List<Color>,
    val particle: ParticleStyle
) {
    companion object {

        fun of(family: SkyconFamily, isDaytime: Boolean, isSystemDark: Boolean = false): AppleWeatherTheme {
            val base = base(family, isDaytime)
            return base.copy(
                colors = if (isSystemDark) base.colors.map { darken(it, 0.34f) } else base.colors
            )
        }

        fun of(skycon: Skycon, isDaytime: Boolean, isSystemDark: Boolean = false) =
            of(skycon.family, isDaytime, isSystemDark)

        private fun base(family: SkyconFamily, day: Boolean) = when (family) {
            SkyconFamily.CLEAR ->
                if (day) theme(SkyconFamily.CLEAR, true, 0xFF0E3D8C, 0xFF1A58A6, 0xFF2C7CBC, 0xFF4E9ED4, ParticleStyle.Clouds(0.14f))
                else theme(SkyconFamily.CLEAR, false, 0xFF060D1E, 0xFF0C1A38, 0xFF17295A, 0xFF24386B, ParticleStyle.Clouds(0.10f))

            SkyconFamily.PARTLY_CLOUDY ->
                if (day) theme(SkyconFamily.PARTLY_CLOUDY, true, 0xFF275C93, 0xFF3A77A8, 0xFF5691BC, 0xFF7FB2D2, ParticleStyle.Clouds(0.24f))
                else theme(SkyconFamily.PARTLY_CLOUDY, false, 0xFF0B1730, 0xFF16294C, 0xFF233A64, 0xFF33507E, ParticleStyle.Clouds(0.16f))

            SkyconFamily.CLOUDY ->
                if (day) theme(SkyconFamily.CLOUDY, true, 0xFF3B5468, 0xFF556D85, 0xFF738CA1, 0xFF97AFBF, ParticleStyle.Clouds(0.32f))
                else theme(SkyconFamily.CLOUDY, false, 0xFF141E28, 0xFF26333F, 0xFF3B4B59, 0xFF566471, ParticleStyle.Clouds(0.32f))

            SkyconFamily.RAIN ->
                if (day) theme(SkyconFamily.RAIN, true, 0xFF1C2E42, 0xFF294155, 0xFF3A556C, 0xFF557089, ParticleStyle.Rain(2))
                else theme(SkyconFamily.RAIN, false, 0xFF0A1219, 0xFF16232F, 0xFF243646, 0xFF3A5164, ParticleStyle.Rain(2))

            SkyconFamily.SNOW ->
                if (day) theme(SkyconFamily.SNOW, true, 0xFF33465C, 0xFF4A5F7B, 0xFF6C8299, 0xFF95A9BC, ParticleStyle.Snow(2))
                else theme(SkyconFamily.SNOW, false, 0xFF0D141C, 0xFF1C2734, 0xFF2E3D4E, 0xFF475A6E, ParticleStyle.Snow(2))

            SkyconFamily.FOG ->
                if (day) theme(SkyconFamily.FOG, true, 0xFF424F5B, 0xFF5E6C78, 0xFF7C8994, 0xFF9EABB5, ParticleStyle.Fog)
                else theme(SkyconFamily.FOG, false, 0xFF10161C, 0xFF222B33, 0xFF37424C, 0xFF525E68, ParticleStyle.Fog)

            SkyconFamily.HAZE ->
                if (day) theme(SkyconFamily.HAZE, true, 0xFF5C4D32, 0xFF7A6949, 0xFF9C8963, 0xFFBAA98C, ParticleStyle.Fog)
                else theme(SkyconFamily.HAZE, false, 0xFF1C1710, 0xFF33291A, 0xFF4C3E27, 0xFF6B5A3B, ParticleStyle.Fog)

            SkyconFamily.DUST ->
                if (day) theme(SkyconFamily.DUST, true, 0xFF553E25, 0xFF755939, 0xFF977853, 0xFFB79B74, ParticleStyle.Dust)
                else theme(SkyconFamily.DUST, false, 0xFF1C150E, 0xFF33261A, 0xFF4C3A28, 0xFF6B533A, ParticleStyle.Dust)

            SkyconFamily.WIND ->
                if (day) theme(SkyconFamily.WIND, true, 0xFF1F4254, 0xFF2D5473, 0xFF426C86, 0xFF688EA3, ParticleStyle.Dust)
                else theme(SkyconFamily.WIND, false, 0xFF0A151B, 0xFF152831, 0xFF233C48, 0xFF3A5A69, ParticleStyle.Dust)

            SkyconFamily.UNKNOWN ->
                if (day) theme(SkyconFamily.UNKNOWN, true, 0xFF323D4A, 0xFF465462, 0xFF5F6C78, 0xFF7D8892, ParticleStyle.None)
                else theme(SkyconFamily.UNKNOWN, false, 0xFF0B0F14, 0xFF161D25, 0xFF232D37, 0xFF36424E, ParticleStyle.None)
        }

        private fun theme(
            family: SkyconFamily,
            day: Boolean,
            c1: Long, c2: Long, c3: Long, c4: Long,
            particle: ParticleStyle
        ) = AppleWeatherTheme(family, day, listOf(
            Color(c1), Color(c2), Color(c3), Color(c4)
        ), particle)

        private fun darken(color: Color, factor: Float) = Color(
            red = color.red * (1f - factor),
            green = color.green * (1f - factor),
            blue = color.blue * (1f - factor),
            alpha = color.alpha
        )
    }
}

/** 背景动画粒子的类型与强度。 */
sealed interface ParticleStyle {
    data object None : ParticleStyle
    data class Clouds(val opacity: Float) : ParticleStyle
    data class Rain(val intensity: Int) : ParticleStyle
    data class Snow(val intensity: Int) : ParticleStyle
    data object Fog : ParticleStyle
    data object Dust : ParticleStyle
}

/** skycon → SF Symbol。夜间把太阳换成月相，与 Apple 的昼夜图标逻辑一致。 */
object SkyconIcons {

    fun icon(skycon: Skycon, isDaytime: Boolean) = when (skycon.family) {
        SkyconFamily.CLEAR -> if (isDaytime) Icons.Filled.WbSunny else Icons.Filled.NightsStay
        SkyconFamily.PARTLY_CLOUDY -> if (isDaytime) Icons.Filled.WbCloudy else Icons.Filled.Cloud
        SkyconFamily.CLOUDY -> Icons.Filled.Cloud
        SkyconFamily.RAIN -> when (skycon) {
            Skycon.LIGHT_RAIN -> Icons.Filled.Grain
            Skycon.MODERATE_RAIN -> Icons.Filled.WaterDrop
            else -> Icons.Filled.Umbrella
        }
        SkyconFamily.SNOW -> Icons.Filled.AcUnit
        SkyconFamily.FOG -> Icons.Filled.BlurOn
        SkyconFamily.HAZE -> Icons.Filled.FilterDrama
        SkyconFamily.DUST -> Icons.Filled.Grain
        SkyconFamily.WIND -> Icons.Filled.Air
        SkyconFamily.UNKNOWN -> Icons.Filled.HelpOutline
    }
}
