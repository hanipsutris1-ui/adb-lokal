package com.omen.adblokal

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/**
 * Adapter daftar paket untuk tab System Apps (saklar), Downloaded Apps
 * (tombol Hapus), dan panel Pulihkan (tombol Pulihkan).
 *
 * Saklar tidak pernah bergerak sendiri: saat pengguna menyentuhnya, saklar
 * dikembalikan ke keadaan data dan keputusan diserahkan ke [padaSaklar] —
 * layar utama boleh meminta konfirmasi dulu; keadaan akhir selalu datang
 * dari data yang dibaca ulang sesudah aksi shell selesai.
 */
class AdapterPaket(
    private val ctx: Context,
    private val mode: Mode,
    private val padaSaklar: (DataPaket.Info, Boolean) -> Unit,
    private val padaTombol: (DataPaket.Info) -> Unit
) : BaseAdapter() {

    enum class Mode { SISTEM, UNDUHAN, PULIHKAN }

    sealed class Baris {
        data class Judul(val teks: String) : Baris()
        data class Paket(val info: DataPaket.Info) : Baris()
    }

    private var semua: List<Baris> = emptyList()
    private var tampil: List<Baris> = emptyList()
    private var kata = ""
    private var hanyaNonaktif = false

    private val cacheIkon = HashMap<String, Drawable>()

    /** Ikon aplikasi asli dari perangkat (ditembolek); kosong bila gagal dibaca. */
    private fun ikonView(pkg: String): ImageView {
        val iv = ImageView(ctx)
        val lp = LinearLayout.LayoutParams(Gaya.dp(ctx, 36), Gaya.dp(ctx, 36))
        lp.setMargins(0, 0, Gaya.dp(ctx, 12), 0)
        iv.layoutParams = lp
        val d = cacheIkon[pkg] ?: try {
            ctx.packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            null
        }
        if (d != null) {
            cacheIkon[pkg] = d
            iv.setImageDrawable(d)
        }
        return iv
    }

    fun gantiData(data: List<Baris>) {
        semua = data
        saringUlang()
    }

    fun saring(kataBaru: String, nonaktifSaja: Boolean) {
        kata = kataBaru.trim().lowercase()
        hanyaNonaktif = nonaktifSaja
        saringUlang()
    }

    private fun saringUlang() {
        if (kata.isEmpty() && !hanyaNonaktif) {
            tampil = semua
        } else {
            val hasil = mutableListOf<Baris>()
            var judul: Baris.Judul? = null
            var isiJudul = mutableListOf<Baris>()
            fun tuang() {
                if (isiJudul.isNotEmpty()) {
                    judul?.let { hasil.add(it) }
                    hasil.addAll(isiJudul)
                }
                isiJudul = mutableListOf()
            }
            for (b in semua) {
                when (b) {
                    is Baris.Judul -> { tuang(); judul = b }
                    is Baris.Paket -> {
                        val cocokKata = kata.isEmpty() ||
                            b.info.label.lowercase().contains(kata) ||
                            b.info.pkg.lowercase().contains(kata)
                        val cocokStatus = !hanyaNonaktif || b.info.nonaktif
                        if (cocokKata && cocokStatus) isiJudul.add(b)
                    }
                }
            }
            tuang()
            tampil = hasil
        }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = tampil.size
    override fun getItem(pos: Int): Baris = tampil[pos]
    override fun getItemId(pos: Int): Long = pos.toLong()
    override fun getViewTypeCount(): Int = 2
    override fun getItemViewType(pos: Int): Int = if (tampil[pos] is Baris.Judul) 0 else 1
    override fun areAllItemsEnabled(): Boolean = false
    override fun isEnabled(pos: Int): Boolean = tampil[pos] is Baris.Paket

    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        return when (val b = tampil[pos]) {
            is Baris.Judul -> buatJudul(b.teks)
            is Baris.Paket -> buatBaris(b.info)
        }
    }

    private fun buatJudul(teks: String): TextView = TextView(ctx).apply {
        text = teks
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Gaya.KEDUA)
        setPadding(Gaya.dp(ctx, 4), Gaya.dp(ctx, 16), Gaya.dp(ctx, 4), Gaya.dp(ctx, 6))
    }

    private fun buatBaris(info: DataPaket.Info): View {
        val terkunci = info.lindungi != null
        val aktif = !info.nonaktif
        // Pembungkus luar memberi jarak antarbaris (ListView membuang margin item).
        val bungkus = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Gaya.dp(ctx, 5), 0, Gaya.dp(ctx, 5))
        }
        val baris = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = Gaya.dp(ctx, 72)
            setPadding(Gaya.dp(ctx, 18), Gaya.dp(ctx, 12), Gaya.dp(ctx, 18), Gaya.dp(ctx, 12))
            background = Gaya.barisSaklar(ctx, aktif && mode == Mode.SISTEM)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (terkunci) alpha = 0.55f
        }
        bungkus.addView(baris)

        val kolom = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        kolom.addView(TextView(ctx).apply {
            text = info.label
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Gaya.TINTA)
        })
        kolom.addView(TextView(ctx).apply {
            text = when {
                terkunci -> "${info.pkg} · Dilindungi — ${info.lindungi}"
                mode == Mode.SISTEM && DataPaket.keteranganSaran(info.pkg) != null ->
                    "${info.pkg} · Saran debloat"
                else -> info.pkg
            }
            textSize = 13.5f
            setTextColor(Gaya.REDA)
        })
        baris.addView(ikonView(info.pkg))
        baris.addView(kolom)

        when (mode) {
            Mode.SISTEM -> {
                val sw = Switch(ctx).apply {
                    trackDrawable = ctx.getDrawable(R.drawable.sw_track)
                    thumbDrawable = ctx.getDrawable(R.drawable.sw_thumb)
                    setSwitchMinWidth(Gaya.dp(ctx, 54))
                    thumbTextPadding = 0
                    text = ""
                    showText = false
                    isEnabled = !terkunci
                }
                val pendengar = object : android.widget.CompoundButton.OnCheckedChangeListener {
                    override fun onCheckedChanged(
                        tombol: android.widget.CompoundButton, nyala: Boolean
                    ) {
                        // Kembalikan saklar ke keadaan data tanpa memicu ulang,
                        // lalu serahkan keputusan ke layar utama.
                        tombol.setOnCheckedChangeListener(null)
                        tombol.isChecked = aktif
                        tombol.setOnCheckedChangeListener(this)
                        padaSaklar(info, nyala)
                    }
                }
                sw.setOnCheckedChangeListener(null)
                sw.isChecked = aktif
                sw.setOnCheckedChangeListener(pendengar)
                baris.addView(sw)
            }
            Mode.UNDUHAN, Mode.PULIHKAN -> {
                if (terkunci) {
                    baris.addView(TextView(ctx).apply {
                        text = "Dilindungi"
                        textSize = 13f
                        setTextColor(Gaya.KEDUA)
                    })
                } else {
                    baris.addView(Button(ctx).apply {
                        text = if (mode == Mode.UNDUHAN) "Hapus" else "Pulihkan"
                        textSize = 14f
                        setTextColor(Gaya.TINTA)
                        background = Gaya.pil(ctx)
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(Gaya.dp(ctx, 18), Gaya.dp(ctx, 6), Gaya.dp(ctx, 18), Gaya.dp(ctx, 6))
                        setOnClickListener { padaTombol(info) }
                    })
                }
            }
        }
        return bungkus
    }
}
