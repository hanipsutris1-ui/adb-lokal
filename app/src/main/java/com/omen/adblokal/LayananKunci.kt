package com.omen.adblokal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Layanan latar depan yang menjalankan rutinitas KunciPort sesudah boot
 * (atau saat diminta dari layar utama), dengan pengulangan sabar:
 * HU yang baru menyala mungkin belum tersambung Wi-Fi, jadi kegagalan awal
 * dicoba lagi tiap menit sampai ~12 kali sebelum menyerah sampai boot berikut.
 */
class LayananKunci : Service() {

    companion object {
        private const val CHANNEL = "jaga"
        private val jalan = AtomicBoolean(false)

        fun mulai(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, LayananKunci::class.java))
            } catch (e: Exception) {
                // Sebagian ROM membatasi start dari latar; tombol di layar utama jadi cadangan.
            }
        }

        fun catat(ctx: Context, baris: String) {
            try {
                val f = File(ctx.filesDir, "log.txt")
                val capWaktu = SimpleDateFormat("dd-MM HH:mm:ss", Locale.US)
                    .format(Date())
                f.appendText("$capWaktu  $baris\n")
                if (f.length() > 32_000) {
                    val lines = f.readLines()
                    f.writeText(lines.takeLast(200).joinToString("\n") + "\n")
                }
            } catch (e: Exception) { }
        }

        fun bacaLog(ctx: Context): String = try {
            val f = File(ctx.filesDir, "log.txt")
            if (f.exists()) f.readLines().takeLast(60).joinToString("\n") else "(belum ada catatan)"
        } catch (e: Exception) { "(gagal membaca catatan)" }
    }

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Penjaga port ADB", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(1, notif("Menjaga port ${KunciPort.PORT}…"))
    }

    private fun notif(teks: String): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ADB Lokal")
            .setContentText(teks)
            .setOngoing(true)
            .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!jalan.compareAndSet(false, true)) return START_NOT_STICKY
        executor.execute {
            try {
                var ok = false
                var putaran = 0
                while (!ok && putaran < 12) {
                    putaran++
                    if (putaran > 1) {
                        catat(this, "— pengulangan ke-$putaran —")
                        getSystemService(NotificationManager::class.java)
                            .notify(1, notif("Percobaan ke-$putaran membuka port ${KunciPort.PORT}…"))
                    }
                    ok = KunciPort.jalankan(this) { baris -> catat(this, baris) }
                    if (!ok && putaran < 12) Thread.sleep(60_000)
                }
                getSystemService(NotificationManager::class.java)
                    .notify(1, notif(if (ok) "Port ${KunciPort.PORT} terbuka ✓" else "Belum berhasil — coba lagi dari aplikasi"))
            } catch (e: Exception) {
                catat(this, "Layanan berhenti karena: ${e.message}")
            } finally {
                jalan.set(false)
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
