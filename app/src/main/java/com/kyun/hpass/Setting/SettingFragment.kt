package com.kyun.hpass.Setting

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import com.kyun.hpass.Main.MainActivity
import com.kyun.hpass.Main.Splash.SplashActivity
import com.kyun.hpass.R
import com.kyun.hpass.Service.HService
import com.kyun.hpass.realmDb.Basic.LocationMap
import com.kyun.hpass.realmDb.Basic.Token
import com.kyun.hpass.realmDb.Nomal.*
import com.kyun.hpass.util.objects.Singleton
import kotlinx.android.synthetic.main.fragment_setting.*


/**
 * Created by kyun on 2018. 3. 13..
 */
@SuppressLint("ValidFragment")
class SettingFragment : Fragment() {

    private lateinit var mContext : Context

    private val realm by lazy { Singleton.getNomalDB() }

    private lateinit var Hs : HService
    private var isBind : Boolean = false

    lateinit var imm : InputMethodManager

    val conn = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            isBind = true
            Hs = (p1 as HService.MqttBinder).getService()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            isBind = false
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
        imm = mContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_setting,null)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setting_user_name.text = Singleton.MyN
        setting_user_id.text = Singleton.MyId
        setting_logout.setOnClickListener {
            AlertDialog.Builder(mContext)
                    .setTitle("로그아웃")
                    .setMessage("로그아웃시 앱 내의 모든 데이터가 초기화됩니다.\n로그아웃 전에 서버에 데이터를 백업해 주세요.\n정말 로그아웃하시겠습니까?")
                    .setPositiveButton("예",{dialog, which ->
                        realm.executeTransaction {
                            it.where(ChatList::class.java).findAll().deleteAllFromRealm()
                            it.where(ChatMember::class.java).findAll().deleteAllFromRealm()
                            it.where(ChatRoom::class.java).findAll().deleteAllFromRealm()
                            it.where(MyInfo::class.java).findAll().deleteAllFromRealm()
                            it.where(Peoples::class.java).findAll().deleteAllFromRealm()
                        }
                        val trealm = Singleton.getBasicDB()
                        trealm.executeTransaction {
                            it.where(Token::class.java).findAll().deleteAllFromRealm()
                            it.where(LocationMap::class.java).findAll().deleteAllFromRealm()
                        }
                        trealm.close()
                        Singleton.userToken = ""
                        Singleton.MyN = ""
                        Singleton.MyId = ""
                        Singleton.realmKey = ByteArray(0)
                        Hs.logout()
                        PreferenceManager.getDefaultSharedPreferences(mContext).edit().putString("type","").commit()
                        startActivity(Intent(mContext,SplashActivity::class.java))
                        (mContext as MainActivity).finish()
                    })
                    .setNegativeButton("아니요",{dialog, which -> dialog.cancel() }).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if(!isBind) mContext.bindService(Intent(mContext,HService::class.java),conn,0)
    }

    override fun onDestroy() {
        super.onDestroy()
        if(!realm.isClosed) realm.close()
        if(isBind) mContext.unbindService(conn)
    }
}