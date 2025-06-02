package com.currency.converter

import android.app.Application
import com.currency.converter.utils.AppUtils
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CurrencyConversionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppUtils.setApplication(this)
    }
}