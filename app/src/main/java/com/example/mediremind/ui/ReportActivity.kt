package com.example.mediremind.ui

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.mediremind.R
import com.example.mediremind.data.Db
import com.example.mediremind.data.Status
import com.example.mediremind.util.LocaleHelper
import com.example.mediremind.util.TimeFmt
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class ReportActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        val db = Db(this)
        val now = System.currentTimeMillis()
        val from = now - 7L * 24 * 60 * 60 * 1000
        val logs = db.logsBetween(from, now + 60_000L)

        val takenAll = logs.count { it.status == Status.TAKEN }
        val missedAll = logs.count { it.status == Status.MISSED }
        val total = takenAll + missedAll
        val pct = if (total == 0) 0 else (takenAll * 100) / total

        val overall = findViewById<TextView>(R.id.txtOverall)
        overall.text = getString(R.string.report_overall, pct)

        val noHistory = findViewById<TextView>(R.id.txtNoHistory)
        val container = findViewById<LinearLayout>(R.id.dayContainer)

        if (total == 0) {
            noHistory.visibility = TextView.VISIBLE
        } else {
            noHistory.visibility = TextView.GONE
            val dateFmt = DateFormat.getDateInstance(DateFormat.SHORT)

            for (dayOffset in 0..6) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, -dayOffset)
                }
                val start = cal.timeInMillis
                val end = start + 24L * 60 * 60 * 1000
                val dayLogs = logs.filter { it.scheduledAt in start until end }
                val t = dayLogs.count { it.status == Status.TAKEN }
                val m = dayLogs.count { it.status == Status.MISSED }
                if (t + m == 0) continue

                val row = TextView(this).apply {
                    text = getString(R.string.day_line, dateFmt.format(Date(start)), t, m)
                    textSize = 19f
                    setPadding(0, 20, 0, 20)
                    setTextColor(getColor(R.color.on_surface))
                    gravity = Gravity.CENTER_VERTICAL
                }
                container.addView(row)
            }

            // Missed doses where the guardian was informed.
            val missed = logs.filter {
                it.status == Status.MISSED && it.guardianNotified == 1
            }.take(10)
            for (m in missed) {
                val time = TimeFmt.time(this, m.scheduledAt)
                val line = TextView(this).apply {
                    text = "✗ ${m.medName} — $time  (${getString(R.string.guardian_informed)})"
                    textSize = 17f
                    setPadding(0, 8, 0, 8)
                    setTextColor(getColor(R.color.missed_red))
                }
                container.addView(line)
            }
        }
    }
}
