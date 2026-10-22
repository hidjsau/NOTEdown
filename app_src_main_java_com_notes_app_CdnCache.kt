package com.notes.app

import android.webkit.WebResourceResponse
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The page loads KaTeX, jsPDF, pdf-lib, fonts, wasm ... from CDNs.
 * Each file is downloaded once, stored on the device, and served from disk afterwards,
 * so the app works offline after its first online launch.
 */
class CdnCache(private val dir: File) {

    init { dir.mkdirs() }

    fun get(url: String): WebResourceResponse? {
        val key = sha1(url)
        val body = File(dir, key)
        if (body.exists()) return respond(url, body)
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.instanceFollowRedirects = true
            if (c.responseCode != 200) {
                c.disconnect()
                return null
            }
            val tmp = File(dir, "$key.${System.nanoTime()}.tmp")
            c.inputStream.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
            c.disconnect()
            if (!tmp.renameTo(body)) {
                tmp.delete()
                if (!body.exists()) return null
            }
            respond(url, body)
        } catch (e: Exception) {
            null
        }
    }

    private fun respond(url: String, file: File): WebResourceResponse {
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        val mime = when (ext) {
            "js", "mjs" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "wasm" -> "application/wasm"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
        val enc = if (mime.startsWith("text/") || mime == "application/javascript" || mime == "application/json") "utf-8" else null
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Cache-Control" to "max-age=31536000"
        )
        return WebResourceResponse(mime, enc, 200, "OK", headers, FileInputStream(file))
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        val HOSTS = setOf("cdnjs.cloudflare.com", "cdn.jsdelivr.net")
    }
}
