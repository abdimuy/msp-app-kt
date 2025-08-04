package com.example.msp_app.core.utils

import android.content.Context
import android.os.Build

fun getCurrentVersionCode(context: Context): Int {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode.toInt()
    } else {
        packageInfo.versionCode
    }
}
