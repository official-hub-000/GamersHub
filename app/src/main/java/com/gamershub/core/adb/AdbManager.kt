package com.gamershub.core.adb

import android.content.Context
import android.os.Build
import android.util.Base64
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.LocalServices
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit

/**
 * One ADB connection over Wireless debugging, holding the loopback listening port.
 *
 * ## Why this is a separate identity from DFRoot / Shizuku
 *
 * Wireless debugging runs `adbd` as uid 2000 — the *shell* user. That is exactly the
 * uid Shizuku's server runs as, which is why this can stand in for Shizuku rather
 * than being a weaker imitation of it: same user, same SELinux domain, same
 * reach into `settings`, `cmd`, `dumpsys`, `wm`, `am`, `pm`.
 *
 * ## The connection is not the hard part
 *
 * Pairing is. Android 11+ refuses `connect` to an unpaired adbd, and pairing is a
 * TLS tunnel plus SPAKE2 over the six-digit code — which is why this class delegates
 * to the vendored Apache-2.0 libadb rather than re-implementing a protocol that
 * cannot be tested without a device.
 *
 * ## Identity is the other hard part
 *
 * adbd authenticates the client by a self-signed X.509 certificate, not by a public
 * key file. Android has no `sun.security.x509`, so the certificate is minted through
 * apksig ([AdbIdentity]) and stored alongside the key.
 */
/** Android 10 (Q) is the floor: the pairing tunnel is TLS 1.3, which the platform
 *  only provides from Q onwards. Below that, the reference implementation's own
 *  `SslUtils` throws rather than silently falling back. */
val ADB_MIN_SDK = Build.VERSION_CODES.Q

fun wirelessSupported(): Boolean = Build.VERSION.SDK_INT >= ADB_MIN_SDK

class AdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {

    private val appContext = context.applicationContext

    /**
     * The identity is re-read from its files on every call instead of being cached.
     *
     * This is not paranoia, it is the only way to survive the vendored library:
     *
     * ```
     * AbsAdbConnectionManager.getAdbKeyPair()  -> new KeyPair(getPrivateKey(), …)
     * KeyPair.destroy()                        -> mPrivateKey.destroy()
     * AdbConnection.close()                    -> mKeyPair.destroy()
     * AbsAdbConnectionManager.disconnect()     -> mAdbConnection.close()
     * ```
     *
     * `getAdbKeyPair()` wraps *the object this method returns*, and `disconnect()`
     * destroys it. Caching one `PrivateKey` on this singleton therefore meant the
     * first DISCONNECT destroyed the app's ADB identity for the rest of the process:
     * the manager kept handing out a destroyed key, the client could no longer sign,
     * and every later connect failed in a way that looks exactly like "not paired" —
     * which re-pairing cannot fix, because the fault is on our side of the wire.
     *
     * The bytes on disk are untouched by `destroy()`, so parsing a fresh key and
     * re-using the stored certificate gives the library a throwaway object each time.
     * The certificate is the identity adbd authorises, so it must stay byte-identical;
     * it is only re-minted when it is missing.
     */
    override fun getPrivateKey(): PrivateKey = AdbIdentity.freshPrivateKey(appContext)

    override fun getCertificate(): Certificate = AdbIdentity.certificate(appContext)

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(12, TimeUnit.SECONDS)
        // Materialise the identity once up front so the first connect does not pay for
        // key generation inside a handshake timeout.
        AdbIdentity.load(appContext)
        // A connection left over from before a reboot can still pass isConnected() —
        // see WirelessAdb's class comment — so a fresh process starts with none.
        runCatching { disconnect() }
    }

    /** Name shown in the "Allow debugging?" prompt on the device. */
    override fun getDeviceName(): String = "GamersHub"

    /**
     * Forgets the key pair and certificate.
     *
     * Needed when the user cleared the authorised-device list on the phone: adbd
     * then rejects this identity forever, and re-using it would loop on "unauthorised".
     * Generating a fresh one is the only recovery, and it is what Shizuku does too.
     */
    fun resetIdentity() {
        AdbIdentity.clear(appContext)
        AdbIdentity.load(appContext)
    }

    companion object {
        @Volatile
        private var instance: AdbConnectionManager? = null

        fun get(context: Context): AdbConnectionManager =
            instance ?: synchronized(this) {
                instance ?: AdbConnectionManager(context).also { instance = it }
            }
    }
}

/**
 * The persistent ADB identity: RSA private key plus a self-signed certificate.
 *
 * Both are stored as files in the app's private directory, which is the same
 * arrangement Shizuku and the adb CLI use — the key is the login, so it must survive
 * restarts or every app launch would need a new pairing.
 */
object AdbIdentity {

    private const val KEY_FILE = "gh_adb_private.key"
    private const val CERT_FILE = "gh_adb_cert.pem"

