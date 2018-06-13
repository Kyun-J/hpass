package com.kyun.hpass.Service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Created by kyun on 2018. 3. 13..
 */
class HReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.i("HService", "Start by Receiver")
            context.startService(Intent(context, HService::class.java))
        }
    }
}