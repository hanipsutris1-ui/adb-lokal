package com.omen.adblokal

import android.content.Context
import android.os.Build
import android.util.Base64
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import org.conscrypt.Conscrypt
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random

/**
 * Pengelola sambungan ADB ke adbd perangkat sendiri (pola yang sama seperti
 * aplikasi anyapk/App Manager di atas libadb-android). Kunci RSA + sertifikat
 * dibuat sekali lalu disimpan di preferensi privat aplikasi; pasangan (pairing)
 * dengan adbd membuat kunci ini dipercaya seterusnya.
 */
class AdbManager private constructor(context: Context) : AbsAdbConnectionManager() {

    companion object {
        @Volatile private var INSTANCE: AdbManager? = null
        fun getInstance(context: Context): AdbManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdbManager(context.applicationContext).also { INSTANCE = it }
            }

        private const val PREFS = "adb_keys"
        private const val K_PRIVATE = "private_key"
        private const val K_CERT = "certificate"
    }

    private var mPrivateKey: PrivateKey? = null
    private var mCertificate: Certificate? = null
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        try {
            if (Security.getProvider("Conscrypt") == null) {
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
            }
        } catch (t: Throwable) { /* libadb memasang penyedia sendiri bila perlu */ }
        setApi(Build.VERSION.SDK_INT)
        loadOrGenerateKeys()
    }

    private fun loadOrGenerateKeys() {
        val p = prefs.getString(K_PRIVATE, null)
        val c = prefs.getString(K_CERT, null)
        if (p != null && c != null) {
            try {
                mPrivateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(p, Base64.DEFAULT)))
                mCertificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(Base64.decode(c, Base64.DEFAULT).inputStream())
                return
            } catch (e: Exception) { /* kunci rusak -> buat baru */ }
        }
        generateAndSaveKeys()
    }

    private fun generateAndSaveKeys() {
        try {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
            val pair = kpg.generateKeyPair()
            mPrivateKey = pair.private

            val algo = "SHA512withRSA"
            val now = Date()
            val until = Date(System.currentTimeMillis() + 3650L * 24 * 60 * 60 * 1000)
            val ext = CertificateExtensions()
            ext.set("SubjectKeyIdentifier", SubjectKeyIdentifierExtension(KeyIdentifier(pair.public).identifier))
            ext.set("PrivateKeyUsage", PrivateKeyUsageExtension(now, until))
            val name = X500Name("CN=adblokal")
            val info = X509CertInfo()
            info.set("version", CertificateVersion(2))
            info.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
            info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algo)))
            info.set("subject", CertificateSubjectName(name))
            info.set("key", CertificateX509Key(pair.public))
            info.set("validity", CertificateValidity(now, until))
            info.set("issuer", CertificateIssuerName(name))
            info.set("extensions", ext)
            val cert = X509CertImpl(info)
            cert.sign(mPrivateKey, algo)
            mCertificate = cert

            prefs.edit()
                .putString(K_PRIVATE, Base64.encodeToString(mPrivateKey!!.encoded, Base64.DEFAULT))
                .putString(K_CERT, Base64.encodeToString(mCertificate!!.encoded, Base64.DEFAULT))
                .apply()
        } catch (e: Exception) {
            throw RuntimeException("Gagal membuat kunci ADB", e)
        }
    }

    public override fun getPrivateKey(): PrivateKey = mPrivateKey!!
    public override fun getCertificate(): Certificate = mCertificate!!
    public override fun getDeviceName(): String = "ADB Lokal"
}
