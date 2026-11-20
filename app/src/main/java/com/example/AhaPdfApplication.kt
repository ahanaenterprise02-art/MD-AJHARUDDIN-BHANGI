package com.example

import android.app.Application
import com.example.ads.AdMobManager
import com.example.data.db.AhaPdfDatabase
import com.example.data.db.PdfRepository
import com.example.data.prefs.SettingsRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AhaPdfApplication : Application() {
    lateinit var database: AhaPdfDatabase
        private set

    lateinit var pdfRepository: PdfRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        database = AhaPdfDatabase.getInstance(this)
        pdfRepository = PdfRepository(this, database.pdfRecordDao())
        settingsRepository = SettingsRepository(this)
        AdMobManager.initialize(this)
    }
}
