package com.moko.iweather.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** 系统定位：只用平台 LocationManager，不依赖 Google Play 服务。 */
class LocationProvider(private val context: Context) {

    private val manager: LocationManager? =
        ContextCompat.getSystemService(context, LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean = runCatching {
        manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true ||
            manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }.getOrDefault(false)

    /**
     * 取一次位置：优先用最近的位置（省电），太旧了再注册监听等一次新的。
     * 整体有超时兜底，不会无限挂起。
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(timeoutMs: Long = 12_000): Result<Location> {
        if (!hasPermission()) return Result.failure(WeatherError.LocationUnavailable)
        val lm = manager ?: return Result.failure(WeatherError.LocationUnavailable)

        val fresh = lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (fresh != null && System.currentTimeMillis() - fresh.time < 120_000) {
            return Result.success(fresh)
        }

        val location = withTimeoutOrNull(timeoutMs) { awaitSingleUpdate(lm) }
            ?: return Result.failure(WeatherError.LocationUnavailable)
        return Result.success(location)
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitSingleUpdate(lm: LocationManager): Location =
        suspendCancellableCoroutine { continuation ->
            val provider = when {
                runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                    LocationManager.NETWORK_PROVIDER
                else -> LocationManager.GPS_PROVIDER
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    // 先摘掉监听再 resume。原来只 resume 不 removeUpdates，
                    // 协程结束、invokeOnCancellation 也不会再触发（已经正常完成），
                    // 于是每定位一次就永久留下一个监听：反复定位会不断累积，
                    // 既耗电，后续回调也会打到已完成的协程上。
                    runCatching { lm.removeUpdates(this) }
                    if (continuation.isActive) continuation.resume(location)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) = Unit

                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }

            runCatching {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, context.mainLooper)
            }.onFailure {
                if (continuation.isActive) continuation.resumeWith(Result.failure(WeatherError.LocationUnavailable))
            }

            continuation.invokeOnCancellation {
                runCatching { lm.removeUpdates(listener) }
            }
        }
}
