package com.kyun.hpass.util

import android.content.Context
import android.os.AsyncTask
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.kyun.hpass.App
import com.kyun.hpass.util.objects.Singleton


class isNetwrok(val app : App) : AsyncTask<Void,Boolean,Void>() {

    private var listeners = ArrayList<(Boolean)->Unit>()

    fun addListener(listener : (Boolean) -> Unit) {
        listeners.add(listener)
    }

    override fun doInBackground(vararg p0: Void?): Void? {
        while (true) {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if(cm.activeNetworkInfo.isConnected) {
                publishProgress(true)
                break
            } else {
                publishProgress(false)
            }
            Thread.sleep(if(app.isForeground()) 1000 else 10000)
        }
        return null
    }

    override fun onProgressUpdate(vararg values: Boolean?) {
        super.onProgressUpdate(*values)
        for(l in listeners) l(values[0]!!)
    }

    override fun onPostExecute(result: Void?) {
        super.onPostExecute(result)
        listeners = ArrayList()
    }
}