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
 * Layar utama ADB Lokal v12.
 *
 * Tampilan per tab bergaya tvOS (gradasi gelap + kartu kaca) dengan saklar
 * gaya Launcher Luxio; fungsi penjaga port 5555 sama persis dengan v11
 * (KunciPort/LayananKunci/BootReceiver tidak berubah perilakunya), ditambah
 * panel Debloat ala tvOS yang berjalan lewat kanal shell libadb yang sudah
 * ada: Downloaded Apps (hapus), System Apps (saklar nonaktif/aktif),
 * Restore Removed Apps (pulihkan), ADB Tools (status kanal).
 *
 * Semua daftar dibaca langsung dari perangkat yang menjalankan aplikasi,
 * dan perlindungan paket dihitung per perangkat — aplikasi ini bisa dipakai
 * di HU Android 11+ lain, bukan hanya HU pemilik pertama.
 */
class MainActivity : Activity() {

    // ---- keadaan ----
    @Volatile private var kanalOk = false
    private var hanyaNonaktif = false

    // ---- tampilan ----
    private lateinit var pilPort: TextView
    private lateinit var pilShell: TextView
    private lateinit var tabTeks: List<TextView>
    private lateinit var panelStatus: View
    private lateinit var panelDebloat: View
    private lateinit var panelSistem: View
    private lateinit var panelUnduhan: View
    private lateinit var panelPulih: View
    private lateinit var teksStatus: TextView
    private lateinit var saklarPenjaga: Switch
    private lateinit var nilaiUnduhan: TextView
    private lateinit var nilaiSistem: TextView
    private lateinit var nilaiPulih: TextView
    private lateinit var nilaiKanal: TextView
    private lateinit var logView: TextView
    private lateinit var isiLipat: View
    private lateinit var judulLipat: TextView
    private lateinit var portField: EditText
    private lateinit var kodeField: EditText
    private lateinit var adapterSistem: AdapterPaket
    private lateinit var adapterUnduhan: AdapterPaket
    private lateinit var adapterPulih: AdapterPaket

