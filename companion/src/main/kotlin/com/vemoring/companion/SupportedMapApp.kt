package com.vemoring.companion

import android.content.Intent
import android.net.Uri

/** The same externally supported providers and URL schemes on both apps. */
enum class SupportedMapApp(val title: String, val packageName: String) {
    NAVER("네이버지도", "com.nhn.android.nmap"), KAKAO("카카오맵", "net.daum.android.map");
    fun searchIntent(keyword: String, callerPackage: String): Intent {
        val uri = when (this) {
            NAVER -> Uri.Builder().scheme("nmap").authority("search").appendQueryParameter("query", keyword)
                .appendQueryParameter("appname", callerPackage).build()
            KAKAO -> Uri.Builder().scheme("kakaomap").authority("search").appendQueryParameter("q", keyword).build()
        }
        return Intent(Intent.ACTION_VIEW, uri).setPackage(packageName).addCategory(Intent.CATEGORY_BROWSABLE)
    }
}
