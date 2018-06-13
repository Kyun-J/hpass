package com.kyun.hpass.util.objects

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.*
import android.os.Build
import android.os.Handler
import android.util.Log


object isNetwork {

    private lateinit var cm : ConnectivityManager

    private var listeners = ArrayList<(Boolean)->Unit>()

    private lateinit var HServiceListener : (Boolean)->Unit

    private val handler = Handler()

    var nowState = false

    private val NetworkCallback =
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network?) {
                        super.onAvailable(network)
                        nowState = true
                        for(l in listeners) l(true)
                        listeners = ArrayList()
                        if(::HServiceListener.isInitialized) HServiceListener(true)
                        Log.i("onAvailable", "onAvailable")
                    }

                    override fun onLost(network: Network?) {
                        super.onLost(network)
                        nowState = false
                        for(l in listeners) l(false)
                        if(::HServiceListener.isInitialized) HServiceListener(false)
                        Log.i("onLost", "onLost")
                    }

                    override fun onLosing(network: Network?, maxMsToLive: Int) {
                        super.onLosing(network, maxMsToLive)
                        nowState = false
                        if(::HServiceListener.isInitialized) HServiceListener(false)
                        Log.i("onLosing", "onLosing")
                    }

                    override fun onLinkPropertiesChanged(network: Network?, linkProperties: LinkProperties?) {
                        super.onLinkPropertiesChanged(network, linkProperties)
                        //nowState = false
                        Log.i("onLinkPropertiesChanged", "onLinkPropertiesChanged")
                    }
                }
            } else {
                object : BroadcastReceiver() {
                    override fun onReceive(p0: Context?, intent: Intent) {
                        if(!intent.hasExtra("noConnectivity")){
                            nowState = true
                            for(l in listeners) l(true)
                            listeners = ArrayList()
                            if(::HServiceListener.isInitialized) HServiceListener(true)
                        }
                        else {
                            nowState = false
                            for (l in listeners) l(false)
                            if(::HServiceListener.isInitialized) HServiceListener(false)
                        }
                    }
                }
            }

    fun init(context: Context) {
        cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        nowState = cm.activeNetworkInfo.isConnected
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            cm.requestNetwork(NetworkRequest.Builder().build(), NetworkCallback as ConnectivityManager.NetworkCallback)
        else
            context.registerReceiver(NetworkCallback as BroadcastReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    fun release(context: Context) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            if(::cm.isInitialized) cm.unregisterNetworkCallback(NetworkCallback as ConnectivityManager.NetworkCallback)
        else
            context.unregisterReceiver(NetworkCallback as BroadcastReceiver)
        listeners = ArrayList()
        HServiceListener = {}
    }

    fun listen(listener : (Boolean) -> Unit) {
        if(nowState) {
            handler.postDelayed({
                if(nowState) listener(true)
                else listeners.add(listener)
            },500)
        }
        else listeners.add(listener)
    }

    fun Hlisten(listener: (Boolean) -> Unit) {
        HServiceListener = listener
    }

}