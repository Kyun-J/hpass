package com.kyun.hpass.Main.Splash

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import com.kyun.hpass.Main.MainActivity
import com.kyun.hpass.util.objects.Status
import com.kyun.hpass.util.objects.Singleton
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.kyun.hpass.R
import com.kyun.hpass.util.objects.isNetwork
import java.lang.NullPointerException


/**
 * Created by kyun on 2018. 3. 20..
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Status.displayMetrics = resources.displayMetrics

        val rect = Rect()
        window.decorView.getWindowVisibleDisplayFrame(rect)
        Status.statusBarH = rect.top
        Status.heightpix = Status.displayMetrics.heightPixels
        Status.widthpix = Status.displayMetrics.widthPixels
        Status.density = Status.displayMetrics.density

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelMessage = NotificationChannel(resources.getString(R.string.noti_chat), "채팅", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            channelMessage.description = "채팅 알람입니다."
            channelMessage.enableLights(true)
            channelMessage.enableVibration(true)
            channelMessage.importance = NotificationManager.IMPORTANCE_HIGH
            channelMessage.setSound(RingtoneManager(this).getRingtoneUri(RingtoneManager.TYPE_NOTIFICATION),AudioAttributes.Builder().build())
            channelMessage.vibrationPattern = longArrayOf(500, 500, 500, 500, 500)
            channelMessage.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(channelMessage)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)  //퍼미션 요청
            requestPermissions(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
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
        if(!Singleton.isinit()) {
            var firsterr = true
            val type = PreferenceManager.getDefaultSharedPreferences(this).getString("type", "")
            if (type == "") {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            } else {
                isNetwork.listen {
                    if (it) {
                        Singleton.slientLogin(this, type, {
                            if (it) {
                                dofinish()
                            } else {
                                //login fail
                            }
                        })
                    } else if (firsterr) {
                        Singleton.notOnlineToast(this)
                        firsterr = false
                    }
                }
            }
        } else {
            dofinish()
        }
    }

    private fun dofinish() {
        try {
            val chat = intent.extras.getString("chatid")
            if(chat != null && chat != "") startActivity(Intent(this,MainActivity::class.java).putExtra("chatid",chat))
            else startActivity(Intent(this, MainActivity::class.java))
        } catch ( e : NullPointerException) {
            startActivity(Intent(this, MainActivity::class.java))
        } finally {
            finish()
        }
    }
}