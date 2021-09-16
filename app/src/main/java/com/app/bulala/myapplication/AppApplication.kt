package com.app.bulala.myapplication

import android.app.Application
import com.bulala.qrcode.QRApplication

class AppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        QRApplication.initQRCode(this)
    }
}