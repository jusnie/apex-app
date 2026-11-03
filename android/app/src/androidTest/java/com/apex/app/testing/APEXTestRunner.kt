package com.apex.app.testing

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.runner.AndroidJUnitRunner
import com.apex.app.BuildConfig
import com.apex.app.APEXApp
import java.util.Locale

/** Lives in the test APK only; normal Debug and Release launches never use it. */
class APEXTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        requireTestTarget(requireNotNull(context))
        return super.newApplication(cl, APEXTestApplication::class.java.name, context)
    }
}

class APEXTestApplication : APEXApp() {
    override val backgroundServicesEnabled = false

    override fun onCreate() {
        requireTestTarget(this)
        Locale.setDefault(Locale.US)
        super.onCreate()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en-US"))
    }
}

internal fun requireTestTarget(context: Context) {
    check(BuildConfig.DEBUG && context.packageName == "com.apex.app.debug") {
        "Instrumentation must target the dedicated APEX Debug package."
    }
    check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" ||
        Build.FINGERPRINT.startsWith("generic") || Build.MODEL.startsWith("sdk_gphone")) {
        "Run this isolated suite on an Android emulator, not a personal phone."
    }
}
