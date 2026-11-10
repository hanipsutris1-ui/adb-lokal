package com.omen.adblokal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Setiap HU selesai boot, minta LayananKunci mengunci lagi port 5555. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                if (!KunciPort.penjagaAktif(context)) {
                    LayananKunci.catat(context, "Boot terdeteksi — penjaga NONAKTIF (saklar), port tidak dikunci.")
                    return
                }
                LayananKunci.catat(context, "Boot terdeteksi — layanan penjaga dimulai.")
                LayananKunci.mulai(context)
            }
        }
    }
}
