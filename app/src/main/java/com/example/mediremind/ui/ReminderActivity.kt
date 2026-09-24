package com.example.mediremind.ui

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mediremind.R
import com.example.mediremind.data.Db
import com.example.mediremind.data.Prefs
import com.example.mediremind.util.DoseActions
import com.example.mediremind.util.ImageUtil
import com.example.mediremind.util.LocaleHelper
import com.example.mediremind.util.Scheduler
import com.example.mediremind.util.TimeFmt
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Full-screen reminder shown even when the phone is locked.
 * Speaks the instruction out loud in the elder's language (TTS).
 */
class ReminderActivity : AppCompatActivity() {

    private var tts: TextToSpeech? = null
    private var pendingSpeech: String? = null
    private var speechDone = false

    private var medId: Long = -1
    private var at: Long = 0
    private var answered = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder)

        medId = intent.getLongExtra(Scheduler.EXTRA_MED_ID, -1)
        at = intent.getLongExtra(Scheduler.EXTRA_AT, 0)
        if (medId < 0 || at <= 0) {
            finish()
            return
        }

        initTts()

        lifecycleScope.launch {
            val med = withContext(Dispatchers.IO) {
                Db(this@ReminderActivity).getMedicine(medId)
            }
            if (med == null) {
                finish()
                return@launch
            }

            findViewById<TextView>(R.id.txtName).text = med.name
            findViewById<TextView>(R.id.txtDose).text = med.dose

            val rel = med.doses
                .minByOrNull {
                    kotlin.math.abs(Scheduler.instantOf(Prefs(this@ReminderActivity), at, it) - at)
                }
                ?.let { TimeFmt.relLabel(this@ReminderActivity, it) }
            val whenText = getString(R.string.rem_when, TimeFmt.time(this@ReminderActivity, at))
            findViewById<TextView>(R.id.txtWhen).text =
                if (rel != null) "$whenText • $rel" else whenText

            val img = findViewById<ImageView>(R.id.imgMed)
            val bmp = ImageUtil.decodeScaled(med.photoPath, 400)
            if (bmp != null) img.setImageBitmap(bmp)

            val patient = Prefs(this@ReminderActivity).patientName
                .ifBlank { getString(R.string.friend) }
            speak(
                getString(
                    R.string.tts_prompt, patient, med.name,
                    med.dose.ifBlank { " " }
                )
            )
        }

        findViewById<MaterialButton>(R.id.btnTook).setOnClickListener {
            if (answered) return@setOnClickListener
            answered = true
            DoseActions.taken(this, medId, at)
            speak(getString(R.string.tts_thanks))
            window.decorView.postDelayed({ finish() }, 1800)
        }

        findViewById<MaterialButton>(R.id.btnSnooze).setOnClickListener {
            if (answered) return@setOnClickListener
            answered = true
            DoseActions.snooze(this, medId, at)
            finish()
        }

        findViewById<MaterialButton>(R.id.btnSkip).setOnClickListener {
            if (answered) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle(R.string.skip_dialog_title)
                .setMessage(R.string.skip_dialog_msg)
                .setPositiveButton(R.string.btn_inform) { _, _ ->
                    answered = true
                    DoseActions.skip(this, medId, at)
                    Toast.makeText(this, R.string.sos_sent, Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val engine = tts ?: return@TextToSpeech
            val locale = Locale(Prefs(this).langCode())
            val res = engine.setLanguage(locale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.getDefault())
            }
            engine.setSpeechRate(0.9f)
            pendingSpeech?.let { text ->
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dose_reminder")
                pendingSpeech = null
                speechDone = true
            }
        }
    }

    private fun speak(text: String) {
        val engine = tts
        if (engine != null && speechDone) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dose_reminder")
        } else {
            pendingSpeech = text
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
