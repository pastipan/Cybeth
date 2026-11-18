package co.cuadra.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Ajustes de la app. La clave del correo se guarda cifrada en el celular. */
class Prefs(ctx: Context) {
    private val app = ctx.applicationContext
    private val p: SharedPreferences = app.getSharedPreferences("cuadra", Context.MODE_PRIVATE)

    private val secure: SharedPreferences by lazy {
        try {
            val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                app, "cuadra_secure", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            app.getSharedPreferences("cuadra_secure_fallback", Context.MODE_PRIVATE)
        }
    }

    /** Decir en voz alta cuando llega plata. */
    var voiceIn: Boolean
        get() = p.getBoolean("voiceIn", true)
        set(v) = p.edit().putBoolean("voiceIn", v).apply()

    /** Decir en voz alta también cuando se envía plata. */
    var voiceOut: Boolean
        get() = p.getBoolean("voiceOut", false)
        set(v) = p.edit().putBoolean("voiceOut", v).apply()

    /** Mostrar una notificación propia de Cuadra. */
    var notify: Boolean
        get() = p.getBoolean("notify", true)
        set(v) = p.edit().putBoolean("notify", v).apply()

    var email: String?
        get() = p.getString("email", null)
        set(v) = p.edit().putString("email", v).apply()

    var emailPassword: String?
        get() = secure.getString("emailPass", null)
        set(v) = secure.edit().putString("emailPass", v).apply()

    var lastEmailCheck: Long
        get() = p.getLong("lastEmailCheck", 0L)
        set(v) = p.edit().putLong("lastEmailCheck", v).apply()

    var lastEmailResult: String?
        get() = p.getString("lastEmailResult", null)
        set(v) = p.edit().putString("lastEmailResult", v).apply()

    /** Para diagnóstico: la última notificación de banco que la app alcanzó a ver. */
    var lastBankNotif: String?
        get() = p.getString("lastBankNotif", null)
        set(v) = p.edit().putString("lastBankNotif", v).apply()

    /** Correos (separados por coma) a los que se manda un aviso por cada transferencia. Ej: el jefe. */
    var forwardTo: String
        get() = p.getString("forwardTo", "") ?: ""
        set(v) = p.edit().putString("forwardTo", v).apply()

    /** Mandar correo cuando LLEGA plata. */
    var forwardIn: Boolean
        get() = p.getBoolean("forwardIn", true)
        set(v) = p.edit().putBoolean("forwardIn", v).apply()

    /** Mandar correo también cuando se ENVÍA plata. */
    var forwardOut: Boolean
        get() = p.getBoolean("forwardOut", false)
        set(v) = p.edit().putBoolean("forwardOut", v).apply()

    var lastForwardResult: String?
        get() = p.getString("lastForwardResult", null)
        set(v) = p.edit().putString("lastForwardResult", v).apply()

    val forwardList: List<String>
        get() = forwardTo.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.contains("@") && it.contains(".") }

    // ---------------- Inicio de sesión (clave de 4 dígitos, como Nequi) ----------------

    val hasPin: Boolean get() = !secure.getString("pinHash", null).isNullOrBlank()

    fun setPin(pin: String) {
        val salt = java.util.UUID.randomUUID().toString()
        secure.edit().putString("pinSalt", salt).putString("pinHash", hash(salt, pin)).apply()
        failedAttempts = 0
        lockUntil = 0
    }

    fun checkPin(pin: String): Boolean {
        val salt = secure.getString("pinSalt", null) ?: return false
        val ok = secure.getString("pinHash", null) == hash(salt, pin)
        if (ok) { failedAttempts = 0; lockUntil = 0 } else {
            failedAttempts += 1
            if (failedAttempts >= 5) lockUntil = System.currentTimeMillis() + 60_000L * (failedAttempts - 4)
        }
        return ok
    }

    private fun hash(salt: String, pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    var failedAttempts: Int
        get() = p.getInt("failedAttempts", 0)
        set(v) = p.edit().putInt("failedAttempts", v).apply()

    /** Después de 5 intentos fallidos la app se bloquea un rato. */
    var lockUntil: Long
        get() = p.getLong("lockUntil", 0L)
        set(v) = p.edit().putLong("lockUntil", v).apply()

    var useBiometric: Boolean
        get() = p.getBoolean("useBiometric", false)
        set(v) = p.edit().putBoolean("useBiometric", v).apply()

    val emailLinked: Boolean get() = !email.isNullOrBlank() && !emailPassword.isNullOrBlank()

    fun unlinkEmail() {
        p.edit().remove("email").remove("lastEmailCheck").remove("lastEmailResult").apply()
        secure.edit().remove("emailPass").apply()
    }
}
