package com.example.vemoring

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class NavigationApp(val title: String, val packageName: String) {
    PLEOS("차량 기본 내비게이션", "ai.umos.maps.android.navigation.app"),
    NAVER("네이버지도", "com.nhn.android.nmap"),
    KAKAO("카카오맵", "net.daum.android.map");

    fun searchIntent(keyword: String, callerPackage: String): Intent {
        require(this != PLEOS) { "Pleos searches use the official NaviHelper SDK." }
        val uri = when (this) {
            NAVER -> Uri.Builder().scheme("nmap").authority("search")
                .appendQueryParameter("query", keyword).appendQueryParameter("appname", callerPackage).build()
            KAKAO -> Uri.Builder().scheme("kakaomap").authority("search")
                .appendQueryParameter("q", keyword).build()
            else -> error("Unsupported navigation app")
        }
        return Intent(Intent.ACTION_VIEW, uri).setPackage(packageName).addCategory(Intent.CATEGORY_BROWSABLE)
    }

    fun isAvailable(context: Context, keyword: String): Boolean = try {
        if (this == PLEOS) {
            context.packageManager.getApplicationInfo(packageName, 0).enabled
        } else {
            searchIntent(keyword, context.packageName).resolveActivity(context.packageManager) != null
        }
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Bound lazily after a tap and released with the banner. Never requests a route. */
class PleosPlaceSearch(private val context: Context, private val onError: (String) -> Unit) {
    private var helper: NaviHelper? = null
    private var connected = false
    private val handler = Handler(Looper.getMainLooper())
    private val listener = object : NaviHelperEventListener {
        override fun onError(code: NaviErrorCode) {
            handler.post { if (helper != null) onError("차량 내비게이션에서 검색을 처리하지 못했어요. ($code)") }
        }
    }

    suspend fun search(keyword: String) {
        try {
            withTimeout(8000) {
                val client = helper ?: NaviHelper(context.applicationContext).also { helper = it }
                if (!connected) {
                    suspendCancellableCoroutine { continuation ->
                        client.initialize(onConnected = {
                            if (continuation.isActive) continuation.resume(Unit)
                        }, onFailed = { failure ->
                            if (continuation.isActive) continuation.resumeWithException(failure)
                        })
                    }
                    connected = true
                    client.addListener(listener)
                }
                client.requestNavigationScreen(NavigationScreenRequest.SearchPlace(keyword))
            }
        } catch (failure: Exception) {
            close()
            throw failure
        }
    }

    fun close() {
        val client = helper
        helper = null
        connected = false
        handler.removeCallbacksAndMessages(null)
        runCatching { client?.removeListener(listener) }
        runCatching { client?.release() }
    }
}
