package com.currency.converter.utils

import android.app.Application

object AppUtils {
    private var application: Application? = null

    fun setApplication(application: Application) {
        this.application = application
    }

    fun getString(resId: Int, vararg formatArgs: Any?): String? {
        return application?.getString(resId, *formatArgs)
    }

    fun getString(resId: Int): String? {
        return application?.getString(resId)
    }
}