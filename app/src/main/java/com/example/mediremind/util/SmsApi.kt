package com.example.mediremind.util

import android.util.Base64
import com.example.mediremind.data.Prefs
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Twilio REST API – sends the guardian SMS through the internet,
 * so it works even when the elder's phone has no SIM card.
 *
 * POST https://api.twilio.com/2010-04-01/Accounts/{AccountSid}/Messages.json
 * Auth: Basic base64(AccountSid:AuthToken)
 * Body: To=…&From=…&Body=…
 */
object SmsApi {

    private const val TAG = "SmsApi"

    /**
     * Normalises a phone number to E.164.
     * 10-digit Indian numbers automatically get +91.
     */
    fun toE164(raw: String): String {
        val s = raw.trim().filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' }
        if (s.isEmpty()) return s
        if (s.startsWith("+")) return s
        if (s.startsWith("00")) return "+" + s.substring(2)
        if (s.length == 10 && s.all { it.isDigit() }) return "+91$s"
        if (s.startsWith("0") && s.length == 11 && s.all { it.isDigit() }) return "+91" + s.substring(1)
        return "+$s"
    }

    /** application/x-www-form-urlencoded body (UTF-8 – safe for Hindi/Marathi/Gujarati text). */
    fun formEncode(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }

    /** True when the caretaker filled in all Twilio fields and switched the service on. */
    fun hasTwilioConfig(prefs: Prefs): Boolean =
        hasTwilioConfig(prefs.twilioEnabled, prefs.twilioSid, prefs.twilioToken, prefs.twilioFrom)

    fun hasTwilioConfig(enabled: Boolean, sid: String, token: String, from: String): Boolean =
        enabled && sid.isNotBlank() && token.isNotBlank() && from.isNotBlank()

    /** Blocking Twilio call. Returns true when Twilio accepted the message (2xx). */
    fun sendTwilio(prefs: Prefs, toRaw: String, message: String): Boolean {
        val to = toE164(toRaw)
        if (to.isEmpty()) return false
        val sid = prefs.twilioSid.trim()
        val token = prefs.twilioToken.trim()
        val from = toE164(prefs.twilioFrom)

        return try {
            val url = URL("https://api.twilio.com/2010-04-01/Accounts/$sid/Messages.json")
            val body = formEncode(mapOf("To" to to, "From" to from, "Body" to message))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                val cred = Base64.encodeToString(
                    "$sid:$token".toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                setRequestProperty("Authorization", "Basic $cred")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val ok = code in 200..299
            if (ok) {
                Log.i(TAG, "Twilio accepted message (HTTP $code)")
            } else {
                val err = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                } catch (_: Exception) {
                    null
                }
                Log.e(TAG, "Twilio HTTP $code: $err")
            }
            conn.disconnect()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Twilio request failed: ${e.message}")
            false
        }
    }
}
