package com.kyun.hpass.Main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import com.kyun.hpass.R
import com.kyun.hpass.realmDb.MyInfo
import io.realm.Realm

/**
 * Created by kyun on 2018. 3. 20..
 */
class SplashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val realm = Realm.getDefaultInstance()
        if(realm.where(MyInfo::class.java).findFirst() == null) realm.executeTransaction { it.insert(MyInfo().set(resources.getString(R.string.newbe))) } //새유저 가입
        realm.close()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)  //퍼미션 요청
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS,Manifest.permission.READ_PHONE_STATE), 0)
         else doFinish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !(getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName))
            startActivityForResult(Intent().setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + packageName)),0)
        else
            doFinish()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        doFinish()
    }

    private fun doFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}