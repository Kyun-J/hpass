package com.kyun.hpass.Main.MakeRoom

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import com.kyun.hpass.Chatting.Activity.ChattingActivity
import com.kyun.hpass.R
import com.kyun.hpass.Service.HService
import com.kyun.hpass.realmDb.Nomal.ChatRoom
import com.kyun.hpass.realmDb.Nomal.Peoples
import com.kyun.hpass.util.objects.Singleton
import kotlinx.android.synthetic.main.activity_search.*

class MakeRoomActivity : AppCompatActivity() {

    private lateinit var adapter : MakeRoomAdapter

    private val realm = Singleton.getNomalDB()

    private lateinit var Hs : HService //서비스 객체
    private var isBind = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            isBind = true
            Hs = (p1 as HService.MqttBinder).getService()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            isBind = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        setSupportActionBar(search_toolbar)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "대화방 생성"
        search_tip.visibility = View.GONE
        search_edit.setOnEditorActionListener { textView, i, keyEvent ->
            if(i == EditorInfo.IME_ACTION_SEARCH) {
                adapter.replaceData(searchFriendList(textView.text.toString()))
                true
            } else false
        }
        search_edit_btn.setOnClickListener {
            adapter.replaceData(searchFriendList(search_edit.text.toString()))
        }

        adapter = MakeRoomAdapter(getFriendList())
        search_list.adapter = adapter
        search_list.layoutManager = LinearLayoutManager(this)
    }

    private fun confirm() {
        val users = ArrayList<String>()
        for(d in adapter.data) {
            if(d.checked) users.add(d.id)
        }
        if(users.size == 1) {
            val p = realm.where(Peoples::class.java).equalTo("UserId",users[0]).findFirst()
            if(p!!.pChatId == "") {
                if(isBind && Hs.isMqttAlive()) Hs.makeOnO(p)
                finish()
            }
            else {
                val isExist = realm.where(ChatRoom::class.java).equalTo("RoomId",p.pChatId).findFirst()
                if(isExist != null) {
                    startActivity(Intent(this, ChattingActivity::class.java).putExtra("id", p.pChatId))
                    finish()
                } else {
                    if(isBind) Hs.remakeOnO(p,p.pChatId)
                    finish()
                }
            }
        } else if(users.size > 1) {
            if(isBind && Hs.isMqttAlive()) {
                Hs.makeGroup(users,null)
                finish()
            }
        }
    }

    private fun getFriendList() : ArrayList<MakeRoomItem> {
        val result = ArrayList<MakeRoomItem>()
        val friends = realm.where(Peoples::class.java)
                .equalTo("isBan",false)
                .equalTo("isFriend",true)
                .sort("UserName").findAll()
        for(f in friends) result.add(MakeRoomItem().set(f.UserId,f.UserName))
        return result
    }

    private fun searchFriendList(text : String) : ArrayList<MakeRoomItem> {
        val result = ArrayList<MakeRoomItem>()
        val friends = realm.where(Peoples::class.java)
                .contains("UserName",text)
                .equalTo("isBan",false)
                .equalTo("isFriend",true)
                .sort("UserName").findAll()
        for(f in friends) result.add(MakeRoomItem().set(f.UserId,f.UserName))
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.makeroom_menu_confrim -> {
                confirm()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        if(!isBind) bindService(Intent(this,HService::class.java),conn,0)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.makeroom_menu,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onDestroy() {
        super.onDestroy()
        if(!realm.isClosed) realm.close()
        if(isBind) unbindService(conn)
    }
}