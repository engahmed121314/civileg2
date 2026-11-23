package com.civileg.app.viewmodel

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.GsonBuilder
import java.io.File
import java.io.IOException

/**
 * User-initiated export of the retained old frame draft strings. The strings are JSON string
 * values inside an envelope, so Gson escaping round-trips them byte-exact. Nothing here touches
 * preferences, and an existing file is never overwritten.
 */
internal object FrameDraftRawExport {
    const val FORMAT = "civileg-frame-draft-raw-export-1"

    fun build(raws: Map<String, String>, appVersion: String, exportedAtMillis: Long): String {
        val o = JsonObject()
        o.addProperty("format", FORMAT)
        o.addProperty("appVersion", appVersion)
        o.addProperty("exportedAtMillis", exportedAtMillis)
        val r = JsonObject()
        raws.forEach { (k, v) -> r.addProperty(k, v) }
        o.add("raws", r)
        return GsonBuilder().disableHtmlEscaping().create().toJson(o)
    }

    /** Parses an envelope back to key -> raw (used for verification). */
    fun parse(text: String): Map<String, String> {
        val r = JsonParser.parseString(text).asJsonObject.getAsJsonObject("raws")
        return r.entrySet().associate { it.key to it.value.asString }
    }

    /**
     * Writes [text] to a NEW file under [dir]; never overwrites. Verifies read-back equals the
     * bytes written. Throws IOException on any failure (a partial file is removed).
     */
    fun writeNew(dir: File, text: String, nowMillis: Long, expected: Map<String, String>? = null): File {
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("no export dir")
        var n = 0
        var f: File
        do {
            f = File(dir, "frame-draft-old-copy-" + nowMillis + (if (n == 0) "" else "-$n") + ".json")
            n++
            if (n > 1000) throw IOException("no free name")
        } while (!f.createNewFile())
        try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            f.writeBytes(bytes)
            val back = f.readBytes()
            if (!back.contentEquals(bytes)) throw IOException("read-back mismatch")
            // Parse the written envelope and compare the stored Strings (UTF-8 of the stored String,
            // not the original XML bytes) before any grant or share.
            if (expected != null && parse(String(back, Charsets.UTF_8)) != expected) throw IOException("envelope mismatch")
            return f
        } catch (e: Exception) {
            f.delete(); if (e is IOException) throw e else throw IOException(e)
        }
    }
}
