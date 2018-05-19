package com.kyun.hpass.Setting

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.kyun.hpass.R
import com.kyun.hpass.Service.HService
import com.kyun.hpass.util.objects.Singleton
import com.kyun.hpass.realmDb.Nomal.Peoples
import kotlinx.android.synthetic.main.fragment_setting.*


/**
 * Created by kyun on 2018. 3. 13..
 */
@SuppressLint("ValidFragment")
class SettingFragment : Fragment() {

    var mContext : Context? = null

    val realm = Singleton.getNomalDB()

    var Hs : HService? = null
    var isBind : Boolean = false

    var imm : InputMethodManager? = null

    val conn = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            isBind = true
            Hs = (p1 as HService.MqttBinder).getService()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            isBind = false
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        mContext = context
        imm = mContext?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_setting,null)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        test_userid.text = Singleton.MyId
        test_adduser.setOnEditorActionListener { textView, i, keyEvent ->
            if(i == EditorInfo.IME_ACTION_DONE) {
                if(isBind && Hs!!.isMqttAlive()) {
                    val newU = textView.text.toString()
                    Hs?.makeRoom(arrayOf(newU),null)
                    realm.executeTransaction { it.insert(Peoples().set(newU, resources.getString(R.string.unknown))) }
                } else Singleton.noMqttErrToast(mContext!!)
                imm?.hideSoftInputFromWindow(test_adduser.windowToken, 0)
                true
            } else
                false
        }
        test_adduser_btn.setOnClickListener {
            if(isBind && Hs!!.isMqttAlive()) {
                val newU = test_adduser.text.toString()
                Hs?.makeRoom(arrayOf(newU),null)
                realm.executeTransaction { it.insert(Peoples().set(newU, resources.getString(R.string.unknown))) }
            } else Singleton.noMqttErrToast(mContext!!)
            imm?.hideSoftInputFromWindow(test_adduser.windowToken, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        if(!isBind)
            mContext?.bindService(Intent(mContext,HService::class.java),conn,0)
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
        if(isBind) mContext?.unbindService(conn)
    }
}