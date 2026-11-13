package com.omen.adblokal

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * Gaya visual v12 — tema tvOS (gradasi gelap + kaca buram) dan saklar gaya
 * Launcher Luxio (aksen #5FB4FF), digambar dengan drawable bawaan Android.
 */
object Gaya {
    val AKSEN = Color.rgb(0x5F, 0xB4, 0xFF)
    val TINTA = Color.rgb(0xEE, 0xF2, 0xF6)
    val REDA = Color.argb(199, 238, 242, 246)   // rgba(238,242,246,.78)
    val KEDUA = Color.rgb(0xC7, 0xC7, 0xCC)     // teks sekunder tvOS
    val HIJAU = Color.rgb(0x8B, 0xC3, 0x4A)
    val KUNING = Color.rgb(0xFF, 0xD5, 0x8A)

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /** Latar halaman: gradasi 135° #1C1C1E -> #3A3A3C seperti tvOS. */
    fun latarHalaman(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(0x1C, 0x1C, 0x1E), Color.rgb(0x3A, 0x3A, 0x3C))
    )

    /** Kartu kaca buram: putih 7%, garis tepi putih 13%, sudut membulat. */
    fun kaca(ctx: Context, radiusDp: Int = 20): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(ctx, radiusDp).toFloat()
        setColor(Color.argb(18, 255, 255, 255))
        setStroke(dp(ctx, 1), Color.argb(33, 255, 255, 255))
    }

    /** Baris saklar gaya Luxio: menyala -> tepian & isi aksen; mati -> putih tipis. */
    fun barisSaklar(ctx: Context, menyala: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(ctx, 18).toFloat()
        if (menyala) {
            setColor(Color.argb(18, 0x5F, 0xB4, 0xFF))
            setStroke(dp(ctx, 1), Color.argb(115, 0x5F, 0xB4, 0xFF))
        } else {
            setColor(Color.argb(8, 255, 255, 255))
            setStroke(dp(ctx, 1), Color.argb(31, 255, 255, 255))
        }
    }

    /** Pil kecil (status / tombol baris). */
    fun pil(ctx: Context): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(ctx, 999).toFloat()
        setColor(Color.argb(20, 255, 255, 255))
        setStroke(dp(ctx, 1), Color.argb(36, 255, 255, 255))
    }
}
