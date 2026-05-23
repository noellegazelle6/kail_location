package com.kail.location.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd

object NativeAdLoader {

    private const val AD_UNIT_ID = "ca-app-pub-3992562752831504/5055978607"
    private var cachedAd: NativeAd? = null
    private var isLoading = false

    fun loadAd(context: Context, onResult: (NativeAd?) -> Unit = {}) {
        if (cachedAd != null) {
            onResult(cachedAd)
            return
        }
        if (isLoading) return
        isLoading = true
        val builder = AdLoader.Builder(context, AD_UNIT_ID)
        builder.forNativeAd { nativeAd ->
            cachedAd?.destroy()
            cachedAd = nativeAd
            isLoading = false
            onResult(nativeAd)
        }
        builder.withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                isLoading = false
                onResult(null)
            }
        })
        val adLoader = builder.build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    fun getCachedAd(): NativeAd? = cachedAd

    fun clearCache() {
        cachedAd?.destroy()
        cachedAd = null
    }
}
