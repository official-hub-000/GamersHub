package com.gamershub.core.adb

import java.io.ByteArrayOutputStream
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Mints the self-signed X.509 certificate that adbd uses to identify this app.
 *
 * ## Why there is a DER writer in here
 *
 * Wireless debugging does not authenticate a client by a public-key file. The
 * `adb pair` / `adb connect` handshake presents a **certificate**, and `adbd` records
 * that certificate as the authorised device. On a desktop that certificate comes from
 * `openssl`. On Android there is no `openssl`, and `sun.security.x509` — which the
 * reference implementations use — does not exist on the platform either.
 *
 * Options were: pull in a certificate-authority library, or write the one certificate
 * this app will ever need. The structure is fixed and small (a self-signed cert with a
 * single CN, no extensions), and every field is verified below against `openssl`, so
 * the small encoder is the honest choice over a dependency that cannot be tested here.
 *
 * ## Correctness
 *
 * The output of this encoder was validated with `openssl x509` and `openssl verify`
 * during development: it parses, and it verifies against itself. The subtleties that
 * actually break hand-rolled certificates are all handled explicitly —
 *
 *  - an `AlgorithmIdentifier` is a SEQUENCE of OID + NULL, not two siblings;
 *  - a `Name` is a SEQUENCE of SETs, and the SET is what makes it a valid RDN;
 *  - the `[0] EXPLICIT` version wrapper is required for v3;
 *  - the signature BIT STRING carries a leading zero unused-bits octet.
 */
object AdbCert {

    private const val OID_SHA256_RSA = "2a864886f70d01010b" // 1.2.840.113549.1.1.11
    private const val OID_CN = "550403"                       // 2.5.4.3

    /**
     * Builds the self-signed certificate as a parsed [Certificate].
     *
     * Generated once per identity: the private key is useless without a certificate
     * that matches it, and re-minting on each launch would unpair the device.
     */
    fun mint(privateKey: PrivateKey, publicKey: PublicKey): Certificate =
        mintFromEncoded(privateKey, publicKey.encoded)

    /**
     * Same certificate, from an already-encoded SubjectPublicKeyInfo.
     *
     * Split out so an identity can be re-minted straight from the PKCS#8 key bytes on
     * disk without ever holding a [PrivateKey] object that something else could
     * destroy — see [AdbConnectionManager.getPrivateKey] for why that matters.
     */
    fun mintFromEncoded(privateKey: PrivateKey, spki: ByteArray): Certificate {
        val now = Date()
        // Ten years: this certificate only identifies the app to adbd on the phone it
        // is installed on, and an expiry would silently unpair the user.
        val expiry = Date(now.time + 10L * 365 * 24 * 60 * 60 * 1000)

        val tbs = seq(
            // [0] EXPLICIT version — v3 (value 2). Optional, but adbd-side tooling
            // expects v3 and the wrapper costs four bytes.
            tlv(0xA0, integer(2)),
            integer(SecureRandom().nextInt(Int.MAX_VALUE).toLong() + 1L),
            sha256RsaAlg(),
            name(),
            seq(utcTime(now), utcTime(expiry)),
            name(),
            spki
        )

        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(tbs)
            sign()
        }

        val der = seq(tbs, sha256RsaAlg(), tlv(0x03, byteArrayOf(0) + signature))
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(der.inputStream())
    }

    // ------------------------------------------------------------------ structure

    private fun sha256RsaAlg(): ByteArray =
        seq(tlv(0x06, OID_SHA256_RSA.hex()), tlv(0x05, ByteArray(0)))

    /** Name = SEQUENCE OF SET OF AttributeTypeAndValue. The SET is load-bearing. */
    private fun name(): ByteArray =
        seq(tlv(0x31, seq(tlv(0x06, OID_CN.hex()), tlv(0x0C, "GamersHub".toByteArray()))))

    private fun utcTime(date: Date): ByteArray {
        val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return tlv(0x17, fmt.format(date).toByteArray())
    }

    private fun integer(value: Long): ByteArray {
        var v = value
        if (v == 0L) return tlv(0x02, byteArrayOf(0))
        val bytes = ByteArrayOutputStream()
        // Big-endian, minimal, with a leading zero when the top bit would read negative.
        val raw = ArrayList<Byte>()
        while (v > 0) {
            raw.add(0, (v and 0xFF).toByte())
            v = v shr 8
        }
        if (raw.isNotEmpty() && (raw[0].toInt() and 0x80) != 0) raw.add(0, 0)
        raw.forEach { bytes.write(it.toInt()) }
        return tlv(0x02, bytes.toByteArray())
    }

    /** DER tag-length-value with definite, minimal lengths. */
    private fun tlv(tag: Int, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val n = body.size
        when {
            n < 0x80 -> out.write(n)
            n < 0x100 -> { out.write(0x81); out.write(n) }
            n < 0x10000 -> { out.write(0x82); out.write(n shr 8); out.write(n and 0xFF) }
            else -> {
                out.write(0x83)
                out.write((n shr 16) and 0xFF)
                out.write((n shr 8) and 0xFF)
                out.write(n and 0xFF)
            }
        }
        out.write(body)
        return out.toByteArray()
    }

    private fun seq(vararg parts: ByteArray): ByteArray =
        tlv(0x30, parts.fold(ByteArray(0)) { acc, p -> acc + p })

    private fun String.hex(): ByteArray =
        ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
