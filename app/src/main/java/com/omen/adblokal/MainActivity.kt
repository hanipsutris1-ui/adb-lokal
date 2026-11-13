package com.omen.adblokal

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layar utama ADB Lokal.
 *
 * Susunan mengikuti pratinjau final yang disetujui pemilik (artefak rancangan,
 * berkas "Pratinjau HU v2"): empat tab — Status, Sistem, Dipulihkan,
 * Aplikasi saya — bergaya tvOS (gradasi gelap + kartu kaca) dengan saklar gaya
 * Launcher Luxio. Tab Sistem berbentuk akordeon dua panel (Saran debloat /
 * Aplikasi sistem lainnya): membuka satu panel menutup panel lainnya.
 *
 * Mesinnya TIDAK berubah dari v11–v16: KunciPort/LayananKunci/BootReceiver/
 * AdbManager/DataPaket/AdapterPaket persis sama — yang ditulis ulang hanya
 * susunan tampilannya. Semua daftar & status dibaca langsung dari perangkat
 * yang menjalankan aplikasi, dan perlindungan paket dihitung per perangkat.
 */
class MainActivity : Activity() {

    private enum class Segmen { SARAN, SEMUA, NONAKTIF }

    // ---- keadaan ----
    @Volatile private var kanalOk = false
    private var uiSiap = false
    private var segmen = Segmen.SARAN
    private var panelBuka = 1 // akordeon Sistem: 1 = Saran, 2 = Sistem lainnya

    // ---- tampilan ----
    private lateinit var pilPort: TextView
    private lateinit var pilShell: TextView
    private lateinit var tabTeks: List<TextView>
    private lateinit var tabPulihTeks: TextView
    private lateinit var panelStatus: View
    private lateinit var panelSistem: View
    private lateinit var panelUnduhan: View
    private lateinit var panelPulih: View
    private lateinit var heroJudul: TextView
    private lateinit var metrikIzin: TextView
    private lateinit var metrikWd: TextView
    private lateinit var metrikKanal: TextView
    private lateinit var metrikHasil: TextView
    private lateinit var teksHasil: TextView
    private lateinit var saklarPenjaga: Switch
    private lateinit var tombolBulk: Button
    private lateinit var teksSaranAktif: TextView
    private lateinit var kepalaPanel1: TextView
    private lateinit var kepalaPanel2: TextView
    private lateinit var badanPanel1: View
    private lateinit var badanPanel2: View
    private lateinit var daftarSistemView: ListView
    private var jumlahSistemTerbaru = 0
    private var jumlahSaranTerbaru = 0
    private var saranTerbaru: List<DataPaket.Info> = emptyList()
    private lateinit var logView: TextView
    private lateinit var isiLipat: View
    private lateinit var judulLipat: TextView
    private lateinit var portField: EditText
    private lateinit var kodeField: EditText
    private lateinit var segmenTeks: List<TextView>
    private lateinit var cariField: EditText
    private lateinit var adapterSaran: AdapterPaket
    private lateinit var adapterSistem: AdapterPaket
    private lateinit var adapterUnduhan: AdapterPaket
    private lateinit var adapterPulih: AdapterPaket