    /** Generate-on-first-use; deterministic afterwards until [clear]. */
    fun load(context: Context): Pair<PrivateKey, Certificate> {
        val keyFile = File(context.filesDir, KEY_FILE)
        val certFile = File(context.filesDir, CERT_FILE)
        if (keyFile.exists() && certFile.exists()) {
            runCatching { return readPrivateKey(keyFile) to readCertificate(certFile) }
        }
        return generate(context)
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, KEY_FILE).delete() }
        runCatching { File(context.filesDir, CERT_FILE).delete() }
    }

    /**
     * A new [PrivateKey] object parsed from the stored PKCS#8 bytes.
     *
     * Never cached: the vendored library destroys whatever object it is handed when a
     * connection is closed (see [AdbConnectionManager.getPrivateKey]), so each caller
     * gets its own. Destroying it costs nothing — the file it came from is untouched.
     */
    fun freshPrivateKey(context: Context): PrivateKey {
        val keyFile = File(context.filesDir, KEY_FILE)
        if (!keyFile.exists()) load(context)
        return readPrivateKey(keyFile)
    }

    /**
     * The authorised certificate.
     *
     * Re-minted from the stored key only when the file is missing. It must stay
     * byte-identical to the one adbd paired with, so it is never regenerated "just in
     * case" — a new certificate is a new device as far as adbd is concerned.
     */
    fun certificate(context: Context): Certificate {
        val certFile = File(context.filesDir, CERT_FILE)
        if (certFile.exists()) {
            return runCatching { readCertificate(certFile) }.getOrElse { reissue(context) }
        }
        return reissue(context)
    }

    private fun reissue(context: Context): Certificate {
        val key = freshPrivateKey(context)
        val cert = AdbCert.mintFromEncoded(key, publicKeyOf(key))
        runCatching { File(context.filesDir, CERT_FILE).writeBytes(cert.encoded) }
        return cert
    }

    /**
     * The public half, derived from the private key's PKCS#8 encoding.
     *
     * Only needed on the repair path (certificate file lost). RSA PKCS#8 ends with the
     * SubjectPublicKeyInfo, but rather than slice DER by hand the key material is
     * re-derived through a KeyFactory round trip.
     */
    private fun publicKeyOf(private: PrivateKey): ByteArray {
        return (private as? RSAPrivateCrtKey)?.let { crt ->
            java.security.KeyFactory.getInstance("RSA")
                .generatePublic(
                    java.security.spec.RSAPublicKeySpec(crt.modulus, crt.publicExponent)
                ).encoded
        } ?: throw IllegalStateException("cannot derive public key from ${private.algorithm}")
    }

    private fun generate(context: Context): Pair<PrivateKey, Certificate> {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val pair = generator.generateKeyPair()

        // apksig signs a PKCS#8 key into a self-signed X.509 certificate. This is the
        // same code path that signs APKs, so it is present on every Android runtime
        // the AGP supports, unlike sun.security.x509.
        val cert = AdbCert.mint(pair.private, pair.public)

        runCatching {
            File(context.filesDir, KEY_FILE).writeBytes(pair.private.encoded)
            File(context.filesDir, CERT_FILE).writeBytes(
                Base64.encode(
                    cert.encoded,
                    Base64.NO_WRAP or Base64.NO_PADDING
                ).decodeToString().toByteArray()
            )
        }
        return pair.private to cert
    }

    private fun readPrivateKey(file: File): PrivateKey =
        KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(file.readBytes()))

    private fun readCertificate(file: File): Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(file.inputStream())
}

/**
 * Command execution over an [AdbStream].
 *
 * ADB's `shell:` service does not report the exit code in the raw stream, so the
 * command is wrapped exactly the way [com.gamershub.core.shizuku.Shell] already wraps
 * its own: append `; echo __GH_EXIT__=$?` and parse it back out. Keeping the sentinel
 * identical is what lets every existing call site keep working untouched.
 *
 * Reads block until data arrives or the daemon closes the stream (see
 * `AdbStream.read`), so a watchdog is the only way to bound a wedged command. A
 * silent, frozen shell is far worse than a timed-out one.
 */
internal object AdbExec {

    const val EXIT_TOKEN = "__GH_EXIT__"

    fun run(manager: AdbConnectionManager, command: String, timeoutMs: Long = 12_000): String? {
        if (!manager.isConnected) return null
        return try {
            val stream = manager.openStream(
                LocalServices.SHELL,
                "sh", "-c", "$command; echo $EXIT_TOKEN=\$?"
            )
            stream.use { read(it, timeoutMs) }
        } catch (t: Throwable) {
            null
        }
    }

    private fun read(stream: AdbStream, timeoutMs: Long): String {
        val out = StringBuilder()
        val buf = ByteArray(8192)
        // Blocking read on a worker; the caller waits on the join with a hard cap so a
        // daemon that never closes the stream cannot hang a settings toggle.
        val worker = Thread {
            runCatching {
                while (true) {
                    val n = stream.read(buf, 0, buf.size)
                    if (n < 0) break
                    if (n > 0) out.append(String(buf, 0, n, Charsets.UTF_8))
                }
            }
        }
        worker.isDaemon = true
        worker.start()
        worker.join(timeoutMs)
        return out.toString()
    }
}
