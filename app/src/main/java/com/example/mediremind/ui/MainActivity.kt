package com.example.mediremind.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mediremind.R
import com.example.mediremind.data.Db
import com.example.mediremind.data.Prefs
import com.example.mediremind.data.Status
import com.example.mediremind.util.LocaleHelper
import com.example.mediremind.util.Scheduler
import com.example.mediremind.util.SmsHelper
import com.google.android.material.button.MaterialButton
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var adapter: DoseAdapter
    private lateinit var empty: android.view.View
    private lateinit var banner: TextView
    private lateinit var greeting: TextView
    private lateinit var adherence: TextView

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        greeting = findViewById(R.id.txtGreeting)
        adherence = findViewById(R.id.txtAdherence)
        banner = findViewById(R.id.txtMissedBanner)
        list = findViewById(R.id.listDoses)
        empty = findViewById(R.id.emptyState)

        adapter = DoseAdapter { row -> openEditor(row.medId) }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<MaterialButton>(R.id.btnAdd).setOnClickListener { openEditor(-1) }
        findViewById<MaterialButton>(R.id.btnAddEmpty).setOnClickListener { openEditor(-1) }
        findViewById<MaterialButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnReport).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnSos).setOnClickListener { sosDialog() }

        requestPermissionsIfNeeded()
        Scheduler.scheduleNextTwoDays(this)
    }

    override fun onResume() {
        super.onResume()
        // Language was changed in Settings while this screen sat in the back stack
        // → rebuild ourselves so the front page instantly shows the new language.
        if (LocaleHelper.isStale(this)) {
            recreate()
            return
        }
        refresh()
    }

    private fun refresh() {
        val prefs = Prefs(this)
        val name = prefs.patientName.ifBlank { getString(R.string.friend) }
        greeting.text = getString(R.string.greeting, name)

        val db = Db(this)
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
        val logs = db.logsBetween(sevenDaysAgo, now + 60_000L)
        val taken = logs.count { it.status == Status.TAKEN }
        val missed = logs.count { it.status == Status.MISSED }
        val total = taken + missed
        val pct = if (total == 0) 0 else (taken * 100) / total
        adherence.text = getString(R.string.week_adherence, pct)

        val anyMissedToday = logs.any {
            it.status == Status.MISSED && isSameDay(it.scheduledAt, now)
        }
        banner.visibility = if (anyMissedToday) android.view.View.VISIBLE else android.view.View.GONE

        // Build today's timeline.
        val dayStart = com.example.mediremind.util.DoseMath.dayStartOf(now, 0)
        val dayEnd = dayStart + 24L * 60 * 60 * 1000
        val rows = ArrayList<DoseAdapter.Row>()
        for (med in db.allMedicines()) {
            for (d in med.doses) {
                val at = Scheduler.instantOf(prefs, dayStart, d)
                if (at < dayStart || at >= dayEnd) continue
                val status = db.logStatus(med.id, at)
                    ?: if (now > at + prefs.graceMin * 60_000L) Status.MISSED else Status.PENDING
                rows.add(
                    DoseAdapter.Row(
                        medId = med.id,
                        name = med.name,
                        dose = med.dose,
                        photoPath = med.photoPath,
                        stock = med.stock,
                        at = at,
                        status = status,
                        relHint = com.example.mediremind.util.TimeFmt.relLabel(this, d)
                    )
                )
            }
        }
        rows.sortBy { it.at }
        adapter.submit(rows)

        val none = rows.isEmpty()
        empty.visibility = if (none) android.view.View.VISIBLE else android.view.View.GONE
        list.visibility = if (none) android.view.View.INVISIBLE else android.view.View.VISIBLE
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun openEditor(medId: Long) {
        startActivity(
            Intent(this, AddMedicineActivity::class.java)
                .putExtra(AddMedicineActivity.EXTRA_MED_ID, medId)
        )
    }

    private fun sosDialog() {
        val prefs = Prefs(this)
        if (prefs.guardian.isBlank()) {
            Toast.makeText(this, R.string.sos_no_guardian, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.sos_title)
            .setMessage(R.string.sos_msg)
            .setPositiveButton(R.string.sos_call) { _, _ ->
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${prefs.guardian}")))
            }
            .setNegativeButton(R.string.sos_sms) { _, _ ->
                SmsHelper.sendSos(this)
                Toast.makeText(this, R.string.sos_sent, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun requestPermissionsIfNeeded() {
        val wanted = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            wanted.add(Manifest.permission.SEND_SMS)
        }
        if (wanted.isNotEmpty()) {
            permLauncher.launch(wanted.toTypedArray())
        }

        // Exact alarms (Android 12+) need a one-time system opt-in.
        val prefs = Prefs(this)
        if (Build.VERSION.SDK_INT >= 31 && !prefs.askedAlarmPerm) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                prefs.askedAlarmPerm = true
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    // Keep ActivityCompat import referenced (permission rationale helper for future use).
    @Suppress("unused")
    private fun hasPerm(p: String): Boolean =
        ActivityCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
}