    private fun dp(v: Int): Int = Gaya.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buatUi()
        uiSiap = true
        muatSemua(probeKanal = true)
    }

    override fun onResume() {
        super.onResume()
        if (uiSiap) muatSemua(probeKanal = false)
    }

    // ================= Pembangun tampilan kecil =================

    private fun teks(isi: String, sp: Float, warna: Int, tebal: Boolean = false): TextView =
        TextView(this).apply {
            text = isi
            textSize = sp
            setTextColor(warna)
            if (tebal) setTypeface(typeface, Typeface.BOLD)
        }

    private fun tombol(isi: String, aksi: () -> Unit): Button = Button(this).apply {
        text = isi
        textSize = 14.5f
        isAllCaps = false
        setTextColor(Gaya.TINTA)
        background = Gaya.kaca(this@MainActivity, 14)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { aksi() }
    }

    /** Tombol utama biru aksen (Jalankan sekarang di pratinjau). */
    private fun tombolUtama(isi: String, aksi: () -> Unit): Button = Button(this).apply {
        text = isi
        textSize = 14.5f
        isAllCaps = false
        setTextColor(Color.rgb(0x0B, 0x16, 0x20))
        background = Gaya.kaca(this@MainActivity, 14).apply { setColor(Gaya.AKSEN) }
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { aksi() }
    }

    private fun pil(isi: String): TextView = TextView(this).apply {
        text = isi
        textSize = 13f
        setTextColor(Gaya.TINTA)
        background = Gaya.pil(this@MainActivity)
        setPadding(dp(13), dp(6), dp(13), dp(6))
    }

    /** Pil status selalu membaca keadaan sebenarnya; buruk = titik kuning. */
    private fun aturPil(tv: TextView, label: String, baik: Boolean) {
        val s = SpannableString("● $label")
        s.setSpan(
            ForegroundColorSpan(if (baik) Gaya.HIJAU else Gaya.KUNING),
            0, 1, 0
        )
        tv.text = s
    }

    private fun kartu(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Gaya.kaca(this@MainActivity, 20)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, 0, dp(14))
        layoutParams = lp
    }

    /** Kotak metrik kecil di kartu hero (label di atas, nilai tebal di bawah). */
    private fun kotakMetrik(label: String): TextView {
        val wadah = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Gaya.kaca(this@MainActivity, 16)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
        }
        wadah.addView(teks(label, 11.5f, Gaya.REDA))
        val nilai = teks("…", 16f, Gaya.TINTA, tebal = true)
        wadah.addView(nilai)
        // Kembalikan TextView nilai; wadahnya ditambahkan pemanggil lewat tag.
        nilai.tag = wadah
        return nilai
    }

    /** Saklar gaya Luxio yang berdiri sendiri (dipakai kartu Kunci Port). */
    private fun buatSaklar(awalNyala: Boolean, aksi: (Boolean) -> Unit): Switch =
        Switch(this).apply {
            trackDrawable = getDrawable(R.drawable.sw_track)
            thumbDrawable = getDrawable(R.drawable.sw_thumb)
            setSwitchMinWidth(dp(54))
            thumbTextPadding = 0
            text = ""
            showText = false
            isChecked = awalNyala
            setOnCheckedChangeListener { _, nyala -> aksi(nyala) }
        }

    /** Satu baris saklar gaya Luxio di dalam kartu (untuk Kunci Port). */
    private fun barisSaklarKartu(
        judul: String, kecil: String, awalNyala: Boolean, aksi: (Boolean) -> Unit
    ): LinearLayout {
        val baris = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(72)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = Gaya.barisSaklar(this@MainActivity, awalNyala)
        }
        val kolom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        kolom.addView(teks(judul, 17f, Gaya.TINTA, tebal = true))
        kolom.addView(teks(kecil, 13.5f, Gaya.REDA))
        baris.addView(kolom)
        val sw = buatSaklar(awalNyala) { nyala ->
            baris.background = Gaya.barisSaklar(this@MainActivity, nyala)
            aksi(nyala)
        }
        saklarPenjaga = sw
        baris.addView(sw)
        return baris
    }

    // ================= Susunan halaman & panel =================

    private fun buatUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Gaya.latarHalaman()
            setPadding(dp(24), dp(18), dp(24), dp(14))
        }

        // Kepala — tetap di semua tab.
        root.addView(teks("ADB Lokal", 30f, Gaya.TINTA, tebal = true))
        root.addView(teks("Penjaga ADB head unit", 14f, Gaya.KEDUA).apply {
            val lp = layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            lp.setMargins(0, dp(2), 0, dp(12))
            layoutParams = lp
        })
        val barisPil = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dp(14))
            layoutParams = lp
        }
        pilPort = pil("● Port ${KunciPort.PORT} · …")
        pilShell = pil("● Shell · …")
        barisPil.addView(pilPort)
        barisPil.addView(pilShell)
        (pilShell.layoutParams as LinearLayout.LayoutParams).setMargins(dp(10), 0, 0, 0)
        root.addView(barisPil)

        // Bar tab kaca model segmented — empat tab.
        val namaTab = listOf("Status", "Sistem", "Dipulihkan", "Aplikasi saya")
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Gaya.kaca(this@MainActivity, 999)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dp(14))
            layoutParams = lp
        }
        tabTeks = namaTab.mapIndexed { i, nama ->
            TextView(this).apply {
                text = nama
                textSize = 15.5f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(9), dp(6), dp(9))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                isClickable = true
                isFocusable = true
                setOnClickListener { bukaTab(i) }
            }
        }
        tabTeks.forEach { tabBar.addView(it) }
        tabPulihTeks = tabTeks[2]
        root.addView(tabBar)

        // Wadah panel.
        val wadah = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        panelStatus = bangunPanelStatus()
        panelSistem = bangunPanelSistem()
        panelPulih = bangunPanelPulih()
        panelUnduhan = bangunPanelUnduhan()
        listOf(panelStatus, panelSistem, panelPulih, panelUnduhan)
            .forEach { wadah.addView(it) }
        root.addView(wadah)

        setContentView(root)
        val tabAwal = getSharedPreferences("setelan", MODE_PRIVATE)
            .getInt("tab_terakhir", 0).coerceIn(0, 3)
        bukaTab(tabAwal)
    }

    private fun bukaTab(i: Int) {
        val panel = listOf(panelStatus, panelSistem, panelPulih, panelUnduhan)[i]
        tampilkanPanel(panel, i)
    }

    private fun tampilkanPanel(panel: View, tabAktif: Int) {
        getSharedPreferences("setelan", MODE_PRIVATE).edit()
            .putInt("tab_terakhir", tabAktif).apply()
        listOf(panelStatus, panelSistem, panelPulih, panelUnduhan)
            .forEach { it.visibility = if (it === panel) View.VISIBLE else View.GONE }
        tabTeks.forEachIndexed { i, tv ->
            if (i == tabAktif) {
                tv.background = Gaya.kaca(this, 999).apply { setColor(Color.rgb(0xE8, 0xE8, 0xEA)) }
                tv.setTextColor(Color.rgb(0x1C, 0x1C, 0x1E))
                tv.setTypeface(tv.typeface, Typeface.BOLD)
            } else {
                tv.background = null
                tv.setTextColor(Gaya.KEDUA)
                tv.setTypeface(tv.typeface, Typeface.NORMAL)
            }
        }
    }

    private fun gulir(isi: LinearLayout): ScrollView = ScrollView(this).apply { addView(isi) }

    private fun bangunPanelStatus(): View {
        // Dua kolom seperti pratinjau: kiri hero + kunci port, kanan catatan +
        // pemasangan + catatan perlindungan. Seluruhnya di dalam gulir.
        val kolom2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(8))
        }
        val kiri = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
                .apply { setMargins(0, 0, dp(11), 0) }
        }
        val kanan = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(11), 0, 0, 0) }
        }
        kolom2.addView(kiri)
        kolom2.addView(kanan)

        // --- Hero keadaan sekarang ---
        val hero = kartu()
        hero.addView(teks("KEADAAN SEKARANG", 12f, Gaya.REDA, tebal = true))
        heroJudul = teks("Memeriksa keadaan…", 28f, Gaya.TINTA, tebal = true).apply {
            setPadding(0, dp(8), 0, dp(8))
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        hero.addView(heroJudul)
        hero.addView(teks(
            "Penjaga mengunci port setiap kali head unit selesai booting, jadi " +
                "perangkat bisa dijangkau dari jaringan tanpa membuka Opsi pengembang.",
            14f, Gaya.KEDUA
        ).apply { setLineSpacing(dp(3).toFloat(), 1f) })
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        val barisM1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val barisM2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        metrikIzin = kotakMetrik("Izin khusus")
        metrikWd = kotakMetrik("Wireless debugging")
        metrikKanal = kotakMetrik("Kanal shell")
        metrikHasil = kotakMetrik("Hasil terakhir")
        barisM1.addView(metrikIzin.tag as View)
        barisM1.addView(metrikWd.tag as View)
        barisM2.addView(metrikKanal.tag as View)
        barisM2.addView(metrikHasil.tag as View)
        grid.addView(barisM1)
        grid.addView(barisM2)
        hero.addView(grid)
        teksHasil = teks("Hasil terakhir: —", 12.5f, Gaya.KEDUA).apply {
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        hero.addView(teksHasil)
        val barisTombol = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        barisTombol.addView(tombolUtama("Jalankan sekarang") {
            LayananKunci.catat(this, "Dijalankan manual dari layar utama.")
            LayananKunci.mulai(this)
            pesan("Layanan penjaga dimulai — lihat Catatan kerja.")
            Handler(Looper.getMainLooper()).postDelayed({ muatSemua(false) }, 4000)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        barisTombol.addView(tombol("Segarkan status") { muatSemua(true) }.apply {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(10), 0, 0, 0)
            layoutParams = lp
        })
        hero.addView(barisTombol)
        kiri.addView(hero)

        // --- Kartu kunci port ---
        val kartuKunci = kartu()
        kartuKunci.addView(barisSaklarKartu(
            "Kunci port ${KunciPort.PORT}",
            "Dinyalakan otomatis setiap head unit selesai booting",
            KunciPort.penjagaAktif(this)
        ) { nyala -> saklarPenjagaBerubah(nyala) })
        kiri.addView(kartuKunci)

        // --- Catatan kerja (kanan) ---
        val kartuLog = kartu()
        kartuLog.addView(teks("CATATAN KERJA", 12f, Gaya.REDA, tebal = true).apply {
            setPadding(0, 0, 0, dp(6))
        })
        logView = teks("(memuat…)", 12f, Gaya.REDA)
        kartuLog.addView(logView)
        kanan.addView(kartuLog)

        // --- Akordeon: Cara pemasangan awal (kanan) ---
        judulLipat = teks("Cara pemasangan awal  ▸", 16.5f, Gaya.TINTA, tebal = true).apply {
            isClickable = true
            setPadding(dp(2), dp(6), dp(2), dp(10))
            setOnClickListener {
                val buka = isiLipat.visibility != View.VISIBLE
                isiLipat.visibility = if (buka) View.VISIBLE else View.GONE
                text = if (buka) "Cara pemasangan awal  ▾" else "Cara pemasangan awal  ▸"
            }
        }
        kanan.addView(judulLipat)
        val lipat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val kartuPasang = kartu()
        kartuPasang.addView(teks(
            "Sekali saja, saat pemasangan awal di perangkat baru:\n" +
                "1) Nyalakan \"Wireless debugging\" di Pengaturan → Sistem → Opsi pengembang.\n" +
                "2) Ketuk \"Pasangkan perangkat dengan kode pemasangan\" sampai kode 6 digit " +
                "dan alamat IP:port tampil — angka sesudah titik dua adalah port pemasangan.\n" +
                "3) Buka ADB Lokal dalam layar belah, isi port & kode 6 digit itu di bawah " +
                "selagi dialognya masih terbuka, lalu tekan Pasangkan.\n" +
                "4) Selesai — pemasangan cukup sekali; ADB Lokal memberi izinnya sendiri " +
                "dan mengunci port ${KunciPort.PORT} otomatis setiap head unit selesai booting.",
            14f, Gaya.REDA
        ).apply { setLineSpacing(dp(3).toFloat(), 1f) })
        kartuPasang.addView(tombol("Buka Opsi pengembang") {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            catch (e: Exception) { pesan("Tidak bisa membuka: ${e.message}") }
        }.apply {
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dp(10), 0, dp(8))
            layoutParams = lp
        })
        portField = EditText(this).apply {
            hint = "Port pemasangan (mis. 37123)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Gaya.TINTA)
            setHintTextColor(Color.argb(140, 255, 255, 255))
            background = Gaya.kaca(this@MainActivity, 14)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            textSize = 15f
        }
        kodeField = EditText(this).apply {
            hint = "Kode pemasangan 6 digit"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Gaya.TINTA)
            setHintTextColor(Color.argb(140, 255, 255, 255))
            background = Gaya.kaca(this@MainActivity, 14)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            textSize = 15f
        }
        kartuPasang.addView(portField)
        kartuPasang.addView(kodeField.apply {
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dp(8), 0, dp(8))
            layoutParams = lp
        })
        kartuPasang.addView(tombol("Pasangkan") { pasangkan() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })
        lipat.addView(kartuPasang)
        lipat.visibility = if (KunciPort.punyaIzin(this)) View.GONE else View.VISIBLE
        if (lipat.visibility == View.VISIBLE) judulLipat.text = "Cara pemasangan awal  ▾"
        isiLipat = lipat
        kanan.addView(lipat)
        kanan.addView(teks(
            "Terlindungi dan tidak bisa diubah: sistem mobil, Cardoor, Launcher Luxio, " +
                "Tailscale, dan ADB Lokal. Semua daftar & status dibaca langsung dari perangkat ini.",
            12f, Gaya.KEDUA
        ).apply { setPadding(dp(4), 0, dp(4), dp(2)) })
        return gulir(kolom2)
    }

    /** Kepala panel akordeon (judul + jumlah + penanda ▸/▾), bisa diklik. */
    private fun kepalaAkordeon(aksi: () -> Unit): TextView =
        teks("", 16f, Gaya.TINTA, tebal = true).apply {
            background = Gaya.kaca(this@MainActivity, 16)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { aksi() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(6)) }
        }

    private fun bangunPanelSistem(): View {
        val isi = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }
        isi.addView(teks(
            "Saklar menyala = aplikasi aktif. Paket berlabel Dilindungi tidak bisa diubah.",
            13f, Gaya.KEDUA
        ).apply { setPadding(dp(4), 0, dp(4), dp(8)) })

        // Baris cari + segmen Saran/Semua/Nonaktif (pola pratinjau).
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dp(8))
            layoutParams = lp
        }
        cariField = EditText(this).apply {
            hint = "Cari nama aplikasi…"
            setTextColor(Gaya.TINTA)
            setHintTextColor(Color.argb(140, 255, 255, 255))
            background = Gaya.kaca(this@MainActivity, 14)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        bar.addView(cariField)
        val segBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Gaya.kaca(this@MainActivity, 999)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(10), 0, 0, 0) }
        }
        val namaSegmen = listOf("Saran", "Semua", "Nonaktif")
        segmenTeks = namaSegmen.mapIndexed { i, nama ->
            TextView(this).apply {
                text = nama
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener { pilihSegmen(Segmen.values()[i]) }
            }
        }
        segmenTeks.forEach { segBar.addView(it) }
        bar.addView(segBar)
        isi.addView(bar)

        // --- Akordeon panel 1: Saran debloat ---
        kepalaPanel1 = kepalaAkordeon { bukaPanelSistem(1) }
        isi.addView(kepalaPanel1)
        val badan1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val barisSaran = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Gaya.kaca(this@MainActivity, 16).apply {
                setColor(Color.argb(18, 0x5F, 0xB4, 0xFF))
                setStroke(dp(1), Color.argb(77, 0x5F, 0xB4, 0xFF))
            }
            setPadding(dp(16), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(6)) }
        }
        teksSaranAktif = teks("… saran masih aktif", 15f, Gaya.TINTA, tebal = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        barisSaran.addView(teksSaranAktif)
        tombolBulk = tombol("Nonaktifkan semua saran") { konfirmasiBulk() }
        barisSaran.addView(tombolBulk)
        badan1.addView(barisSaran)
        adapterSaran = AdapterPaket(
            this, AdapterPaket.Mode.SISTEM,
            padaSaklar = { info, nyala -> saklarSistemBerubah(info, nyala) },
            padaTombol = { }
        )
        badan1.addView(ListView(this).apply {
            adapter = adapterSaran
            divider = null
            dividerHeight = 0
            setCacheColorHint(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        })
        badanPanel1 = badan1
        isi.addView(badan1.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        })

        // --- Akordeon panel 2: Aplikasi sistem lainnya ---
        kepalaPanel2 = kepalaAkordeon { bukaPanelSistem(2) }
        isi.addView(kepalaPanel2)
        adapterSistem = AdapterPaket(
            this, AdapterPaket.Mode.SISTEM,
            padaSaklar = { info, nyala -> saklarSistemBerubah(info, nyala) },
            padaTombol = { }
        )
        daftarSistemView = ListView(this).apply {
            adapter = adapterSistem
            divider = null
            dividerHeight = 0
            setCacheColorHint(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        badanPanel2 = daftarSistemView
        isi.addView(daftarSistemView)

        cariField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s2: Editable?) {
                terapkanSaring(s2?.toString() ?: "")
            }
            override fun beforeTextChanged(s2: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s2: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        segarkanSegmen()
        segarkanAkordeon()
        return isi
    }

    private fun pilihSegmen(s: Segmen) {
        segmen = s
        if (s == Segmen.SARAN) panelBuka = 1
        segarkanSegmen()
        segarkanAkordeon()
        terapkanSaring(if (::cariField.isInitialized) cariField.text.toString() else "")
    }

    private fun segarkanSegmen() {
        if (!::segmenTeks.isInitialized) return
        segmenTeks.forEachIndexed { i, tv ->
            if (Segmen.values()[i] == segmen) {
                tv.background = Gaya.kaca(this, 999).apply { setColor(Color.rgb(0xE8, 0xE8, 0xEA)) }
                tv.setTextColor(Color.rgb(0x1C, 0x1C, 0x1E))
                tv.setTypeface(tv.typeface, Typeface.BOLD)
            } else {
                tv.background = null
                tv.setTextColor(Gaya.KEDUA)
                tv.setTypeface(tv.typeface, Typeface.NORMAL)
            }
        }
    }

    private fun bukaPanelSistem(n: Int) {
        panelBuka = n
        segarkanAkordeon()
    }

    /** Akordeon ketat: panel yang terbuka badannya tampak, yang lain tertutup. */
    private fun segarkanAkordeon() {
        if (!::badanPanel1.isInitialized) return
        badanPanel1.visibility = if (panelBuka == 1) View.VISIBLE else View.GONE
        badanPanel2.visibility = if (panelBuka == 2) View.VISIBLE else View.GONE
        kepalaPanel1.text = "Saran debloat ($jumlahSaranTerbaru)  " + if (panelBuka == 1) "▾" else "▸"
        kepalaPanel2.text = "Aplikasi sistem lainnya ($jumlahSistemTerbaru)  " + if (panelBuka == 2) "▾" else "▸"
    }

    private fun terapkanSaring(kata: String) {
        val nonaktifSaja = segmen == Segmen.NONAKTIF
        if (::adapterSaran.isInitialized) adapterSaran.saring(kata, nonaktifSaja)
        if (::adapterSistem.isInitialized) adapterSistem.saring(kata, nonaktifSaja)
    }

    private fun bangunPanelPulih(): View {
        val isi = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }
        isi.addView(teks("Aplikasi sistem yang pernah dihapus", 20f, Gaya.TINTA, tebal = true).apply {
            setPadding(dp(4), dp(2), dp(4), dp(4))
        })
        isi.addView(teks(
            "Pulihkan untuk memasangnya lagi tanpa file APK. Daftar ini dibaca dari perangkat.",
            13f, Gaya.KEDUA
        ).apply { setPadding(dp(4), 0, dp(4), dp(8)) })
        adapterPulih = AdapterPaket(
            this, AdapterPaket.Mode.PULIHKAN,
            padaSaklar = { _, _ -> },
            padaTombol = { info -> pulihkanPaket(info) }
        )
        isi.addView(ListView(this).apply {
            adapter = adapterPulih
            divider = null
            dividerHeight = 0
            setCacheColorHint(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        })
        return isi
    }

    private fun bangunPanelUnduhan(): View {
        val isi = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }
        isi.addView(teks("Aplikasi yang Anda pasang", 20f, Gaya.TINTA, tebal = true).apply {
            setPadding(dp(4), dp(2), dp(4), dp(4))
        })
        isi.addView(teks(
            "Hapus untuk mencopot dari HU. Pasang lagi dari toko kalau ingin mengembalikannya.",
            13f, Gaya.KEDUA
        ).apply { setPadding(dp(4), 0, dp(4), dp(8)) })
        adapterUnduhan = AdapterPaket(
            this, AdapterPaket.Mode.UNDUHAN,
            padaSaklar = { _, _ -> },
            padaTombol = { info -> konfirmasiHapus(info) }
        )
        isi.addView(ListView(this).apply {
            adapter = adapterUnduhan
            divider = null
            dividerHeight = 0
            setCacheColorHint(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        })
        return isi
    }

    // ================= Logika =================

    private fun pesan(isi: String) {
        Toast.makeText(this, isi, Toast.LENGTH_LONG).show()
    }

    /** Baca ulang keadaan perangkat + semua daftar, lalu segarkan tampilan. */
    private fun muatSemua(probeKanal: Boolean) {
        Thread {
            val izin = KunciPort.punyaIzin(this)
            val wd = KunciPort.wdNyala(this)
            val port = KunciPort.portTerbuka()
            val (ok, pesanHasil, waktu) = KunciPort.hasilTerakhir(this)
            kanalOk = if (probeKanal) {
                KunciPort.pastikanSambung(this) { LayananKunci.catat(this, it) }
            } else {
                KunciPort.ujiKanal(this)
            }
            val sistem = DataPaket.daftar(this, sistem = true)
            val unduhan = DataPaket.daftar(this, sistem = false)
            val terhapus = DataPaket.daftarTerhapus(this)
            val saran = DataPaket.saranYangAda(sistem)
            val saranPkg = saran.map { it.pkg }.toHashSet()
            val sistemLain = sistem.filter { it.pkg !in saranPkg }
            val capWaktu = if (waktu > 0) {
                SimpleDateFormat("dd-MM HH:mm", Locale.US).format(Date(waktu))
            } else "—"
            val log = LayananKunci.bacaLog(this)
            runOnUiThread {
                if (!uiSiap) return@runOnUiThread
                // Hero + metrik (menggantikan teks ringkas lama).
                val judul = SpannableString(
                    if (port) "ADB terjaga di port ${KunciPort.PORT}" else "Port ${KunciPort.PORT} belum terjaga"
                )
                val dari = judul.indexOf("port")
                if (dari >= 0) {
                    judul.setSpan(
                        ForegroundColorSpan(if (port) Gaya.HIJAU else Gaya.KUNING),
                        dari, judul.length, 0
                    )
                }
                heroJudul.text = judul
                metrikIzin.text = if (izin) "Ada ✓" else "Belum ada"
                metrikWd.text = if (wd) "Nyala" else "Mati"
                metrikKanal.text = if (kanalOk) "Tersambung ✓" else "Belum tersambung"
                metrikHasil.text = if (ok) "Berhasil" else "—"
                teksHasil.text = "Hasil terakhir ($capWaktu): ${if (ok) "BERHASIL" else "—"} · $pesanHasil"
                logView.text = log
                aturPil(pilPort, "Port ${KunciPort.PORT} · ${if (port) "Terbuka" else "Tertutup"}", port)
                aturPil(pilShell, "Shell · ${if (kanalOk) "Tersambung" else "Belum tersambung"}", kanalOk)
                saranTerbaru = saran
                jumlahSaranTerbaru = saran.size
                jumlahSistemTerbaru = sistemLain.size
                val saranAktif = saran.count { !it.nonaktif }
                teksSaranAktif.text =
                    if (saranAktif > 0) "$saranAktif saran masih aktif" else "Semua saran sudah nonaktif"
                tombolBulk.visibility = if (saranAktif > 0) View.VISIBLE else View.GONE
                tabPulihTeks.text =
                    if (terhapus.isEmpty()) "Dipulihkan" else "Dipulihkan (${terhapus.size})"
                segarkanAkordeon()
                adapterSaran.gantiData(
                    if (saran.isEmpty()) {
                        listOf(AdapterPaket.Baris.Judul("Tidak ada saran debloat yang terpasang"))
                    } else {
                        saran.map { AdapterPaket.Baris.Paket(it) }
                    }
                )
                adapterSistem.gantiData(
                    if (sistemLain.isEmpty()) {
                        listOf(AdapterPaket.Baris.Judul("Tidak ada aplikasi sistem lain"))
                    } else {
                        sistemLain.map { AdapterPaket.Baris.Paket(it) }
                    }
                )
                adapterUnduhan.gantiData(
                    if (unduhan.isEmpty()) {
                        listOf(AdapterPaket.Baris.Judul("Tidak ada aplikasi unduhan terpasang"))
                    } else {
                        unduhan.map { AdapterPaket.Baris.Paket(it) }
                    }
                )
                adapterPulih.gantiData(
                    if (terhapus.isEmpty()) {
                        listOf(AdapterPaket.Baris.Judul("Belum ada aplikasi yang di-Remove di perangkat ini"))
                    } else {
                        terhapus.map { AdapterPaket.Baris.Paket(it) }
                    }
                )
            }
        }.start()
    }

    private fun saklarPenjagaBerubah(nyala: Boolean) {
        KunciPort.setPenjagaAktif(this, nyala)
        if (nyala) {
            LayananKunci.catat(this, "Penjaga DIAKTIFKAN dari saklar.")
            LayananKunci.mulai(this)
            pesan("Penjaga aktif — port ${KunciPort.PORT} dikunci tiap boot.")
        } else {
            LayananKunci.catat(this, "Penjaga DIMATIKAN dari saklar.")
            try { stopService(Intent(this, LayananKunci::class.java)) } catch (e: Exception) { }
            pesan("Penjaga mati — sesudah restart berikutnya, port ${KunciPort.PORT} tidak dikunci lagi.")
        }
    }

    private fun saklarSistemBerubah(info: DataPaket.Info, nyala: Boolean) {
        if (info.lindungi != null) return
        if (nyala) {
            // Mengaktifkan lagi selalu aman — langsung jalankan.
            jalankanAksi(
                "pm enable --user 0 ${info.pkg}", "enabled",
                berhasil = "${info.label} diaktifkan lagi.",
                gagal = "Gagal mengaktifkan ${info.label} — periksa kanal shell di tab Status."
            )
        } else {
            AlertDialog.Builder(this)
                .setTitle("Nonaktifkan ${info.label}?")
                .setMessage(
                    "Paket: ${info.pkg}\n" +
                        "Perintah: pm disable-user --user 0 ${info.pkg}\n\n" +
                        "Aplikasi berhenti bekerja sampai diaktifkan lagi dari tab ini. " +
                        "Fungsi mobil tidak terpengaruh — paket sistem mobil dilindungi " +
                        "dan tidak bisa diubah."
                )
                .setNegativeButton("Batal", null)
                .setPositiveButton("Nonaktifkan") { _, _ ->
                    jalankanAksi(
                        "pm disable-user --user 0 ${info.pkg}", "disabled",
                        berhasil = "${info.label} dinonaktifkan.",
                        gagal = "Gagal menonaktifkan ${info.label} — periksa kanal shell di tab Status."
                    )
                }
                .show()
        }
    }

    private fun konfirmasiHapus(info: DataPaket.Info) {
        AlertDialog.Builder(this)
            .setTitle("Hapus ${info.label}?")
            .setMessage(
                "Paket: ${info.pkg}\n" +
                    "Perintah: pm uninstall ${info.pkg}\n\n" +
                    "Aplikasi DIHAPUS dari perangkat beserta datanya. Untuk " +
                    "mengembalikannya harus dipasang lagi dari toko atau file APK."
            )
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                Thread {
                    val log: (String) -> Unit = { LayananKunci.catat(this, it) }
                    val okShell = KunciPort.aksiShell(this, "pm uninstall ${info.pkg}", "Success", log)
                    runOnUiThread {
                        if (okShell) {
                            pesan("${info.label} dihapus.")
                            muatSemua(false)
                        } else {
                            // Cadangan: penghapus bawaan Android.
                            pesan("Kanal shell gagal — membuka penghapus bawaan.")
                            try {
                                startActivity(
                                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${info.pkg}"))
                                )
                            } catch (e: Exception) {
                                pesan("Tidak bisa menghapus ${info.label}: ${e.message}")
                            }
                            Handler(Looper.getMainLooper()).postDelayed({ muatSemua(false) }, 2500)
                        }
                    }
                }.start()
            }
            .show()
    }

    private fun pulihkanPaket(info: DataPaket.Info) {
        jalankanAksi(
            "cmd package install-existing --user 0 ${info.pkg}", "installed for user",
            berhasil = "${info.label} dipulihkan.",
            gagal = "Gagal memulihkan ${info.label} — periksa kanal shell di tab Status."
        )
    }

    /** Jalankan satu perintah shell di latar, beri tahu hasilnya, lalu baca ulang daftar. */
    private fun jalankanAksi(perintah: String, tanda: String, berhasil: String, gagal: String) {
        Thread {
            val log: (String) -> Unit = { LayananKunci.catat(this, it) }
            val okShell = KunciPort.aksiShell(this, perintah, tanda, log)
            runOnUiThread {
                pesan(if (okShell) berhasil else gagal)
                muatSemua(false)
            }
        }.start()
    }

    /** Nonaktifkan semua saran debloat yang masih aktif, sesudah satu konfirmasi. */
    private fun konfirmasiBulk() {
        val target = saranTerbaru.filter { !it.nonaktif && it.lindungi == null }
        if (target.isEmpty()) {
            pesan("Semua saran sudah nonaktif.")
            return
        }
        val daftar = target.joinToString("\n") { "• ${it.label}" }
        AlertDialog.Builder(this)
            .setTitle("Nonaktifkan ${target.size} saran debloat?")
            .setMessage(
                "$daftar\n\nSemua aplikasi di atas berhenti bekerja sampai diaktifkan " +
                    "lagi dari tab ini. Paket yang dilindungi tidak termasuk."
            )
            .setNegativeButton("Batal", null)
            .setPositiveButton("Nonaktifkan semua") { _, _ ->
                Thread {
                    val log: (String) -> Unit = { LayananKunci.catat(this, it) }
                    var berhasil = 0
                    for (info in target) {
                        if (KunciPort.aksiShell(
                                this, "pm disable-user --user 0 ${info.pkg}", "disabled", log
                            )
                        ) berhasil++
                    }
                    val gagal = target.size - berhasil
                    runOnUiThread {
                        pesan(
                            if (gagal == 0) "$berhasil saran dinonaktifkan."
                            else "$berhasil berhasil, $gagal gagal — periksa kanal shell di tab Status."
                        )
                        muatSemua(false)
                    }
                }.start()
            }
            .show()
    }

    private fun pasangkan() {
        val port = portField.text.toString().trim().toIntOrNull()
        val kode = kodeField.text.toString().trim()
        if (port == null || kode.length != 6) {
            pesan("Isi port pemasangan (angka) dan kode 6 digit dulu.")
            return
        }
        pesan("Memasangkan…")
        Thread {
            val hasil = try {
                AdbManager.getInstance(this).pair("127.0.0.1", port, kode)
            } catch (e: Exception) {
                LayananKunci.catat(this, "Pairing melempar: ${e.message}")
                false
            }
            LayananKunci.catat(this, "Pairing ke port $port: ${if (hasil) "BERHASIL ✓" else "GAGAL"}")
            runOnUiThread {
                pesan(
                    if (hasil) {
                        "Terpasang ✓ — layanan penjaga dimulai, port akan dikunci."
                    } else {
                        "Pairing gagal — periksa kode & port, pastikan dialog pemasangan masih terbuka."
                    }
                )
                if (hasil) LayananKunci.mulai(this)
                Handler(Looper.getMainLooper()).postDelayed({ muatSemua(true) }, 1500)
            }
        }.start()
    }
}
