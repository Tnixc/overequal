package dev.overequal.bot

import com.github.luben.zstd.Zstd
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Uploads files to [litterbox](https://litterbox.catbox.moe) — a no-auth,
 * temporary-by-design file host. Every upload is given the shortest lifetime
 * litterbox offers, `time=1h`, after which it is deleted automatically.
 *
 * The transport is a plain HTTPS multipart POST via the JDK [HttpClient], so
 * the only runtime requirement is outbound network access: no SSH keys, no
 * registered account, no external binaries on `PATH`. A request timeout keeps
 * the calling coroutine from blocking forever if litterbox stalls.
 */
object Export {
    private val log = LoggerFactory.getLogger(Export::class.java)

    private const val ENDPOINT = "https://litterbox.catbox.moe/resources/internals/api.php"

    /** litterbox only accepts 1h / 12h / 24h / 72h; we always use the shortest. */
    private const val EXPIRY = "1h"

    private val http =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build()

    /** zstd compression level; 19 trades CPU for a smaller corpus (max is 22). */
    private const val ZSTD_LEVEL = 19

    /** AES-GCM key size in bits and the authentication-tag length in bits. */
    private const val AES_KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    /** AES-GCM nonce length in bytes (the standard 96-bit IV). */
    private const val GCM_NONCE_BYTES = 12

    private val random = SecureRandom()

    /** The result of encrypting a blob: ciphertext plus the base64 key that unlocks it. */
    data class Encrypted(
        /** `nonce || ciphertext || tag` — the nonce is prefixed so decryption is self-contained. */
        val payload: ByteArray,
        /** Base64-encoded 256-bit AES key. Share this out-of-band from the ciphertext. */
        val keyBase64: String,
    )

    /** Compress [content] with zstd (level [ZSTD_LEVEL]). */
    fun compress(content: ByteArray): ByteArray = Zstd.compress(content, ZSTD_LEVEL)

    /**
     * Encrypt [content] under a freshly generated random AES-256 key using
     * AES/GCM. The returned [Encrypted.payload] is `nonce || ciphertext+tag`,
     * so a holder of [Encrypted.keyBase64] can decrypt it standalone, e.g.:
     *
     * ```
     * key   = base64-decode(keyBase64)                # 32 bytes
     * nonce = payload[0:12]; ct = payload[12:]
     * plaintext = AES-GCM-decrypt(key, nonce, ct)     # tag is the last 16 bytes of ct
     * # then zstd-decompress plaintext
     * ```
     */
    fun encrypt(content: ByteArray): Encrypted {
        val key = KeyGenerator.getInstance("AES").apply { init(AES_KEY_BITS) }.generateKey()
        val nonce = ByteArray(GCM_NONCE_BYTES).also(random::nextBytes)
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.encoded, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            }
        val ciphertext = cipher.doFinal(content)
        val payload = ByteArray(nonce.size + ciphertext.size)
        System.arraycopy(nonce, 0, payload, 0, nonce.size)
        System.arraycopy(ciphertext, 0, payload, nonce.size, ciphertext.size)
        return Encrypted(payload, Base64.getEncoder().encodeToString(key.encoded))
    }

    /**
     * Upload [content] to litterbox under [filename] and return its URL.
     * The file is deleted by litterbox one hour after upload.
     */
    fun upload(
        content: ByteArray,
        filename: String,
    ): Result<String> =
        runCatching {
            val boundary = "overequal" + UUID.randomUUID().toString().replace("-", "")
            val request =
                HttpRequest
                    .newBuilder(URI.create(ENDPOINT))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "multipart/form-data; boundary=$boundary")
                    .header("User-Agent", "overequal-bot")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary, content, filename)))
                    .build()

            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            val body = response.body().trim()
            if (response.statusCode() != 200) {
                error("litterbox HTTP ${response.statusCode()}: ${body.take(512)}")
            }
            if (!body.startsWith("https://")) {
                error("litterbox returned an unexpected response: ${body.take(512)}")
            }
            log.info("exported {} ({} bytes) to {}", filename, content.size, body)
            body
        }

    /** Builds a `multipart/form-data` body with litterbox's upload fields. */
    private fun multipartBody(
        boundary: String,
        content: ByteArray,
        filename: String,
    ): ByteArray {
        val out = ByteArrayOutputStream()

        fun ascii(s: String) = out.write(s.toByteArray(Charsets.UTF_8))

        fun field(
            name: String,
            value: String,
        ) {
            ascii("--$boundary\r\n")
            ascii("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
            ascii("$value\r\n")
        }

        field("reqtype", "fileupload")
        field("time", EXPIRY)
        ascii("--$boundary\r\n")
        ascii("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"$filename\"\r\n")
        ascii("Content-Type: application/octet-stream\r\n\r\n")
        out.write(content)
        ascii("\r\n--$boundary--\r\n")
        return out.toByteArray()
    }
}
