package com.omen.adblokal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Rutinitas inti ADB Lokal:
 *  1) Nyalakan Wireless debugging (bila izin WRITE_SECURE_SETTINGS sudah ada).
 *  2) Sambung ke adbd perangkat sendiri lewat libadb (port ditemukan via mDNS).
 *  3) Beri izin WRITE_SECURE_SETTINGS ke diri sendiri lewat shell (sekali saja,
 *     supaya boot berikutnya langkah 1 bisa jalan walau belum pernah diberi manual).
 *  4) Minta adbd mendengarkan port tetap 5555 (permintaan "tcpip", cadangan setprop).
 *  5) Buktikan 127.0.0.1:5555 menjawab.
 * Semua langkah dicatat lewat [log] dan hasil akhir disimpan ke preferensi "status".
 */
object KunciPort {
    const val PORT = 5555
    private const val PREFS = "status"

    fun portTerbuka(): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", PORT), 900)
            true
        }
    } catch (e: Exception) { false }

    fun wdNyala(ctx: Context): Boolean =
        Settings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", 0) == 1

    fun punyaIzin(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun simpan(ctx: Context, ok: Boolean, pesan: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("ok", ok)
            .putString("pesan", pesan)
            .putLong("waktu", System.currentTimeMillis())
            .apply()
    }

    fun hasilTerakhir(ctx: Context): Triple<Boolean, String, Long> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(p.getBoolean("ok", false), p.getString("pesan", "belum pernah jalan") ?: "", p.getLong("waktu", 0))
    }

    // ---- Saklar penjaga (v12): saklar Kunci Port di layar adalah saklar betulan. ----

    private const val PREFS_SETELAN = "setelan"

    fun penjagaAktif(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS_SETELAN, Context.MODE_PRIVATE)
            .getBoolean("penjaga_aktif", true)

    fun setPenjagaAktif(ctx: Context, aktif: Boolean) {
        ctx.getSharedPreferences(PREFS_SETELAN, Context.MODE_PRIVATE).edit()
            .putBoolean("penjaga_aktif", aktif).apply()
    }

    // ---- Kanal shell untuk panel Debloat (v12) — memakai sambungan libadb yang sama. ----

    /** Uji cepat: apakah kanal shell menjawab? (Mengandaikan sambungan libadb mungkin sudah hidup.) */
    fun ujiKanal(ctx: Context): Boolean =
        shell(ctx, "echo adblokal-ok") { }.contains("adblokal-ok")

    /** Pastikan kanal shell tersambung ke adbd perangkat sendiri; true bila siap dipakai. */
    fun pastikanSambung(ctx: Context, log: (String) -> Unit): Boolean {
        if (ujiKanal(ctx)) return true
        if (punyaIzin(ctx)) {
            try {
                Settings.Global.putInt(ctx.contentResolver, "adb_wifi_enabled", 1)
                log("Wireless debugging dinyalakan untuk kanal shell.")
            } catch (e: Exception) { /* dicoba lagi lewat autoConnect */ }
        }
        val mgr = AdbManager.getInstance(ctx)
        for (usaha in 1..3) {
            val ok = try { mgr.autoConnect(ctx, 10_000) } catch (e: Exception) {
                log("autoConnect kanal ke-$usaha: ${e.message}"); false
            }
            if (ok && ujiKanal(ctx)) return true
            if (usaha < 3) Thread.sleep(3_000)
        }
        return ujiKanal(ctx)
    }

    /**
     * Jalankan satu perintah lewat kanal shell; sukses bila keluaran mengandung [tanda].
     * Panggil dari benang latar — penyambungan bisa memakan beberapa detik.
     */
    fun aksiShell(ctx: Context, perintah: String, tanda: String, log: (String) -> Unit): Boolean {
        if (!pastikanSambung(ctx, log)) {
            log("Kanal shell tidak tersambung — aksi dibatalkan: $perintah")
            return false
        }
        val out = shell(ctx, perintah, log)
        log("shell '$perintah' -> ${out.ifEmpty { "(kosong)" }}")
        return out.contains(tanda)
    }

    /** Jalankan perintah shell lewat sambungan libadb; kembalikan keluaran (boleh kosong). */
    fun shell(ctx: Context, perintah: String, log: (String) -> Unit): String {
        val mgr = AdbManager.getInstance(ctx)
        var stream: io.github.muntashirakon.adb.AdbStream? = null
        return try {
            stream = mgr.openStream("shell:$perintah")
            val input = stream.openInputStream()
            val buf = ByteArray(1024)
            val out = StringBuilder()
            var tunggu = 0
            while (tunggu < 3000) {
                if (input.available() > 0) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.append(String(buf, 0, n))
                } else {
                    Thread.sleep(100); tunggu += 100
                }
            }
            out.toString().trim()
        } catch (e: Exception) {
            log("shell '$perintah' gagal: ${e.message}")
            ""
        } finally {
            try { stream?.close() } catch (e: Exception) { }
        }
    }

    fun jalankan(ctx: Context, log: (String) -> Unit): Boolean {
        if (portTerbuka()) {
            log("Port $PORT sudah terbuka — tidak ada yang perlu dilakukan.")
            simpan(ctx, true, "Port $PORT sudah terbuka")
            return true
        }

        // 1) Nyalakan wireless debugging bila bisa
        if (punyaIzin(ctx)) {
            try {
                Settings.Global.putInt(ctx.contentResolver, "adb_wifi_enabled", 1)
                log("Wireless debugging dinyalakan (izin ada).")
            } catch (e: Exception) {
                log("Gagal menyalakan wireless debugging: ${e.message}")
            }
        } else {
            log("Izin WRITE_SECURE_SETTINGS belum ada — wireless debugging harus sudah nyala dari sesi pemasangan.")
        }

        // 2) Sambung ke adbd sendiri (beri waktu WD + mDNS siap, terutama sesudah boot).
        //    Bila kanal shell SUDAH hidup (mis. dibuka lebih dulu oleh layar utama
        //    v12 untuk panel Debloat), pakai sambungan yang ada itu — autoConnect
        //    kedua di atas sambungan yang hidup selalu gagal.
        val mgr = AdbManager.getInstance(ctx)
        var tersambung = false
        if (shell(ctx, "echo kunci-ok", log).contains("kunci-ok")) {
            tersambung = true
            log("Kanal shell sudah hidup — memakai sambungan yang ada.")
        }
        for (usaha in 1..6) {
            if (tersambung) break
            tersambung = try { mgr.autoConnect(ctx, 12_000) } catch (e: Exception) {
                log("autoConnect ke-$usaha melempar: ${e.message}"); false
            }
            if (tersambung) break
            log("Belum tersambung (usaha $usaha/6) — tunggu 10 detik…")
            Thread.sleep(10_000)
        }
        if (!tersambung) {
            log("GAGAL: tidak bisa menyambung ke ADB lokal. Pastikan wireless debugging nyala dan aplikasi sudah dipasangkan.")
            simpan(ctx, false, "Tidak tersambung ke ADB lokal (WD mati / belum pairing?)")
            return false
        }
        log("Tersambung ke ADB lokal.")

        // 3) Self-grant supaya boot berikutnya bisa menyalakan WD sendiri
        if (!punyaIzin(ctx)) {
            val r = shell(ctx, "pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS", log)
            log("Self-grant WRITE_SECURE_SETTINGS: ${if (r.isEmpty()) "(tanpa keluaran)" else r} — izin kini: ${punyaIzin(ctx)}")
        }

        // 4a) Minta adbd pindah ke port tetap lewat permintaan tcpip
        try {
            val s = mgr.openStream("tcpip:$PORT")
            try {
                val input = s.openInputStream()
                val buf = ByteArray(256)
                var tunggu = 0
                while (tunggu < 2500) {
                    if (input.available() > 0) { if (input.read(buf) <= 0) break }
                    else { Thread.sleep(100); tunggu += 100 }
                }
            } catch (e: Exception) { /* adbd restart di tengah bacaan itu wajar */ }
            try { s.close() } catch (e: Exception) { }
            log("Permintaan tcpip:$PORT terkirim.")
        } catch (e: Exception) {
            log("Permintaan tcpip melempar: ${e.message}")
        }
        for (i in 1..10) {
            Thread.sleep(1_000)
            if (portTerbuka()) {
                log("BERHASIL: port $PORT terbuka (jalur tcpip).")
                simpan(ctx, true, "Port $PORT dikunci lewat tcpip")
                return true
            }
        }

        // 4b) Cadangan: setprop lewat shell (butuh sambungan baru karena adbd mungkin restart)
        log("Jalur tcpip belum membuka port — coba setprop lewat shell…")
        try { mgr.autoConnect(ctx, 10_000) } catch (e: Exception) { }
        val rp = shell(ctx, "setprop service.adb.tcp.port $PORT", log)
        if (rp.isNotEmpty()) log("setprop: $rp")
        for (i in 1..10) {
            Thread.sleep(1_000)
            if (portTerbuka()) {
                log("BERHASIL: port $PORT terbuka (jalur setprop).")
                simpan(ctx, true, "Port $PORT dikunci lewat setprop")
                return true
            }
        }

        log("GAGAL: port $PORT tetap tertutup sesudah semua jalur dicoba.")
        simpan(ctx, false, "Semua jalur dicoba, port $PORT tetap tertutup")
        return false
    }
}
