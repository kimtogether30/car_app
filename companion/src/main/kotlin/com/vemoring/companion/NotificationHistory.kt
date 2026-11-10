package com.vemoring.companion

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow

enum class NotificationDelivery { Pending, Delivered, PermissionBlocked, ChannelBlocked, Disabled, Error }
data class CareNotificationEntry(val vehicleId: String, val eventId: String, val bannerKey: String,
    val title: String, val body: String, val mood: String, val art: String?, val occurredAt: Long,
    val createdAt: Long = System.currentTimeMillis(), val source: String = "Real", val visitId: String? = null,
    val delivery: NotificationDelivery = NotificationDelivery.Pending, val cancelled: Boolean = false,
    val read: Boolean = false)

/** Indexed incremental storage. Open/read/write on IO; no history trimming or user-data deletion. */
class NotificationHistory(context: Context) : SQLiteOpenHelper(context, "care_notification_history_v1.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events(vehicle TEXT NOT NULL,event TEXT NOT NULL,banner TEXT NOT NULL,title TEXT NOT NULL,body TEXT NOT NULL,mood TEXT NOT NULL,art TEXT,occurred INTEGER NOT NULL,created INTEGER NOT NULL,source TEXT NOT NULL,visit TEXT,delivery TEXT NOT NULL,cancelled INTEGER NOT NULL DEFAULT 0,is_read INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(vehicle,event))")
        db.execSQL("CREATE INDEX events_created ON events(created DESC)")
        db.execSQL("CREATE INDEX events_vehicle_created ON events(vehicle,created DESC)")
        db.execSQL("CREATE TABLE state(vehicle TEXT NOT NULL,name TEXT NOT NULL,value TEXT NOT NULL,PRIMARY KEY(vehicle,name))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun record(e: CareNotificationEntry): CareNotificationEntry {
        get(e.vehicleId,e.eventId)?.let {return it}
        val values = ContentValues().apply {
            put("vehicle",e.vehicleId); put("event",e.eventId); put("banner",e.bannerKey); put("title",e.title)
            put("body",e.body); put("mood",e.mood); put("art",e.art); put("occurred",e.occurredAt)
            put("created",e.createdAt); put("source",e.source); put("visit",e.visitId); put("delivery",e.delivery.name)
        }
        val inserted = writableDatabase.insertWithOnConflict("events",null,values,SQLiteDatabase.CONFLICT_IGNORE)
        if(inserted != -1L) changed()
        return requireNotNull(get(e.vehicleId,e.eventId))
    }
    fun get(vehicle: String,event: String): CareNotificationEntry? = readableDatabase.query("events",null,
        "vehicle=? AND event=?",arrayOf(vehicle,event),null,null,null).use { if(it.moveToFirst()) it.entry() else null }
    fun recent(vehicle: String? = null, limit: Int = 50, offset: Int = 0): List<CareNotificationEntry> {
        require(limit in 1..200 && offset >= 0)
        return readableDatabase.query("events",null,if(vehicle==null) null else "vehicle=?",
            vehicle?.let { arrayOf(it) },null,null,"created DESC, event DESC","$offset,$limit").use { c ->
            buildList { while(c.moveToNext()) add(c.entry()) }
        }
    }
    fun delivery(vehicle: String,event: String,result: NotificationDelivery) {
        if(writableDatabase.update("events",ContentValues().apply {put("delivery",result.name)},
            "vehicle=? AND event=? AND delivery<>?",arrayOf(vehicle,event,result.name))>0) changed()
    }
    fun cancelVisit(vehicle: String,visit: String) {
        if(writableDatabase.update("events",ContentValues().apply {put("cancelled",1);put("is_read",1)},
            "vehicle=? AND visit=? AND cancelled=0",arrayOf(vehicle,visit))>0) changed()
    }
    fun markRead(vehicle: String,event: String) {
        if(writableDatabase.update("events",ContentValues().apply {put("is_read",1)},"vehicle=? AND event=? AND is_read=0",arrayOf(vehicle,event))>0) changed()
    }
    fun value(vehicle: String,name: String): String? = readableDatabase.query("state",arrayOf("value"),
        "vehicle=? AND name=?",arrayOf(vehicle,name),null,null,null).use {if(it.moveToFirst())it.getString(0) else null}
    fun value(vehicle: String,name: String,value: String) {
        if(value(vehicle,name)==value) return
        writableDatabase.insertWithOnConflict("state",null,ContentValues().apply {put("vehicle",vehicle);put("name",name);put("value",value)},SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun deleteVehicle(vehicle: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("events","vehicle=?",arrayOf(vehicle))
            writableDatabase.delete("state","vehicle=?",arrayOf(vehicle))
            writableDatabase.setTransactionSuccessful()
        } finally {writableDatabase.endTransaction()}
        changed()
    }
    /** Same state is one episode even after app restart; a recurrence gets a new sequence. */
    fun stateEvent(vehicle: String,signature: String,lane: String = "state"): String {
        writableDatabase.beginTransaction()
        try {
            var sequence = value(vehicle,"sequence:$lane")?.toLongOrNull() ?: 0
            if(value(vehicle,"signature:$lane") != signature) {
                sequence++; value(vehicle,"sequence:$lane",sequence.toString()); value(vehicle,"signature:$lane",signature)
            }
            writableDatabase.setTransactionSuccessful()
            return "state:$lane:$sequence"
        } finally { writableDatabase.endTransaction() }
    }
    private fun Cursor.entry(): CareNotificationEntry {
        fun s(key:String): String? = getString(getColumnIndexOrThrow(key))
        fun t(key:String): String = requireNotNull(s(key))
        fun n(key:String)=getLong(getColumnIndexOrThrow(key))
        return CareNotificationEntry(t("vehicle"),t("event"),t("banner"),t("title"),t("body"),t("mood"),s("art"),
            n("occurred"),n("created"),t("source"),s("visit"),NotificationDelivery.valueOf(t("delivery")),n("cancelled")!=0L,n("is_read")!=0L)
    }
    companion object {
        val revision = MutableStateFlow(0L)
        private fun changed() { synchronized(revision) { revision.value++ } }
    }
}
