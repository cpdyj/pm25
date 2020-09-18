package com.example.pm25

import android.app.Service
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.util.Log

@Suppress("LocalVariableName")
class LocalServiceConnection<T : Service, B : BaseLocalBinder<T>> : ServiceConnection {
    val service: T
        get() = _s ?: error("unbind.")
    val isBind: Boolean
        get() = _s != null

    private var _s: T? = null
    override fun onServiceConnected(name: ComponentName?, service: IBinder) {
        val _s = _s
        if (_s != null) {
            Log.e("SimpleServiceConnection", "service not unbind")
            return
        }
        @Suppress("UNCHECKED_CAST")
        this._s = (service as B).service
        Log.d("SimpleServiceConnection", "connect: $name")

    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.d("SimpleServiceConnection", "disconnect: $name")
    }
}

open class BaseLocalBinder<T : Service>(val service: T) : Binder()

inline fun <T : Service, B : BaseLocalBinder<T>> localServiceConnection() =
    LocalServiceConnection<T, B>()