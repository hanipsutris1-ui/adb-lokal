package com.omen.adblokal

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layar utama ADB Lokal: ringkasan keadaan, tombol jalankan manual,
 * dan formulir pemasangan (pairing) sekali waktu saat pemasangan awal.
 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var portField: EditText
    private lateinit var kodeField: EditText

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        fun teks(isi: String, ukuran: Float = 15f, tebal: Boolean = false) = TextView(this).apply {
            text = isi; textSize = ukuran
            if (tebal) setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        fun tombol(isi: String, aksi: (View) -> Unit) = Button(this).apply {
            text = isi; setOnClickListener(aksi)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        fun kolom(angka: Boolean = true) = EditText(this).apply {
            inputType = if (angka) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        root.addView(teks("ADB Lokal", 22f, true))
        root.addView(teks("Menjaga ADB head unit tetap bisa dijangkau di port tetap ${KunciPort.PORT}: " +
            "tiap HU selesai menyala, aplikasi ini menyalakan wireless debugging, menyambung ke ADB lokal, " +
            "dan mengunci portnya — tanpa perlu utak-atik Opsi pengembang lagi."))

        statusView = teks("Memeriksa keadaan…", 15f, true)
        root.addView(statusView)

        root.addView(tombol("Jalankan sekarang — nyalakan & kunci port") {
            LayananKunci.catat(this, "Dijalankan manual dari layar utama.")
            LayananKunci.mulai(this)
            Toast.makeText(this, "Layanan penjaga dimulai — lihat catatan di bawah.", Toast.LENGTH_SHORT).show()
            segarkanSesudah(2500)
        })
        root.addView(tombol("Segarkan status") { segarkan() })

        root.addView(teks("Pemasangan (sekali saja, saat awal)", 17f, true))
        root.addView(teks("1) Buka Opsi pengembang → Wireless debugging → \"Pasangkan perangkat dengan kode pemasangan\". " +
            "Layar itu menampilkan kode 6 digit dan alamat IP:port — angka sesudah titik dua adalah port pemasangan. " +
            "2) Isi keduanya di sini selagi dialog itu masih terbuka, lalu tekan Pasangkan. " +
            "3) Sesudah berhasil, aplikasi memberi izinnya sendiri dan langsung mengunci port."))
        root.addView(tombol("Buka Opsi pengembang") {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            catch (e: Exception) { Toast.makeText(this, "Tidak bisa membuka: ${e.message}", Toast.LENGTH_LONG).show() }
        })
        portField = kolom().apply { hint = "Port pemasangan (mis. 37123)" }
        kodeField = kolom().apply { hint = "Kode pemasangan 6 digit" }
        root.addView(portField)
        root.addView(kodeField)
        root.addView(tombol("Pasangkan") { pasangkan() })

        root.addView(teks("Catatan kerja", 17f, true))
        logView = teks("(memuat…)", 12f)
        root.addView(logView)

        setContentView(ScrollView(this).apply { addView(root) })
        segarkan()
    }

    override fun onResume() {
        super.onResume()
        segarkan()
    }

    private fun segarkanSesudah(jedaMs: Long) {
        Thread { Thread.sleep(jedaMs); runOnUiThread { segarkan() } }.start()
    }

    private fun segarkan() {
        Thread {
            val izin = KunciPort.punyaIzin(this)
            val wd = KunciPort.wdNyala(this)
            val port = KunciPort.portTerbuka()
            val (ok, pesan, waktu) = KunciPort.hasilTerakhir(this)
            val capWaktu = if (waktu > 0) SimpleDateFormat("dd-MM HH:mm", Locale.US).format(Date(waktu)) else "—"
            val ringkas = buildString {
                appendLine("Izin khusus (WRITE_SECURE_SETTINGS): ${if (izin) "ADA ✓" else "BELUM ADA"}")
                appendLine("Wireless debugging: ${if (wd) "NYALA" else "mati"}")
                appendLine("Port ${KunciPort.PORT}: ${if (port) "TERBUKA ✓" else "tertutup"}")
                append("Hasil terakhir ($capWaktu): ${if (ok) "BERHASIL" else "—"} · $pesan")
            }
            val log = LayananKunci.bacaLog(this)
            runOnUiThread {
                statusView.text = ringkas
                logView.text = log
            }
        }.start()
    }

    private fun pasangkan() {
        val port = portField.text.toString().trim().toIntOrNull()
        val kode = kodeField.text.toString().trim()
        if (port == null || kode.length != 6) {
            Toast.makeText(this, "Isi port pemasangan (angka) dan kode 6 digit dulu.", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Memasangkan…", Toast.LENGTH_SHORT).show()
        Thread {
            val hasil = try {
                AdbManager.getInstance(this).pair("127.0.0.1", port, kode)
            } catch (e: Exception) {
                LayananKunci.catat(this, "Pairing melempar: ${e.message}")
                false
            }
            LayananKunci.catat(this, "Pairing ke port $port: ${if (hasil) "BERHASIL ✓" else "GAGAL"}")
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (hasil) "Terpasang ✓ — layanan penjaga dimulai, port akan dikunci." else "Pairing gagal — periksa kode & port, pastikan dialog pemasangan masih terbuka.",
                    Toast.LENGTH_LONG
                ).show()
                if (hasil) LayananKunci.mulai(this)
                segarkanSesudah(1500)
            }
        }.start()
    }
}