    private fun dp(v: Int): Int = Gaya.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buatUi()
        muatSemua(probeKanal = true)
    }

    override fun onResume() {
        super.onResume()
        if (::teksStatus.isInitialized) muatSemua(probeKanal = false)
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

    private fun pil(isi: String): TextView = TextView(this).apply {
        text = isi
        textSize = 13f
        setTextColor(Gaya.TINTA)
        background = Gaya.pil(this@MainActivity)
        setPadding(dp(13), dp(6), dp(13), dp(6))
    }

    private fun aturPil(tv: TextView, label: String, baik: Boolean) {
        val s = SpannableString("● $label")
        s.setSpan(
            ForegroundColorSpan(if (baik) Gaya.HIJAU else Color.rgb(0x8E, 0x8E, 0x93)),
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

    /** Baris menu ala tvOS: label kiri, nilai + › kanan. Mengembalikan TextView nilai. */
    private fun barisMenu(
        induk: LinearLayout, label: String, nilaiAwal: String, aksi: () -> Unit
    ): TextView {
        val baris = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Gaya.kaca(this@MainActivity, 16)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dp(10))
            layoutParams = lp
            isClickable = true
            isFocusable = true
            setOnClickListener { aksi() }
        }
        baris.addView(teks(label, 16.5f, Gaya.TINTA).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val nilai = teks(nilaiAwal, 15f, Gaya.KEDUA)
        baris.addView(nilai)
        baris.addView(teks("  ›", 17f, Gaya.KEDUA))
        induk.addView(baris)
        return nilai
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
        root.addView(teks("Penjaga ADB & alat debloat head unit", 14f, Gaya.KEDUA).apply {
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

        // Bar tab kaca model segmented.
        val namaTab = listOf("Status", "Debloat", "System Apps", "Downloaded Apps")
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
        root.addView(tabBar)

        // Wadah panel.
        val wadah = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        panelStatus = bangunPanelStatus()
        panelDebloat = bangunPanelDebloat()
        panelSistem = bangunPanelSistem()
        panelUnduhan = bangunPanelUnduhan()
        panelPulih = bangunPanelPulih()
        listOf(panelStatus, panelDebloat, panelSistem, panelUnduhan, panelPulih)
            .forEach { wadah.addView(it) }
        root.addView(wadah)

        root.addView(teks(
            "Dilindungi & tidak bisa diubah: sistem mobil (com.tw.* dan sejenisnya), " +
                "pengaturan mobil, launcher Home yang sedang dipakai, inti Android, " +
                "Tailscale, dan aplikasi ini sendiri. Semua daftar & status dibaca " +
                "langsung dari perangkat ini.",
            12f, Gaya.KEDUA
        ).apply { setPadding(0, dp(10), 0, 0) })

        setContentView(root)
        bukaTab(0)
    }

    private fun bukaTab(i: Int) {
        val panel = listOf(panelStatus, panelDebloat, panelSistem, panelUnduhan)[i]
        tampilkanPanel(panel, i)
    }

    private fun tampilkanPanel(panel: View, tabAktif: Int) {
        listOf(panelStatus, panelDebloat, panelSistem, panelUnduhan, panelPulih)
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
        val isi = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(8))
        }
        val kartuStatus = kartu()
        teksStatus = teks("Memeriksa keadaan…", 15f, Gaya.TINTA, tebal = true).apply {
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        kartuStatus.addView(teksStatus)
        isi.addView(kartuStatus)

        val kartuKunci = kartu()
        kartuKunci.addView(barisSaklarKartu(
            "Kunci port ${KunciPort.PORT}",
            "Penjaga mengunci port tiap HU selesai booting",
            KunciPort.penjagaAktif(this)
        ) { nyala -> saklarPenjagaBerubah(nyala) })
        val barisTombol = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        barisTombol.addView(tombol("Jalankan sekarang") {
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
        kartuKunci.addView(barisTombol)
        isi.addView(kartuKunci)

        // Bagian lipat: Pemasangan & Catatan (warisan v11, tetap utuh fungsinya).
        judulLipat = teks("Pemasangan & Catatan  ▸", 16.5f, Gaya.TINTA, tebal = true).apply {
            isClickable = true
            setPadding(dp(2), dp(6), dp(2), dp(10))
            setOnClickListener {
                val buka = isiLipat.visibility != View.VISIBLE
                isiLipat.visibility = if (buka) View.VISIBLE else View.GONE
                text = if (buka) "Pemasangan & Catatan  ▾" else "Pemasangan & Catatan  ▸"
            }
        }
        isi.addView(judulLipat)
        val lipat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val kartuPasang = kartu()
        kartuPasang.addView(teks(
            "Sekali saja, saat pemasangan awal di perangkat baru:\n" +
                "1) Buka Opsi pengembang → Wireless debugging → \"Pasangkan perangkat dengan kode pemasangan\". " +
                "Layar itu menampilkan kode 6 digit dan alamat IP:port — angka sesudah titik dua adalah port pemasangan.\n" +
                "2) Isi keduanya di sini selagi dialog itu masih terbuka, lalu tekan Pasangkan.\n" +
                "3) Sesudah berhasil, aplikasi memberi izinnya sendiri dan langsung mengunci port.",
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
        val kartuLog = kartu()
        kartuLog.addView(teks("Catatan kerja", 15f, Gaya.TINTA, tebal = true).apply {
            setPadding(0, 0, 0, dp(6))
        })
        logView = teks("(memuat…)", 12f, Gaya.REDA)
        kartuLog.addView(logView)
        lipat.addView(kartuLog)
        lipat.visibility = if (KunciPort.punyaIzin(this)) View.GONE else View.VISIBLE
        if (lipat.visibility == View.VISIBLE) judulLipat.text = "Pemasangan & Catatan  ▾"
        isiLipat = lipat
        isi.addView(lipat)
        return gulir(isi)
    }

    private fun bangunPanelDebloat(): View {
        val isi = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(8))
        }
        nilaiUnduhan = barisMenu(isi, "Downloaded Apps", "…") { bukaTab(3) }
        nilaiSistem = barisMenu(isi, "System Apps", "…") { bukaTab(2) }
        nilaiPulih = barisMenu(isi, "Restore Removed Apps", "") {
            tampilkanPanel(panelPulih, 1)
        }
        nilaiKanal = barisMenu(isi, "ADB Tools", "…") {
            bukaTab(0)
            muatSemua(probeKanal = true)
        }
        isi.addView(teks(
            "Sebelum mulai: aplikasi unduhan dihapus dari perangkat — pasang lagi dari " +
                "toko untuk mengembalikannya. Aplikasi sistem hanya dinonaktifkan dan " +
                "bisa diaktifkan lagi kapan saja; yang sudah terlanjur di-Remove bisa " +
                "dipulihkan lewat Restore Removed Apps.",
            13.5f, Gaya.KEDUA
        ).apply { setPadding(dp(4), dp(6), dp(4), 0) })
        return gulir(isi)
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
        val cari = EditText(this).apply {
            hint = "Cari aplikasi sistem…"
            setTextColor(Gaya.TINTA)
            setHintTextColor(Color.argb(140, 255, 255, 255))
            background = Gaya.kaca(this@MainActivity, 14)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            textSize = 15f
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dp(8))
            layoutParams = lp
        }
        isi.addView(cari)
        val chip = teks("Hanya yang nonaktif", 13.5f, Gaya.KEDUA).apply {
            background = Gaya.pil(this@MainActivity)
            setPadding(dp(13), dp(7), dp(13), dp(7))
            isClickable = true
            setOnClickListener {
                hanyaNonaktif = !hanyaNonaktif
                if (hanyaNonaktif) {
                    background = Gaya.pil(this@MainActivity).apply {
                        setColor(Color.argb(46, 0x5F, 0xB4, 0xFF))
                        setStroke(dp(1), Color.argb(115, 0x5F, 0xB4, 0xFF))
                    }
                    setTextColor(Gaya.TINTA)
                } else {
                    background = Gaya.pil(this@MainActivity)
                    setTextColor(Gaya.KEDUA)
                }
                if (::adapterSistem.isInitialized) {
                    adapterSistem.saring(cari.text.toString(), hanyaNonaktif)
                }
            }
        }
        val barisChip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dp(4))
            layoutParams = lp
        }
        barisChip.addView(chip)
        isi.addView(barisChip)
        adapterSistem = AdapterPaket(
            this, AdapterPaket.Mode.SISTEM,
            padaSaklar = { info, nyala -> saklarSistemBerubah(info, nyala) },
            padaTombol = { }
        )
        val daftar = ListView(this).apply {
            adapter = adapterSistem
            divider = null
            dividerHeight = 0
            setCacheColorHint(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        isi.addView(daftar)
        cari.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (::adapterSistem.isInitialized) {
                    adapterSistem.saring(s?.toString() ?: "", hanyaNonaktif)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        return isi
    }

    private fun bangunPanelUnduhan(): View {
        val isi = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }
        isi.addView(teks(
            "Menghapus = uninstall dari perangkat; pasang lagi dari toko untuk mengembalikan.",
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

    private fun bangunPanelPulih(): View {
        val isi = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }
        isi.addView(teks("‹  Kembali ke Debloat", 15.5f, Gaya.AKSEN, tebal = true).apply {
            isClickable = true
            setPadding(dp(4), dp(4), dp(4), dp(10))
            setOnClickListener { tampilkanPanel(panelDebloat, 1) }
        })
        isi.addView(teks(
            "Aplikasi sistem yang pernah di-Remove untuk pengguna ini. Memulihkan " +
                "memasangnya lagi tanpa perlu file APK.",
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
            val capWaktu = if (waktu > 0) {
                SimpleDateFormat("dd-MM HH:mm", Locale.US).format(Date(waktu))
            } else "—"
            val ringkas = buildString {
                appendLine("Izin khusus (WRITE_SECURE_SETTINGS): ${if (izin) "ADA ✓" else "BELUM ADA"}")
                appendLine("Wireless debugging: ${if (wd) "NYALA" else "mati"}")
                appendLine("Port ${KunciPort.PORT}: ${if (port) "TERBUKA ✓" else "tertutup"}")
                appendLine("Kanal shell (ADB lokal): ${if (kanalOk) "TERSAMBUNG ✓" else "belum tersambung"}")
                append("Hasil terakhir ($capWaktu): ${if (ok) "BERHASIL" else "—"} · $pesanHasil")
            }
            val log = LayananKunci.bacaLog(this)
            runOnUiThread {
                if (!::teksStatus.isInitialized) return@runOnUiThread
                teksStatus.text = ringkas
                logView.text = log
                aturPil(pilPort, "Port ${KunciPort.PORT} · ${if (port) "Terbuka" else "Tertutup"}", port)
                aturPil(pilShell, "Shell · ${if (kanalOk) "Tersambung" else "Belum tersambung"}", kanalOk)
                nilaiUnduhan.text = "${unduhan.size}"
                nilaiSistem.text = "${sistem.size}"
                nilaiPulih.text = if (terhapus.isEmpty()) "" else "${terhapus.size}"
                nilaiKanal.text = if (kanalOk) "Tersambung ✓" else "Belum tersambung"
                val barisSistem = mutableListOf<AdapterPaket.Baris>()
                if (saran.isNotEmpty()) {
                    barisSistem.add(AdapterPaket.Baris.Judul("Saran debloat — terpasang di perangkat ini"))
                    saran.forEach { barisSistem.add(AdapterPaket.Baris.Paket(it)) }
                    barisSistem.add(AdapterPaket.Baris.Judul("Semua aplikasi sistem"))
                }
                sistem.filter { it.pkg !in saranPkg }
                    .forEach { barisSistem.add(AdapterPaket.Baris.Paket(it)) }
                adapterSistem.gantiData(barisSistem)
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
