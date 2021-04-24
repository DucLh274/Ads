package com.volio.ads.admob.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.volio.ads.AdCallback
import com.volio.ads.model.AdsChild
import com.volio.ads.utils.AdDef
import com.volio.ads.utils.AdDialog
import com.volio.ads.utils.Constant
import com.volio.ads.utils.Utils
import java.util.*

class AdmobInterstitial : AdmobAds() {
    private var error: String? = null

    private var loadFailed = false
    private var loaded: Boolean = false
    private var preload: Boolean = false

    private var eventLifecycle: Lifecycle.Event = Lifecycle.Event.ON_RESUME
    private var mInterstitialAd: InterstitialAd? = null
    private var timeClick = 0L
    private var callback: AdCallback? = null
    private val TAG = "AdmobInterstitial"
    private var currentActivity: Activity? = null
    private fun resetValue() {
        loaded = false
        loadFailed = false
        error = null
    }

    override fun loadAndShow(
        activity: Activity,
        adsChild: AdsChild,
        textLoading: String?,
        layout: ViewGroup?,
        layoutAds: View?,
        lifecycle: Lifecycle?,
        timeMillisecond: Long?,
        adCallback: AdCallback?
    ) {
        currentActivity = activity
        callback = adCallback
        preload = false
        load(
            activity,
            adsChild,
            textLoading,
            lifecycle,
            timeMillisecond ?: Constant.TIME_OUT_DEFAULT,
            adCallback
        )
    }

    override fun preload(activity: Activity, adsChild: AdsChild) {
        preload = true
        currentActivity = activity
        load(activity, adsChild, null, null, Constant.TIME_OUT_DEFAULT, null)
    }

    override fun show(
        activity: Activity,
        adsChild: AdsChild,
        loadingText: String?,
        layout: ViewGroup?,
        layoutAds: View?,
        adCallback: AdCallback?
    ): Boolean {
        callback = adCallback
        currentActivity = activity
        AdDialog.getInstance().showLoadingWithMessage(activity, loadingText)
        if (loaded && mInterstitialAd != null) {
            mInterstitialAd?.show(activity)
            return true
        }
//        else {
//            adCallback?.onAdFailToLoad(error)
//        }
        return false;
    }

    private fun load(
        activity: Activity,
        adsChild: AdsChild,
        textLoading: String?,
        lifecycle: Lifecycle?,
        timeOut: Long,
        adCallback: AdCallback?
    ) {
        if (System.currentTimeMillis() - timeClick < 500) return
        textLoading?.let {
            AdDialog.getInstance().showLoadingWithMessage(activity, textLoading)
        }
        Log.d(TAG, "load: " + textLoading)
        Utils.showToastDebug(activity, "Admob Interstitial id: ${adsChild.adsId}")

        if (!preload) {
            lifecycle?.addObserver(lifecycleObserver)
        }
        resetValue()
        callback = adCallback
        timeClick = System.currentTimeMillis();
        val id = if (Constant.isDebug) Constant.ID_ADMOB_INTERSTITIAL_TEST else adsChild.adsId
        val interstitialAdLoadCallback = object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(p0: InterstitialAd) {
                Log.d(TAG, "onAdLoaded: ")
                mInterstitialAd = p0
                mInterstitialAd?.fullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            super.onAdDismissedFullScreenContent()
                            Log.d(TAG, "onAdDismissedFullScreenContent: ")
                            callback?.onAdClose(AdDef.ADS_TYPE.INTERSTITIAL)
                            mInterstitialAd = null

                            //// perform your code that you wants to do after ad dismissed or closed
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            super.onAdFailedToShowFullScreenContent(adError)
                            Log.d(TAG, "onAdFailedToShowFullScreenContent: ")
                            mInterstitialAd = null
                            loadFailed = true
                            error = adError.message
                            if (eventLifecycle == Lifecycle.Event.ON_RESUME && !preload) {
                                AdDialog.getInstance().hideLoading()
                                callback?.onAdFailToLoad(adError.message)
                                lifecycle?.removeObserver(lifecycleObserver)
                            }
                            /// perform your action here when ad will not load
                        }

                        override fun onAdShowedFullScreenContent() {
                            super.onAdShowedFullScreenContent()
                            Log.d(TAG, "onAdShowedFullScreenContent: ")
                            mInterstitialAd = null
                            AdDialog.getInstance().hideLoading()
                            Utils.showToastDebug(
                                activity,
                                "Admob Interstitial id: ${adsChild.adsId}"
                            )
                            callback?.onAdShow(
                                AdDef.NETWORK.GOOGLE,
                                AdDef.ADS_TYPE.INTERSTITIAL
                            )
                        }
                    }
                if (eventLifecycle == Lifecycle.Event.ON_RESUME && !preload) {
                    Handler(Looper.getMainLooper()).postDelayed(Runnable {
                        AdDialog.getInstance().hideLoading()
                    }, 500)
                    currentActivity?.let { mInterstitialAd?.show(it) }
                    lifecycle?.removeObserver(lifecycleObserver)
                }
                loaded = true
                timeLoader = Date().time
                Log.d(TAG, "onAdLoaded: ")
            }

            override fun onAdFailedToLoad(p0: LoadAdError) {
                super.onAdFailedToLoad(p0)
                loadFailed = true
                error = p0.message
                if (eventLifecycle == Lifecycle.Event.ON_RESUME && !preload) {
                    AdDialog.getInstance().hideLoading()
                    callback?.onAdFailToLoad(p0.message)
                    lifecycle?.removeObserver(lifecycleObserver)
                }
            }
        }
        InterstitialAd.load(
            activity,
            id,
            AdRequest.Builder().setHttpTimeoutMillis(timeOut.toInt()).build(),
            interstitialAdLoadCallback
        )
    }

    private val lifecycleObserver = LifecycleEventObserver { source, event ->
        eventLifecycle = event
        if (event == Lifecycle.Event.ON_RESUME) {
            AdDialog.getInstance().hideLoading()
            if (loadFailed || loaded) {
                AdDialog.getInstance().hideLoading()
                if (loaded) {
                    currentActivity?.let { mInterstitialAd?.show(it) }
                    Log.d(TAG, "show: ")
                } else {
                    callback?.onAdFailToLoad(error)
                    Log.d(TAG, "faild: ")
                }
            }
        }

    }

    override fun isLoaded(): Boolean {
        return loaded
    }

    override fun isDestroy(): Boolean {
        return mInterstitialAd == null
    }

    override fun destroy() {
        mInterstitialAd = null
    }

}