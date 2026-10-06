package com.moko.iweather.core

import java.util.Calendar
import java.util.TimeZone

/**
 * 把彩云的响应映射成领域模型。
 *
 * 两个要点：
 *  1. `hourly` / `daily` 是"按变量分数组 + 每项自带时间"，必须按时间对齐，
 *     不能把 temperature 和 skycon 数组按下标 zip（长度和缺失项都可能不同）；
 *  2. 量纲在这里统一：湿度转 0~1 保留、降水概率归一化到 0~1，
 *     气压保留 Pa（由 UI 决定显示 hPa）、风速保留 m/s。
 */
object SnapshotMapper {

    fun map(
        response: CaiyunResponse,
        city: WeatherCity,
        fetchedAt: Long = System.currentTimeMillis()
    ): WeatherSnapshot {
        val result = response.result
        val timeZone = resolveTimeZone(response.timezone, response.tzShiftSeconds, city)

        return WeatherSnapshot(
            cityId = city.id,
            timeZoneId = timeZone,
            fetchedAt = fetchedAt,
            realtime = mapRealtime(result?.realtime),
            hourly = mapHourly(result?.hourly, timeZone),
            daily = mapDaily(result?.daily, timeZone),
            airQuality = mapAirQuality(result?.realtime?.airQuality),
            alerts = mapAlerts(result?.alert),
            forecastKeypoint = result?.forecastKeypoint?.takeIf { it.isNotBlank() },
            hourlySummary = result?.hourly?.description?.takeIf { it.isNotBlank() }
        )
    }

    /** 彩云返回的 timezone 是 IANA 名；拿不到就用 tzshift 兜底，再退到城市上的推算值。 */
    private fun resolveTimeZone(fromApi: String?, tzShiftSeconds: Int?, city: WeatherCity): String {
        fromApi?.takeIf { TimeZoneResolver.isValidId(it) }?.let { return it }
        tzShiftSeconds?.let { seconds ->
            TimeZoneResolver.zoneByOffsetSeconds(seconds)?.let { zone -> return zone.id }
        }
        return city.timeZoneId.takeIf { TimeZoneResolver.isValidId(it) } ?: "GMT+00:00"
    }

    // ------------------------------------------------------------ 实况

    private fun mapRealtime(source: CaiyunRealtime?): Realtime {
        if (source == null) return Realtime.EMPTY
        val ultraviolet = source.lifeIndex?.ultraviolet
        return Realtime(
            temperature = source.temperature,
            apparentTemperature = source.apparentTemperature,
            skycon = Skycon.from(source.skycon),
            humidity = source.humidity,
            pressureHpa = source.pressure?.div(100.0),
            visibilityKm = source.visibility,
            windSpeedMs = source.wind?.speed,
            windDirectionDeg = source.wind?.direction,
            cloudRate = source.cloudrate,
            precipitationIntensity = source.precipitation?.local?.intensity
                ?: source.precipitation?.nearest?.intensity,
            solarRadiation = source.dswrf,
            uvIndex = ultraviolet?.index,
            uvText = ultraviolet?.desc,
            comfortText = source.lifeIndex?.comfort?.desc
        )
    }

    private fun mapAirQuality(source: CaiyunAirQuality?): AirQuality {
        if (source == null) return AirQuality.EMPTY
        return AirQuality(
            aqi = source.aqi?.chn,
            category = source.description?.chn,
            pm25 = source.pm25,
            pm10 = source.pm10,
            o3 = source.o3,
            no2 = source.no2,
            so2 = source.so2,
            co = source.co
        )
    }

    // ------------------------------------------------------------ 逐时

