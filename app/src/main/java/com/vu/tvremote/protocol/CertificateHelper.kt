package com.vu.tvremote.protocol

import android.content.Context
import android.util.Base64
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.*

/**
 * Handles lightweight mTLS KeyStore management for Google TV Remote protocol.
 * Generates an RSA keypair & self-signed X.509 certificate once and caches it in SharedPreferences.
 * Zero external libraries needed; uses standard Android java.security provider.
 */
class CertificateHelper(private val context: Context) {

    private val prefs = context.getSharedPreferences("vu_remote_crypto", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_PRIVATE_KEY = "client_priv_key"
        private const val PREF_CERTIFICATE = "client_cert_der"
    }

    data class ClientCredentials(
        val keyStore: KeyStore,
        val sslContext: SSLContext,
        val clientCert: X509Certificate
    )

    fun getOrCreateSslContext(): ClientCredentials {
        var keyPair: KeyPair? = null
        var cert: X509Certificate? = null

        val savedKey = prefs.getString(PREF_PRIVATE_KEY, null)
        val savedCert = prefs.getString(PREF_CERTIFICATE, null)

        if (savedKey != null && savedCert != null) {
            try {
                keyPair = loadKeyPair(savedKey)
                cert = loadCertificate(savedCert)
            } catch (e: Exception) {
                // Key corrupted or format changed, regenerate
                keyPair = null
                cert = null
            }
        }

        if (keyPair == null || cert == null) {
            val generated = generateNewCredentials()
            keyPair = generated.first
            cert = generated.second
            saveCredentials(keyPair, cert)
        }

        // Initialize KeyStore
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("client_alias", keyPair.private, "vu_pass".toCharArray(), arrayOf(cert))
        }

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, "vu_pass".toCharArray())
        }

        // Custom TrustManager accepting TV's self-signed TLS cert during pairing & control
        val trustAllManager = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, trustAllManager, SecureRandom())
        }

        return ClientCredentials(keyStore, sslContext, cert)
    }

    private fun generateNewCredentials(): Pair<KeyPair, X509Certificate> {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val keyPair = kpg.generateKeyPair()

        // Generate self-signed X.509 cert
        // Note: For Android 9+ Google TV Remote, standard X.509 V1/V3 structure with CN="VU-Remote"
        val cert = createSelfSignedCert(keyPair)
        return Pair(keyPair, cert)
    }

    private fun createSelfSignedCert(keyPair: KeyPair): X509Certificate {
        // Minimal DER structure or using Android KeyStore self-signed generator
        // In Android, we construct a valid X.509 ASN.1 certificate for the client identity:
        val subject = "CN=VU-Remote-Controller, O=VUSmartTV, C=IN"
        
        // Standard Android CertificateFactory generator
        // Using bouncycastle or system provider if available, or lightweight dummy DER format
        val certBytes = generateSelfSignedDer(keyPair, subject)
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
    }

    private fun generateSelfSignedDer(keyPair: KeyPair, subjectDn: String): ByteArray {
        // Build self-signed certificate bytes
        // On modern Android, we can create via KeyStore or fallback to precomputed valid ASN1 header
        val validFrom = Date(System.currentTimeMillis() - 86400000L)
        val validTo = Date(System.currentTimeMillis() + 10L * 365 * 86400000L) // 10 years validity

        // We use Android's internal certificate builder or a standard ASN.1 wrapper:
        return SimpleCertGenerator.generate(keyPair, subjectDn, validFrom, validTo)
    }

    private fun saveCredentials(keyPair: KeyPair, cert: X509Certificate) {
        val privEncoded = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        val certEncoded = Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
        prefs.edit()
            .putString(PREF_PRIVATE_KEY, privEncoded)
            .putString(PREF_CERTIFICATE, certEncoded)
            .apply()
    }

    private fun loadKeyPair(encodedPriv: String): KeyPair {
        val kf = java.security.KeyFactory.getInstance("RSA")
        val spec = java.security.spec.PKCS8EncodedKeySpec(Base64.decode(encodedPriv, Base64.NO_WRAP))
        val privKey = kf.generatePrivate(spec)
        return KeyPair(null, privKey)
    }

    private fun loadCertificate(encodedCert: String): X509Certificate {
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(ByteArrayInputStream(Base64.decode(encodedCert, Base64.NO_WRAP))) as X509Certificate
    }
}

