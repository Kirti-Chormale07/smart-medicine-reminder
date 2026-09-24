package com.example.mediremind.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * One reminder time.
 * meal: 0 = fixed time, 1 = breakfast, 2 = lunch, 3 = evening snack, 4 = dinner.
 * hour/minute are used when meal == 0, offset (minutes relative to the meal) otherwise.
 */
data class DoseTime(
    var meal: Int = 0,
    var hour: Int = 8,
    var minute: Int = 0,
    var offset: Int = 0
)

data class Medicine(
    val id: Long,
    val name: String,
    val dose: String,
    val photoPath: String?,
    val stock: Int,
    val doses: List<DoseTime>
)

data class DoseLog(
    val id: Long,
    val medId: Long,
    val medName: String,
    val dose: String,
    val scheduledAt: Long,
    val status: Int,
    val takenAt: Long,
    val guardianNotified: Int
)

object Status {
    const val PENDING = 0
    const val TAKEN = 1
    const val MISSED = 2
    const val SNOOZED = 3
}

class Db(context: Context) : SQLiteOpenHelper(context.applicationContext, "mediremind.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE medicines (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "dose TEXT," +
                "photo_path TEXT," +
                "stock INTEGER DEFAULT 0," +
                "active INTEGER DEFAULT 1," +
                "doses TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE dose_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "med_id INTEGER NOT NULL," +
                "med_name TEXT NOT NULL," +
                "dose TEXT," +
                "scheduled_at INTEGER NOT NULL," +
                "status INTEGER DEFAULT 0," +
                "taken_at INTEGER DEFAULT 0," +
                "guardian_notified INTEGER DEFAULT 0," +
                "UNIQUE(med_id, scheduled_at))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS medicines")
        db.execSQL("DROP TABLE IF EXISTS dose_log")
        onCreate(db)
    }

    // ---------- medicines ----------

    fun allMedicines(): List<Medicine> {
        val out = ArrayList<Medicine>()
        readableDatabase.rawQuery(
            "SELECT id,name,dose,photo_path,stock,doses FROM medicines WHERE active=1 ORDER BY name",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Medicine(
                        id = c.getLong(0),
                        name = c.getString(1),
                        dose = c.getString(2) ?: "",
                        photoPath = c.getString(3),
                        stock = c.getInt(4),
                        doses = decodeDoses(c.getString(5) ?: "[]")
                    )
                )
            }
        }
        return out
    }

    fun getMedicine(id: Long): Medicine? {
        readableDatabase.rawQuery(
            "SELECT id,name,dose,photo_path,stock,doses FROM medicines WHERE id=?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                return Medicine(
                    id = c.getLong(0),
                    name = c.getString(1),
                    dose = c.getString(2) ?: "",
                    photoPath = c.getString(3),
                    stock = c.getInt(4),
                    doses = decodeDoses(c.getString(5) ?: "[]")
                )
            }
        }
        return null
    }

    /** Returns new id for insert; 0 for update. */
    fun saveMedicine(m: Medicine): Long {
        val cv = ContentValues().apply {
            put("name", m.name)
            put("dose", m.dose)
            put("photo_path", m.photoPath ?: "")
            put("stock", m.stock)
            put("doses", encodeDoses(m.doses))
        }
        return if (m.id <= 0) {
            writableDatabase.insert("medicines", null, cv)
        } else {
            writableDatabase.update("medicines", cv, "id=?", arrayOf(m.id.toString()))
            0
        }
    }

    fun deleteMedicine(id: Long) {
        writableDatabase.delete("dose_log", "med_id=?", arrayOf(id.toString()))
        writableDatabase.delete("medicines", "id=?", arrayOf(id.toString()))
    }

    fun adjustStock(id: Long, delta: Int) {
        writableDatabase.execSQL(
            "UPDATE medicines SET stock = MAX(0, stock + ?) WHERE id = ?",
            arrayOf<Any>(delta, id)
        )
    }

    // ---------- dose log ----------

    /** Inserts a PENDING row if none exists for (med, time); returns the current status. */
    fun ensureLog(med: Medicine, at: Long): Int {
        val cv = ContentValues().apply {
            put("med_id", med.id)
            put("med_name", med.name)
            put("dose", med.dose)
            put("scheduled_at", at)
            put("status", Status.PENDING)
        }
        writableDatabase.insertWithOnConflict("dose_log", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        return logStatus(med.id, at) ?: Status.PENDING
    }

    fun logStatus(medId: Long, at: Long): Int? {
        readableDatabase.rawQuery(
            "SELECT status FROM dose_log WHERE med_id=? AND scheduled_at=?",
            arrayOf(medId.toString(), at.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return null
    }

    fun updateLog(medId: Long, at: Long, status: Int, takenAt: Long = 0, guardianNotified: Int = -1) {
        val cv = ContentValues().apply {
            put("status", status)
            if (status == Status.TAKEN) put("taken_at", if (takenAt > 0) takenAt else System.currentTimeMillis())
            if (guardianNotified >= 0) put("guardian_notified", guardianNotified)
        }
        writableDatabase.update("dose_log", cv, "med_id=? AND scheduled_at=?", arrayOf(medId.toString(), at.toString()))
    }

    fun logsBetween(fromInclusive: Long, toExclusive: Long): List<DoseLog> {
        val out = ArrayList<DoseLog>()
        readableDatabase.rawQuery(
            "SELECT id,med_id,med_name,dose,scheduled_at,status,taken_at,guardian_notified " +
                "FROM dose_log WHERE scheduled_at>=? AND scheduled_at<? ORDER BY scheduled_at DESC",
            arrayOf(fromInclusive.toString(), toExclusive.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    DoseLog(
                        id = c.getLong(0),
                        medId = c.getLong(1),
                        medName = c.getString(2),
                        dose = c.getString(3) ?: "",
                        scheduledAt = c.getLong(4),
                        status = c.getInt(5),
                        takenAt = c.getLong(6),
                        guardianNotified = c.getInt(7)
                    )
                )
            }
        }
        return out
    }

    // ---------- json ----------

    private fun encodeDoses(list: List<DoseTime>): String {
        val arr = JSONArray()
        for (d in list) {
            val o = JSONObject()
            o.put("meal", d.meal)
            o.put("h", d.hour)
            o.put("m", d.minute)
            o.put("off", d.offset)
            arr.put(o)
        }
        return arr.toString()
    }

    private fun decodeDoses(json: String): List<DoseTime> {
        val out = ArrayList<DoseTime>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    DoseTime(
                        meal = o.optInt("meal", 0),
                        hour = o.optInt("h", 8),
                        minute = o.optInt("m", 0),
                        offset = o.optInt("off", 0)
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }
}
