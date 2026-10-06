package com.moko.iweather.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** 从 Context 里逐层找出 Activity（`shouldShowRequestPermissionRationale` 是 Activity 的方法）。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.showLocationRationale(): Boolean =
    findActivity()?.shouldShowRequestPermissionRationale(
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) ?: false

/** 定位权限的四种状态。 */
enum class LocationPermissionState {
    /** 从未申请过 */
    UNKNOWN,

    /** 已授权 */
    GRANTED,

    /** 申请过但被拒绝，可以再次弹窗申请 */
    DENIED,

    /** 被"不再询问"或系统策略拒绝，只能去系统设置里开 */
    BLOCKED
}

/** 是否已获得定位权限（粗略或精确任一即可）。 */
fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.hasAskedForLocation(): Boolean =
    getSharedPreferences(PERM_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ASKED, false)

private fun Context.markLocationAsked() {
    getSharedPreferences(PERM_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_ASKED, true).apply()
}

private const val PERM_PREFS = "iweather_permission_state"
private const val KEY_ASKED = "location_requested"

/**
 * 定位权限控制器 —— 修掉"点了没反应"的关键。
 *
 * 之前 `viewModel.locateCurrentCity()` 只做了一次权限检查，没授权就写一句提示，
 * 从设置页/城市页点进去永远弹不出系统对话框。现在统一由这里发起申请。
 */
class LocationPermissionController(
    val state: LocationPermissionState,
    /** 发起系统权限申请弹窗 */
    val request: () -> Unit,
    /** 跳转到系统应用设置页 */
    val openSystemSettings: () -> Unit
) {
    val isGranted: Boolean get() = state == LocationPermissionState.GRANTED

    /** 根据当前状态决定该做什么：已授权就直接回调，否则弹窗。 */
    fun requestOrRun(onGranted: () -> Unit) {
        if (isGranted) onGranted() else request()
    }
}

/**
 * 记住一个权限控制器。
 *
 * @param onGranted 获得授权（或本来就有权限）后回调，通常在这里真正去定位。
 */
@Composable
fun rememberLocationPermissionController(
    onGranted: () -> Unit
): LocationPermissionController {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasLocationPermission()) }
    // 用于区分"从未申请"与"永久拒绝"：系统只在"申请过且被拒"时才返回 false。
    var shouldShowRationale by remember { mutableStateOf(context.showLocationRationale()) }
    var hasAsked by remember { mutableStateOf(context.hasAskedForLocation()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val nowGranted = result.values.any { it } || context.hasLocationPermission()
        granted = nowGranted
        hasAsked = true
        context.markLocationAsked()
        shouldShowRationale = context.showLocationRationale()
        if (nowGranted) onGranted()
    }

    val state = when {
        granted -> LocationPermissionState.GRANTED
        shouldShowRationale -> LocationPermissionState.DENIED
        hasAsked -> LocationPermissionState.BLOCKED
        else -> LocationPermissionState.UNKNOWN
    }
    return remember(state) {
        LocationPermissionController(
            state = state,
            request = {
                // 系统定位总开关关着时，弹窗不会有任何回音，先引导去设置页
                if (!isLocationServiceEnabled(context)) {
                    context.startActivity(
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                }
            },
            openSystemSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.packageName)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )
    }
}

/** 仅返回一个"确保有权限"的回调，方便在别处一行接上。 */
@Composable
fun rememberLocationRequester(onGranted: () -> Unit): () -> Unit {
    val controller = rememberLocationPermissionController(onGranted)
    return remember(controller) { { controller.requestOrRun(onGranted) } }
}

private fun isLocationServiceEnabled(context: Context): Boolean = runCatching {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
        lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
}.getOrDefault(false)
