package com.kyun.hpass

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
import com.kyun.hpass.Service.HService
import com.kyun.hpass.util.objects.Singleton
import com.kyun.hpass.realmDb.Nomal.Peoples
import io.realm.Realm
import kotlinx.android.synthetic.main.fragment_test.*


/**
 * Created by kyun on 2018. 3. 13..
 */
@SuppressLint("ValidFragment")
class test : Fragment() {

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
        return inflater.inflate(R.layout.fragment_test,null)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        test_userid.text = Singleton.MyId
        test_adduser.setOnEditorActionListener { textView, i, keyEvent ->
            if(i == EditorInfo.IME_ACTION_DONE) {
                val newU = test_adduser.text.toString()
                realm.executeTransaction { it.insert(Peoples().set(newU,resources.getString(R.string.unknown))) }
                Hs?.makeRoom(arrayOf(newU),null)
                imm?.hideSoftInputFromWindow(test_adduser.windowToken,0)
                true
            } else
                false
        }
    }

    override fun onResume() {
        super.onResume()
        mContext?.bindService(Intent(mContext,HService::class.java),conn,0)
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
        if(isBind) mContext?.unbindService(conn)
    }
}