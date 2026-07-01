package dev.overequal.bot

import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

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
