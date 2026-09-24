package com.example.mediremind.ui

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mediremind.R
import com.example.mediremind.data.Prefs
import com.example.mediremind.util.LocaleHelper
import com.example.mediremind.util.Scheduler
import com.example.mediremind.util.TimeFmt
import com.google.android.material.button.MaterialButton
import java.util.Calendar

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var edtPatient: EditText
    private lateinit var edtGuardian: EditText
    private lateinit var radLang: RadioGroup
    private lateinit var spnGrace: Spinner
    private lateinit var chkTwilio: android.widget.CheckBox
    private lateinit var edtSid: EditText
    private lateinit var edtToken: EditText
    private lateinit var edtFrom: EditText

    // Meal times held locally until Save is pressed.
    private var breakfast = 0
    private var lunch = 0
    private var snack = 0
    private var dinner = 0

    private var graceValue = 15

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = Prefs(this)
        edtPatient = findViewById(R.id.edtPatient)
        edtGuardian = findViewById(R.id.edtGuardian)
        radLang = findViewById(R.id.radLang)
        spnGrace = findViewById(R.id.spnGrace)

        edtPatient.setText(prefs.patientName)
        edtGuardian.setText(prefs.guardian)

        chkTwilio = findViewById(R.id.chkTwilio)
        edtSid = findViewById(R.id.edtSid)
        edtToken = findViewById(R.id.edtToken)
        edtFrom = findViewById(R.id.edtFrom)
        chkTwilio.isChecked = prefs.twilioEnabled
        edtSid.setText(prefs.twilioSid)
        edtToken.setText(prefs.twilioToken)
        edtFrom.setText(prefs.twilioFrom)

        when (prefs.langCode()) {
            "hi" -> radLang.check(R.id.radHi)
            "mr" -> radLang.check(R.id.radMr)
            "gu" -> radLang.check(R.id.radGu)
            else -> radLang.check(R.id.radEn)
        }

        // Grace period spinner: 5/10/15/30/60 minutes.
        val minutes = resources.getStringArray(R.array.grace_options).map { it.toInt() }
        val labels = minutes.map { getString(R.string.grace_fmt, it) }
        spnGrace.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        graceValue = prefs.graceMin
        spnGrace.setSelection(minutes.indexOf(graceValue).coerceAtLeast(0))
        spnGrace.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos in minutes.indices) graceValue = minutes[pos]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        breakfast = prefs.breakfast
        lunch = prefs.lunch
        snack = prefs.snack
        dinner = prefs.dinner

        bindMealButton(R.id.btnBreakfast, R.string.lbl_breakfast_time) { breakfast = it }
        bindMealButton(R.id.btnLunch, R.string.lbl_lunch_time) { lunch = it }
        bindMealButton(R.id.btnSnack, R.string.lbl_snack_time) { snack = it }
        bindMealButton(R.id.btnDinner, R.string.lbl_dinner_time) { dinner = it }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }

        findViewById<MaterialButton>(R.id.btnAlarmPerm).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (_: Exception) {
                }
            } else {
                Toast.makeText(this, R.string.ok, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<MaterialButton>(R.id.btnBattery).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.ok, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindMealButton(buttonId: Int, labelId: Int, assign: (Int) -> Unit) {
        val button = findViewById<Button>(buttonId)
        fun refresh(value: Int) {
            button.text = "${getString(labelId)} — ${minutesToLabel(value)}"
        }
        when (buttonId) {
            R.id.btnBreakfast -> refresh(breakfast)
            R.id.btnLunch -> refresh(lunch)
            R.id.btnSnack -> refresh(snack)
            R.id.btnDinner -> refresh(dinner)
        }
        button.setOnClickListener {
            val current = when (buttonId) {
                R.id.btnBreakfast -> breakfast
                R.id.btnLunch -> lunch
                R.id.btnSnack -> snack
                else -> dinner
            }
            TimePickerDialog(
                this,
                { _, h, m ->
                    val value = h * 60 + m
                    assign(value)
                    refresh(value)
                },
                current / 60, current % 60,
                android.text.format.DateFormat.is24HourFormat(this)
            ).show()
        }
    }

    private fun minutesToLabel(minutesOfDay: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
            set(Calendar.MINUTE, minutesOfDay % 60)
        }
        return TimeFmt.time(this, cal.timeInMillis)
    }

    private fun save() {
        val phone = edtGuardian.text.toString().trim()
        if (phone.isNotEmpty() && phone.filter { it.isDigit() }.length < 10) {
            Toast.makeText(this, R.string.err_phone, Toast.LENGTH_LONG).show()
            return
        }

        // Twilio cloud SMS: all three fields required when the switch is on.
        val twOn = chkTwilio.isChecked
        val sid = edtSid.text.toString().trim()
        val token = edtToken.text.toString().trim()
        val from = edtFrom.text.toString().trim()
        if (twOn && (sid.isEmpty() || token.isEmpty() || from.isEmpty())) {
            Toast.makeText(this, R.string.err_twilio, Toast.LENGTH_LONG).show()
            return
        }

        prefs.patientName = edtPatient.text.toString().trim()
        prefs.guardian = phone
        prefs.twilioEnabled = twOn
        prefs.twilioSid = sid
        prefs.twilioToken = token
        prefs.twilioFrom = from
        prefs.graceMin = graceValue
        prefs.breakfast = breakfast
        prefs.lunch = lunch
        prefs.snack = snack
        prefs.dinner = dinner

        val newLang = when (radLang.checkedRadioButtonId) {
            R.id.radHi -> "hi"
            R.id.radMr -> "mr"
            R.id.radGu -> "gu"
            else -> "en"
        }
        val langChanged = newLang != prefs.lang
        prefs.lang = newLang

        Scheduler.scheduleNextTwoDays(this)

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        if (langChanged) {
            recreate() // re-reads locale in attachBaseContext
        }
    }
}
