package co.cuadra.app

import android.app.Application
import co.cuadra.app.data.Prefs
import co.cuadra.app.data.Repo
import co.cuadra.app.service.Announcer
import co.cuadra.app.service.DayClose
import co.cuadra.app.service.MailWorker
import co.cuadra.app.service.Watchdog

class CuadraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Announcer.init(this)
        Announcer.warmUp(this)
        if (Prefs(this).emailLinked) MailWorker.schedule(this)
        DayClose.schedule(this, replace = false)
        Watchdog.rebind(this)
        Watchdog.schedule(this)
    }
}