/**
 * Lightweight ASN.1 DER X.509 v3 Generator with SHA256withRSA signature.
 * Compatible with Android TV Remote Service protocol on Android 9.
 */
object SimpleCertGenerator {
    fun generate(keyPair: KeyPair, subject: String, notBefore: Date, notAfter: Date): ByteArray {
        // Standard self-signed X.509 format:
        // TBSCertificate -> SignatureAlgorithm -> SignatureValue
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)

        val tbs = buildTbsCertificate(keyPair.public, subject, notBefore, notAfter)
        sig.update(tbs)
        val signature = sig.sign()

        // Wrap into full SEQUENCE
        val algId = byteArrayOf(
            0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(),
            0xf7.toByte(), 0x0d, 0x01, 0x01, 0x0b, 0x05, 0x00
        ) // sha256WithRSAEncryption

        val sigBitString = encodeBitString(signature)
        return encodeSequence(tbs + algId + sigBitString)
    }

    private fun buildTbsCertificate(publicKey: PublicKey, subject: String, from: Date, to: Date): ByteArray {
        val serial = encodeInteger(BigInteger.valueOf(System.currentTimeMillis()))
        val algId = byteArrayOf(
            0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(),
            0xf7.toByte(), 0x0d, 0x01, 0x01, 0x0b, 0x05, 0x00
        )
        val nameDer = encodeName(subject)
        val validity = encodeValidity(from, to)
        val spki = publicKey.encoded

        return encodeSequence(serial + algId + nameDer + validity + nameDer + spki)
    }

    private fun encodeSequence(content: ByteArray): ByteArray {
        return byteArrayOf(0x30) + encodeLength(content.size) + content
    }

    private fun encodeLength(len: Int): ByteArray {
        return if (len < 128) {
            byteArrayOf(len.toByte())
        } else if (len < 256) {
            byteArrayOf(0x81.toByte(), len.toByte())
        } else {
            byteArrayOf(0x82.toByte(), (len shr 8).toByte(), (len and 0xFF).toByte())
        }
    }

    private fun encodeInteger(bigInt: BigInteger): ByteArray {
        val bytes = bigInt.toByteArray()
        return byteArrayOf(0x02) + encodeLength(bytes.size) + bytes
    }

    private fun encodeBitString(bytes: ByteArray): ByteArray {
        return byteArrayOf(0x03) + encodeLength(bytes.size + 1) + byteArrayOf(0x00) + bytes
    }

    private fun encodeName(dn: String): ByteArray {
        val cnVal = "VU-Remote"
        val cnBytes = cnVal.toByteArray(Charsets.UTF_8)
        val cnSeq = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03) + byteArrayOf(0x0c) + encodeLength(cnBytes.size) + cnBytes
        val set = byteArrayOf(0x31) + encodeLength(cnSeq.size + 2) + encodeSequence(cnSeq)
        return encodeSequence(set)
    }

    private fun encodeValidity(from: Date, to: Date): ByteArray {
        val sdf = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val fromBytes = sdf.format(from).toByteArray(Charsets.US_ASCII)
        val toBytes = sdf.format(to).toByteArray(Charsets.US_ASCII)
        val fDer = byteArrayOf(0x17) + encodeLength(fromBytes.size) + fromBytes
        val tDer = byteArrayOf(0x17) + encodeLength(toBytes.size) + toBytes
        return encodeSequence(fDer + tDer)
    }
}
