package com.jm.reader.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catches any uncaught exception (crash) and writes a readable report to
 * [crashFile] so the next app launch can show it to the user with a copy button.
 * The default handler is still invoked afterwards (the process dies normally).
 */
class CrashHandler(context: Context) : Thread.UncaughtExceptionHandler {

    private val appContext: Context = context.applicationContext
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val pkg = runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0) }.getOrNull()
            val sb = StringBuilder()
            sb.append("========== JMReader Crash Report ==========\n")
            sb.append("Time:    ").append(time).append('\n')
            sb.append("App:     v").append(pkg?.versionName ?: "?").append(" (").append(pkg?.versionCode ?: "?").append(")\n")
            sb.append("Device:  ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            sb.append("Thread:  ").append(thread.name).append('\n')
            sb.append("------------------------------------------\n")
            sb.append(Log.getStackTraceString(throwable))
            File(appContext.filesDir, CRASH_FILE).writeText(sb.toString())
        } catch (_: Exception) {
            // never throw from a crash handler
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        const val CRASH_FILE = "crash_log.txt"
    }
}

/** Reads/clears the persisted crash report. */
object CrashReportManager {
    fun readCrash(context: Context): String? {
        val f = File(context.applicationContext.filesDir, CrashHandler.CRASH_FILE)
        return if (f.exists()) f.readText().trim().takeIf { it.isNotEmpty() } else null
    }

    fun clearCrash(context: Context) {
        runCatching { File(context.applicationContext.filesDir, CrashHandler.CRASH_FILE).delete() }
    }
}
