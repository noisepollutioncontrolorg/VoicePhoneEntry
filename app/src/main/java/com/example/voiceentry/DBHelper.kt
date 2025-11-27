package com.example.voiceentry

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Entry(val id: Long, val phone: String, val timestamp: String)

class DBHelper(context: Context) : SQLiteOpenHelper(context, "entries.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, phone TEXT UNIQUE, timestamp TEXT);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS entries")
        onCreate(db)
    }

    fun insertPhone(phone: String, timestamp: String): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("phone", phone)
            put("timestamp", timestamp)
        }
        val id = db.insertWithOnConflict("entries", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        return id != -1L
    }

    fun exists(phone: String): Boolean {
        val db = readableDatabase
        val c = db.rawQuery("SELECT 1 FROM entries WHERE phone = ?", arrayOf(phone))
        val exists = c.count > 0
        c.close()
        return exists
    }

    fun getAll(): List<Entry> {
        val list = mutableListOf<Entry>()
        val db = readableDatabase
        val c = db.rawQuery("SELECT id, phone, timestamp FROM entries ORDER BY id ASC", null)
        while (c.moveToNext()) {
            list.add(Entry(c.getLong(0), c.getString(1), c.getString(2)))
        }
        c.close()
        return list
    }
}