    private fun mapHourly(source: CaiyunHourly?, timeZone: String): List<HourlyPoint> {
        if (source == null) return emptyList()
        val zone = TimeZoneResolver.zone(timeZone)

        val temperatures = source.temperature.associateBy { it.datetime }
        val skycons = source.skycon.associateBy { it.datetime }
        val precipitations = source.precipitation.associateBy { it.datetime }
        val winds = source.wind.associateBy { it.datetime }

        return temperatures.entries
            .mapNotNull { (datetime, item) ->
                val time = CaiyunClient.parseIso(datetime, zone) ?: return@mapNotNull null
                HourlyPoint(
                    time = time,
                    temperature = item.value,
                    skycon = Skycon.from(skycons[datetime]?.value),
                    precipProbability = normalizeProbability(precipitations[datetime]?.probability),
                    precipitationIntensity = precipitations[datetime]?.value,
                    windSpeedMs = winds[datetime]?.speed
                )
            }
            .sortedBy { it.time }
    }

    // ------------------------------------------------------------ 逐天

    private fun mapDaily(source: CaiyunDaily?, timeZone: String): List<DailyPoint> {
        if (source == null) return emptyList()
        val zone = TimeZoneResolver.zone(timeZone)

        val astroByDate = source.astro.associateBy { it.date }
        val precipByDate = source.precipitation.associateBy { it.date }
        val uvByDate = source.lifeIndex?.ultraviolet?.associateBy { it.date }.orEmpty()
        val dressingByDate = source.lifeIndex?.dressing?.associateBy { it.date }.orEmpty()
        val comfortByDate = source.lifeIndex?.comfort?.associateBy { it.date }.orEmpty()
        val carWashByDate = source.lifeIndex?.carWashing?.associateBy { it.date }.orEmpty()

        return source.temperature.mapNotNull { item ->
            val date = CaiyunClient.parseIso(item.date, zone) ?: return@mapNotNull null
            val astro = astroByDate[item.date]
            DailyPoint(
                date = date,
                skyconDay = Skycon.from(source.skycon.firstOrNull { it.date == item.date }?.value),
                temperatureMax = item.max,
                temperatureMin = item.min,
                temperatureAvg = item.avg,
                precipProbability = normalizeProbability(precipByDate[item.date]?.probability),
                precipMax = precipByDate[item.date]?.max,
                // astro 只给 "07:13"，用当天的本地零点做基准拼出绝对时刻
                sunrise = combineDateAndTime(date, astro?.sunrise?.time, zone),
                sunset = combineDateAndTime(date, astro?.sunset?.time, zone),
                uvText = uvByDate[item.date]?.desc,
                dressingText = dressingByDate[item.date]?.desc,
                comfortText = comfortByDate[item.date]?.desc,
                carWashText = carWashByDate[item.date]?.desc
            )
        }.sortedBy { it.date }
    }

    /** 把 `HH:mm` 挂到该日本地零点之后的偏移上。 */
    private fun combineDateAndTime(
        dayStart: Long,
        time: String?,
        zone: TimeZone
    ): Long? {
        val text = time?.takeIf { it.isNotBlank() } ?: return null
        val parts = text.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = dayStart }
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // ------------------------------------------------------------ 预警

    private fun mapAlerts(source: CaiyunAlert?): List<WeatherAlert> {
        if (source == null) return emptyList()
        return source.content.mapNotNull { alert ->
            val title = alert.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WeatherAlert(
                id = title.hashCode().toString(),
                title = title,
                type = alert.type.orEmpty(),
                level = AlertLevel.from(alert.level),
                body = alert.detail.orEmpty(),
                publishedAt = CaiyunClient.parseIso(alert.effective, TimeZone.getTimeZone("Asia/Shanghai")),
                source = alert.sender?.takeIf { it.isNotBlank() }
            )
        }
    }

    /**
     * 降水概率归一化到 0~1。
     * 彩云在不同版本里返回过 0~1 与 0~100 两种量纲，这里两种都兼容
     * （只有恰好等于 1 时无法区分，实际影响可忽略）。
     */
    fun normalizeProbability(raw: Double?): Double? {
        val value = raw ?: return null
        if (value <= 0.0) return 0.0
        return if (value <= 1.0) value else (value / 100.0).coerceAtMost(1.0)
    }
}
