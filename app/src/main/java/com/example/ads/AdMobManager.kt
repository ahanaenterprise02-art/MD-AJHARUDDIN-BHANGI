package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AdMobManager {
    private const val TAG = "AdMobManager"

    // Official Google AdMob Test IDs (safe for development and testing)
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    private const val INTERSTITIAL_COOLDOWN_MS = 45_000L

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var lastShownTimestampMs = 0L

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    private val _isInterstitialReady = MutableStateFlow(false)
    val isInterstitialReady: StateFlow<Boolean> = _isInterstitialReady

    fun initialize(context: Context) {
        if (_isInitialized.value) return
        val appContext = context.applicationContext
        runCatching {
            MobileAds.initialize(appContext) {
                _isInitialized.value = true
                preloadInterstitial(appContext)
            }
        }.onFailure { err ->
            Log.w(TAG, "MobileAds initialization skipped or failed: ${err.message}")
        }
    }

    fun preloadInterstitial(context: Context) {
        if (interstitialAd != null || isLoadingInterstitial) return
        isLoadingInterstitial = true
        val appContext = context.applicationContext
        runCatching {
            val request = AdRequest.Builder().build()
            InterstitialAd.load(
                appContext,
                TEST_INTERSTITIAL_AD_UNIT_ID,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isLoadingInterstitial = false
                        _isInterstitialReady.value = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.d(TAG, "Test interstitial failed to load: ${error.message}")
                        interstitialAd = null
                        isLoadingInterstitial = false
                        _isInterstitialReady.value = false
                    }
                }
            )
        }.onFailure {
            isLoadingInterstitial = false
            _isInterstitialReady.value = false
        }
    }

    fun showInterstitialIfReady(
        activity: Activity,
        forceIgnoreCooldown: Boolean = false,
        onAdFinished: () -> Unit = {}
    ): Boolean {
        val now = System.currentTimeMillis()
        if (!forceIgnoreCooldown && now - lastShownTimestampMs < INTERSTITIAL_COOLDOWN_MS) {
            onAdFinished()
            return false
        }

        val ad = interstitialAd
        if (ad == null) {
            preloadInterstitial(activity)
            onAdFinished()
            return false
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                _isInterstitialReady.value = false
                preloadInterstitial(activity)
                onAdFinished()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                _isInterstitialReady.value = false
                preloadInterstitial(activity)
                onAdFinished()
            }

            override fun onAdShowedFullScreenContent() {
                lastShownTimestampMs = System.currentTimeMillis()
            }
        }
        ad.show(activity)
        return true
    }
}
