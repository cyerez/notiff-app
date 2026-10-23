package com.rez.notif

import android.app.Application
import android.content.Context
import com.rez.notif.data.Db
import com.rez.notif.data.Prefs
import com.rez.notif.data.Repository

/** App entry point. Holds the single Repository shared by screens, the SMS receiver and the listener. */
class NotifApp : Application() {

    val repository: Repository by lazy { Repository(this, Db(this), Prefs(this)) }

    companion object {
        fun repo(context: Context): Repository = (context.applicationContext as NotifApp).repository
    }
}
