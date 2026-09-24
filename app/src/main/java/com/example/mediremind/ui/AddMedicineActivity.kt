package com.example.mediremind.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.mediremind.R
import com.example.mediremind.data.Db
import com.example.mediremind.data.DoseTime
import com.example.mediremind.data.Medicine
import com.example.mediremind.util.ImageUtil
import com.example.mediremind.util.LocaleHelper
import com.example.mediremind.util.Scheduler
import com.example.mediremind.util.TimeFmt
import com.google.android.material.button.MaterialButton
import java.io.File

class AddMedicineActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MED_ID = "med_id"
        private val OFFSET_VALUES = intArrayOf(0, -30, -15, 15, 30, 60)
    }

    private lateinit var edtName: EditText
    private lateinit var edtDose: EditText
    private lateinit var edtStock: EditText
    private lateinit var imgPreview: ImageView
    private lateinit var doseContainer: android.widget.LinearLayout

    private var photoPath: String? = null
    private var pendingFile: File? = null
    private var editing: Medicine? = null

    private val rows = ArrayList<DoseRow>()

    private class DoseRow(val view: View) {
        val spnMeal: Spinner = view.findViewById(R.id.spnMeal)
        val spnOffset: Spinner = view.findViewById(R.id.spnOffset)
        val btnTime: Button = view.findViewById(R.id.btnTime)
        val btnRemove: Button = view.findViewById(R.id.btnRemoveRow)
        val data = DoseTime()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) {
                photoPath = pendingFile?.absolutePath
                showPhoto()
            }
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { copyIntoPhotoDir(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_medicine)

        edtName = findViewById(R.id.edtName)
        edtDose = findViewById(R.id.edtDose)
        edtStock = findViewById(R.id.edtStock)
        imgPreview = findViewById(R.id.imgPreview)
        doseContainer = findViewById(R.id.doseContainer)

        val title: android.widget.TextView = findViewById(R.id.txtTitle)

        val medId = intent.getLongExtra(EXTRA_MED_ID, -1)
        if (medId > 0) {
            val med = Db(this).getMedicine(medId)
            if (med != null) {
                editing = med
                title.setText(R.string.edit_title)
                edtName.setText(med.name)
                edtDose.setText(med.dose)
                edtStock.setText(med.stock.toString())
                photoPath = med.photoPath
                showPhoto()
                findViewById<MaterialButton>(R.id.btnDelete).visibility = View.VISIBLE
                if (med.doses.isEmpty()) addRow(null) else med.doses.forEach { addRow(it) }
            }
        }
        if (editing == null) addRow(null)

        findViewById<MaterialButton>(R.id.btnCamera).setOnClickListener { openCamera() }
        findViewById<MaterialButton>(R.id.btnGallery).setOnClickListener {
            pickImage.launch("image/*")
        }
        findViewById<MaterialButton>(R.id.btnAddTime).setOnClickListener { addRow(null) }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }
        findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener { confirmDelete() }
    }

    // ---------- photo ----------

    private fun newPhotoFile(): File {
        val dir = File(filesDir, "med_photos")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "med_${System.currentTimeMillis()}.jpg")
    }

    private fun openCamera() {
        val file = newPhotoFile()
        pendingFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        takePicture.launch(uri)
    }

    private fun copyIntoPhotoDir(src: Uri) {
        try {
            val file = newPhotoFile()
            contentResolver.openInputStream(src)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            photoPath = file.absolutePath
            showPhoto()
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPhoto() {
        val bmp = ImageUtil.decodeScaled(photoPath, 300)
        if (bmp != null) imgPreview.setImageBitmap(bmp)
        else imgPreview.setImageResource(R.drawable.ic_pill_placeholder)
    }

    // ---------- dose rows ----------

    private fun addRow(initial: DoseTime?) {
        val row = DoseRow(LayoutInflater.from(this).inflate(R.layout.item_dose_edit, doseContainer, false))
        initial?.let {
            row.data.meal = it.meal
            row.data.hour = it.hour
            row.data.minute = it.minute
            row.data.offset = it.offset
        }

        row.spnMeal.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.meals)
        )
        row.spnOffset.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.offsets)
        )

        // Restore saved selection.
        row.spnMeal.setSelection(row.data.meal)
        val offIndex = OFFSET_VALUES.indexOf(row.data.offset).coerceAtLeast(0)
        row.spnOffset.setSelection(offIndex)

        row.spnMeal.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long
            ) {
                row.data.meal = pos
                updateRowUi(row)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        row.spnOffset.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long
            ) {
                if (pos in OFFSET_VALUES.indices) row.data.offset = OFFSET_VALUES[pos]
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        row.btnTime.setOnClickListener { pickTime(row) }
        row.btnRemove.setOnClickListener {
            if (rows.size <= 1) {
                Toast.makeText(this, R.string.err_time, Toast.LENGTH_SHORT).show()
            } else {
                doseContainer.removeView(row.view)
                rows.remove(row)
            }
        }

        rows.add(row)
        doseContainer.addView(row.view)
        updateRowUi(row)
    }

    private fun updateRowUi(row: DoseRow) {
        if (row.data.meal == 0) {
            row.spnOffset.visibility = View.GONE
            row.btnTime.isEnabled = true
            row.btnTime.text = getString(R.string.btn_pick_time, timeLabel(row.data.hour, row.data.minute))
        } else {
            row.spnOffset.visibility = View.VISIBLE
            row.spnOffset.setSelection(OFFSET_VALUES.indexOf(row.data.offset).coerceAtLeast(0))
            row.btnTime.isEnabled = false
            row.btnTime.text = getString(R.string.btn_time_auto)
        }
    }

    private fun timeLabel(h: Int, m: Int): String {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, h)
            set(java.util.Calendar.MINUTE, m)
        }
        return DateFormat.getTimeFormat(this).format(cal.time)
    }

    private fun pickTime(row: DoseRow) {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                row.data.hour = hour
                row.data.minute = minute
                row.btnTime.text = getString(R.string.btn_pick_time, timeLabel(hour, minute))
            },
            row.data.hour, row.data.minute,
            DateFormat.is24HourFormat(this)
        ).show()
    }

    // ---------- save / delete ----------

    private fun save() {
        val name = edtName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.err_name, Toast.LENGTH_SHORT).show()
            return
        }
        val doses = rows.map {
            DoseTime(it.data.meal, it.data.hour, it.data.minute, it.data.offset)
        }
        if (doses.isEmpty()) {
            Toast.makeText(this, R.string.err_time, Toast.LENGTH_SHORT).show()
            return
        }
        val stock = edtStock.text.toString().toIntOrNull() ?: 0

        val db = Db(this)
        val existing = editing
        val med = Medicine(
            id = existing?.id ?: 0,
            name = name,
            dose = edtDose.text.toString().trim(),
            photoPath = photoPath,
            stock = stock,
            doses = doses
        )
        db.saveMedicine(med)
        Scheduler.scheduleNextTwoDays(this)
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        val med = editing ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_msg)
            .setPositiveButton(R.string.yes) { _, _ ->
                Db(this).deleteMedicine(med.id)
                Scheduler.cancelMedicine(this, med)
                Scheduler.scheduleNextTwoDays(this)
                Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // Suppress unused warning for helper kept for parity with TimeFmt usage.
    @Suppress("unused")
    private fun rel(d: DoseTime): String? = TimeFmt.relLabel(this, d)
}
