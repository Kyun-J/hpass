package com.kyun.hpass.Main.Splash

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import com.kyun.hpass.App
import com.kyun.hpass.Main.MainActivity
import com.kyun.hpass.util.objects.Singleton

/**
 * Created by kyun on 2018. 3. 20..
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)  //퍼미션 요청
            requestPermissions(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CONTACTS), 0)
         else doLogin()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !(getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName))
            startActivityForResult(Intent().setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + packageName)),0)
        else
            doLogin()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        doLogin()
    }

    private fun doLogin() {
        var first = true
        Singleton.isOnNetwork(application as App, {
            if(it) {
                val type = PreferenceManager.getDefaultSharedPreferences(this).getString("type","")
                if (type == "") {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                } else {
                    Singleton.slientLogin(this, type, {
                        if(it) {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        } else {
                            //login fail
                        }
                    })
                }
            } else if(first) {
                Singleton.notOnlineToast(this)
                first = false
            }
        })
    }
}