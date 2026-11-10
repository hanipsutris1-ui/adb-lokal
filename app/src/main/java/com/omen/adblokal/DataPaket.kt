package com.omen.adblokal

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Data paket untuk panel Debloat v12 — semuanya dibaca LANGSUNG dari perangkat
 * yang menjalankan aplikasi ini, jadi aplikasi yang sama bisa dipakai di HU lain:
 *  - daftar aplikasi sistem & unduhan dari PackageManager perangkat itu,
 *  - status aktif/nonaktif per paket,
 *  - daftar aplikasi sistem yang pernah di-Remove untuk user 0 (bisa dipulihkan),
 *  - aturan perlindungan yang dihitung per perangkat (Home aktif, inti Android,
 *    lapisan sistem mobil) + daftar lindung bawaan beralasan,
 *  - basis "saran debloat": paket boros yang dikenal; hanya yang benar-benar
 *    terpasang di perangkat yang ditampilkan sebagai saran.
 */
object DataPaket {

    data class Info(
        val pkg: String,
        val label: String,
        val sistem: Boolean,
        val nonaktif: Boolean,
        /** Alasan dilindungi; null = boleh diubah. */
        val lindungi: String?
    )

    // ---------- Perlindungan ----------

    /** Paket inti Android: mengubahnya bisa membuat perangkat tidak bisa menyala normal. */
    private val INTI = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.shell",
        "com.android.providers.settings",
        "com.android.providers.media",
        "com.android.providers.downloads",
        "com.google.android.gms",
        "com.google.android.gsf"
    )

    /** Awalan paket lapisan sistem mobil dari beberapa merek HU yang dikenal. */
    private val AWALAN_MOBIL = listOf("com.tw.", "com.syu.", "com.microntek.", "com.zjinnova.")

    /** Paket spesifik beralasan kuat, di mana pun ia ada. */
    private val LINDUNG_KHUSUS = mapOf(
        "com.dofun.variety" to "Launcher bawaan HU — di HU TopWay radio bergantung padanya",
        "com.tailscale.ipn" to "VPN akses jarak jauh — memutusnya bisa memutus jalur remote"
    )

    /** Paket Home (launcher) yang sedang dipakai perangkat ini. */
    fun paketHome(ctx: Context): String? = try {
        val niat = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        ctx.packageManager.resolveActivity(niat, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    } catch (e: Exception) { null }

    /** Kembalikan alasan perlindungan, atau null bila paket boleh diubah. */
    fun alasanLindungi(ctx: Context, pkg: String, home: String?): String? {
        if (pkg == ctx.packageName) return "Aplikasi ini sendiri"
        if (pkg == home) return "Launcher Home yang sedang dipakai"
        if (pkg in INTI) return "Inti sistem Android"
        if (AWALAN_MOBIL.any { pkg.startsWith(it) }) return "Sistem mobil bawaan HU"
        if (pkg.contains("carsetting")) return "Pengaturan mobil"
        LINDUNG_KHUSUS[pkg]?.let { return it }
        return null
    }

    // ---------- Pembacaan daftar ----------

    private fun statusNonaktif(pm: PackageManager, pkg: String): Boolean = try {
        val s = pm.getApplicationEnabledSetting(pkg)
        s == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            s == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
    } catch (e: Exception) { false }

    private fun keInfo(ctx: Context, pm: PackageManager, ai: ApplicationInfo, home: String?): Info {
        val label = try { pm.getApplicationLabel(ai).toString() } catch (e: Exception) { ai.packageName }
        val sistem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return Info(
            pkg = ai.packageName,
            label = label.ifBlank { ai.packageName },
            sistem = sistem,
            nonaktif = statusNonaktif(pm, ai.packageName),
            lindungi = alasanLindungi(ctx, ai.packageName, home)
        )
    }

    /** Semua aplikasi terpasang, disaring sistem/unduhan, terurut menurut label. */
    fun daftar(ctx: Context, sistem: Boolean): List<Info> {
        val pm = ctx.packageManager
        val home = paketHome(ctx)
        return try {
            pm.getInstalledPackages(0)
                .map { keInfo(ctx, pm, it.applicationInfo, home) }
                .filter { it.sistem == sistem }
                .sortedBy { it.label.lowercase() }
        } catch (e: Exception) { emptyList() }
    }

    /** Aplikasi yang sudah di-Remove untuk user 0 (masih ada di perangkat, bisa dipulihkan). */
    fun daftarTerhapus(ctx: Context): List<Info> {
        val pm = ctx.packageManager
        val home = paketHome(ctx)
        return try {
            val terpasang = pm.getInstalledPackages(0).map { it.packageName }.toHashSet()
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES)
                .filter { it.packageName !in terpasang }
                .map { keInfo(ctx, pm, it.applicationInfo, home) }
                .sortedBy { it.label.lowercase() }
        } catch (e: Exception) { emptyList() }
    }

    // ---------- Saran debloat ----------

    /** (paket, nama ramah, alasan) — paket boros yang dikenal dari HU Android umumnya. */
    val SARAN: List<Triple<String, String, String>> = listOf(
        Triple("com.dofun.market", "Dofun Market", "Toko aplikasi bawaan Dofun"),
        Triple("com.huawei.nearby", "Huawei Nearby", "Berbagi jarak dekat Huawei"),
        Triple("com.huawei.hisight", "Huawei HiSight", "Layanan penglihatan Huawei"),
        Triple("com.huawei.hiviewlite", "Huawei HiView Lite", "Diagnostik Huawei"),
        Triple("com.huawei.dmsdpdevice", "Huawei DMSDP", "Sambungan antar-perangkat Huawei"),
        Triple("com.google.android.apps.bard", "Google Bard", "Asisten AI Google (Bard)"),
        Triple("com.google.android.apps.actionsservice", "Google Actions Service", "Layanan aksi Google"),
        Triple("com.google.android.gms.location.history", "Riwayat Lokasi Google", "Pencatat riwayat lokasi"),
        Triple("com.google.android.feedback", "Google Feedback", "Pengirim masukan Google"),
        Triple("com.android.dreams.basic", "Screensaver Basic", "Screensaver bawaan Android"),
        Triple("com.android.dreams.phototable", "Screensaver Photo Table", "Screensaver foto"),
        Triple("com.android.egg", "Easter Egg Android", "Permainan telur paskah Android"),
        Triple("com.android.bookmarkprovider", "Bookmark Provider", "Penyedia markah peramban lama"),
        Triple("com.android.providers.partnerbookmarks", "Partner Bookmarks", "Markah bawaan mitra"),
        Triple("com.android.printspooler", "Print Spooler", "Antrean cetak — HU tidak mencetak"),
        Triple("com.android.bips", "Layanan Cetak Bawaan", "Cetak bawaan — HU tidak mencetak"),
        Triple("com.google.android.printservice.recommendation", "Rekomendasi Cetak Google", "Rekomendasi layanan cetak")
    )

    /** Saran yang benar-benar terpasang di perangkat ini (dan tidak dilindungi). */
    fun saranYangAda(daftarSistem: List<Info>): List<Info> {
        val menurutPaket = daftarSistem.associateBy { it.pkg }
        return SARAN.mapNotNull { (pkg, _, _) -> menurutPaket[pkg] }
            .filter { it.lindungi == null }
    }

    /** Nama ramah + alasan saran untuk satu paket, bila dikenal. */
    fun keteranganSaran(pkg: String): String? =
        SARAN.firstOrNull { it.first == pkg }?.let { "${it.second} — ${it.third}" }
}
