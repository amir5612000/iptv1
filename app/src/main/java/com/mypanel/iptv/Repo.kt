package com.mypanel.iptv

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Repo {

    fun normalizeBase(base: String): String {
        var b = base.trim()
        if (!b.contains("://")) b = "http://$b"
        return b.trimEnd('/')
    }

    /** لیست کانال‌ها را از پنل به صورت JSON خام دریافت می‌کند */
    fun download(base: String, key: String): String {
        val u = URL(normalizeBase(base) + "/api/channels.php?key=" + URLEncoder.encode(key.trim(), "UTF-8"))
        val c = u.openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 90000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "MyIPTV/1.0")
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try { JSONObject(body).optString("error", "") } catch (e: Exception) { "" }
                throw Exception(if (msg.isNotEmpty()) msg else "HTTP $code")
            }
            return body
        } finally {
            c.disconnect()
        }
    }

    fun parse(body: String): List<Channel> {
        val o = JSONObject(body)
        if (!o.optBoolean("ok", false)) throw Exception(o.optString("error", "پاسخ نامعتبر از پنل"))
        val a = o.getJSONArray("channels")
        val out = ArrayList<Channel>(a.length())
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            out.add(
                Channel(
                    c.getInt("id"),
                    c.optString("name", ""),
                    c.optString("logo", ""),
                    c.optString("group", ""),
                    c.getString("url")
                )
            )
        }
        return out
    }
}
