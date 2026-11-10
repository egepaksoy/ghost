package com.egepaksoy.ghost.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.egepaksoy.ghost.core.Fix
import com.egepaksoy.ghost.core.PlaceDef
import kotlinx.coroutines.flow.MutableStateFlow

/** Everything is stored locally on the phone in one small SQLite file. */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "ghost.db", null, 1) {

    /** Bumped on every write so the UI knows when to reload. */
    val version = MutableStateFlow(0L)
    private fun changed() { version.value++ }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE points(id INTEGER PRIMARY KEY, time INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, acc REAL NOT NULL)")
        db.execSQL("CREATE INDEX points_time ON points(time)")
        db.execSQL("CREATE TABLE places(id INTEGER PRIMARY KEY, name TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, radius REAL NOT NULL)")
        db.execSQL("CREATE TABLE geocache(key TEXT PRIMARY KEY, label TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun insertPoint(f: Fix) {
        writableDatabase.insert("points", null, ContentValues().apply {
            put("time", f.time); put("lat", f.lat); put("lon", f.lon); put("acc", f.accuracy)
        })
        changed()
    }

    fun pointsBetween(from: Long, to: Long): List<Fix> {
        val out = ArrayList<Fix>()
        forEachPoint(from, to) { out += it }
        return out
    }

    fun forEachPoint(from: Long = 0, to: Long = Long.MAX_VALUE, block: (Fix) -> Unit) {
        readableDatabase.rawQuery(
            "SELECT time, lat, lon, acc FROM points WHERE time >= ? AND time < ? ORDER BY time",
            arrayOf(from.toString(), to.toString()),
        ).use { c ->
            while (c.moveToNext()) block(Fix(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getFloat(3)))
        }
    }

    fun pointCount(): Long =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM points", null).use { it.moveToFirst(); it.getLong(0) }

    fun lastPoint(): Fix? =
        readableDatabase.rawQuery("SELECT time, lat, lon, acc FROM points ORDER BY time DESC LIMIT 1", null).use { c ->
            if (c.moveToFirst()) Fix(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getFloat(3)) else null
        }

    fun places(): List<PlaceDef> {
        val out = ArrayList<PlaceDef>()
        readableDatabase.rawQuery("SELECT id, name, lat, lon, radius FROM places ORDER BY name COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) out += PlaceDef(c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getDouble(4))
        }
        return out
    }

    fun addPlace(name: String, lat: Double, lon: Double, radius: Double) {
        writableDatabase.insert("places", null, ContentValues().apply {
            put("name", name); put("lat", lat); put("lon", lon); put("radius", radius)
        })
        changed()
    }

    fun updatePlace(id: Long, name: String, radius: Double) {
        writableDatabase.update("places", ContentValues().apply { put("name", name); put("radius", radius) }, "id = ?", arrayOf(id.toString()))
        changed()
    }

    fun deletePlace(id: Long) {
        writableDatabase.delete("places", "id = ?", arrayOf(id.toString()))
        changed()
    }

    fun cachedAddress(key: String): String? =
        readableDatabase.rawQuery("SELECT label FROM geocache WHERE key = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun cacheAddress(key: String, label: String) {
        writableDatabase.insertWithOnConflict("geocache", null, ContentValues().apply {
            put("key", key); put("label", label)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteAllPoints() {
        writableDatabase.delete("points", null, null)
        changed()
    }

    companion object {
        @Volatile private var instance: Db? = null
        fun get(ctx: Context): Db = instance ?: synchronized(this) {
            instance ?: Db(ctx.applicationContext).also { instance = it }
        }
    }
}
