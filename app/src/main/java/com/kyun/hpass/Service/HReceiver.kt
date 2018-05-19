package com.kyun.hpass.Service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kyun.hpass.realmDb.Nomal.MyInfo
import com.kyun.hpass.util.objects.Singleton
import io.realm.Realm

/**
 * Created by kyun on 2018. 3. 13..
 */
class HReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            val realm = Singleton.getNomalDB()
            val u = realm.where(MyInfo::class.java).findFirst()
            realm.close()
            if(u != null) {
                Log.i("HService", "Start by Receiver")
                context.startService(Intent(context, HService::class.java))
            }
        }
    }
}