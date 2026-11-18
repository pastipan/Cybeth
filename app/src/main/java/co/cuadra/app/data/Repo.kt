package co.cuadra.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Base de datos local (todo queda en el celular). */
object Repo {
    private lateinit var db: Helper
    private val _txs = MutableStateFlow<List<Tx>>(emptyList())
    val txs: StateFlow<List<Tx>> = _txs

    fun init(ctx: Context) {
        if (::db.isInitialized) return
        db = Helper(ctx.applicationContext)
        reload()
    }

    @Synchronized
    private fun reload() {
        val list = mutableListOf<Tx>()
        db.readableDatabase.rawQuery("SELECT id,amount,dir,who,bank,last4,ref,source,raw,time FROM tx ORDER BY time DESC LIMIT 2000", null).use { c ->
            while (c.moveToNext()) {
                list += Tx(
                    id = c.getLong(0),
                    amount = c.getLong(1),
                    direction = if (c.getString(2) == "OUT") Direction.OUT else Direction.IN,
                    counterparty = c.getString(3),
                    bank = c.getString(4),
                    last4 = c.getString(5),
                    reference = c.getString(6),
                    source = c.getString(7) ?: "",
                    raw = c.getString(8) ?: "",
                    time = c.getLong(9),
                )
            }
        }
        _txs.value = list
    }

    /**
     * Guarda la transferencia si no está repetida. Una misma transferencia puede llegar
     * por notificación Y por correo, o la notificación puede actualizarse: se considera
     * repetida si coincide el monto, el sentido y el banco en una ventana de 15 minutos
     * (o la misma referencia).
     * @return el movimiento guardado, o null si era repetido.
     */
    @Synchronized
    fun insertIfNew(p: ParsedTx, source: String, raw: String, time: Long = System.currentTimeMillis()): Tx? {
        if (Dedup.isDuplicate(_txs.value, p, source, raw.take(2000), time)) return null
        val cv = ContentValues().apply {
            put("amount", p.amount)
            put("dir", p.direction.name)
            put("who", p.counterparty)
            put("bank", p.bank)
            put("last4", p.last4)
            put("ref", p.reference)
            put("source", source)
            put("raw", raw.take(2000))
            put("time", time)
        }
        val id = db.writableDatabase.insert("tx", null, cv)
        reload()
        return _txs.value.firstOrNull { it.id == id }
    }

    @Synchronized
    fun delete(id: Long) {
        db.writableDatabase.delete("tx", "id=?", arrayOf(id.toString()))
        reload()
    }

    @Synchronized
    fun emailSeen(msgId: String): Boolean =
        db.readableDatabase.rawQuery("SELECT 1 FROM seen_email WHERE mid=?", arrayOf(msgId)).use { it.moveToFirst() }

    @Synchronized
    fun markEmailSeen(msgId: String) {
        db.writableDatabase.insertWithOnConflict(
            "seen_email", null,
            ContentValues().apply { put("mid", msgId) },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, "cuadra.db", null, 1) {
        override fun onCreate(d: SQLiteDatabase) {
            d.execSQL(
                """CREATE TABLE tx(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    amount INTEGER NOT NULL, dir TEXT NOT NULL, who TEXT, bank TEXT,
                    last4 TEXT, ref TEXT, source TEXT, raw TEXT, time INTEGER NOT NULL)"""
            )
            d.execSQL("CREATE INDEX tx_time ON tx(time)")
            d.execSQL("CREATE TABLE seen_email(mid TEXT PRIMARY KEY)")
        }

        override fun onUpgrade(d: SQLiteDatabase, oldV: Int, newV: Int) {}
    }
}
