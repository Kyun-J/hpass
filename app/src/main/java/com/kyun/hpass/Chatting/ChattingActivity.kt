package com.kyun.hpass.Chatting

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import com.kyun.hpass.R
import com.kyun.hpass.Service.HService
import com.kyun.hpass.util.objects.Singleton
import com.kyun.hpass.realmDb.ChatList
import com.kyun.hpass.realmDb.ChatRoom
import com.kyun.hpass.realmDb.Peoples
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.android.synthetic.main.activity_chatting.*
import java.util.Calendar
import kotlin.collections.ArrayList

/**
 * Created by kyun on 2018. 3. 19..
 */
class ChattingActivity : AppCompatActivity(), HService.ChatCallBack {

    var RoomId : String = ""

    var realm : Realm = Realm.getDefaultInstance()
    var adapter : ChattingRecyclerAdapter? = null

    var Hs : HService? = null //서비스 객체
    var isBind : Boolean = false //바인드 여부

    var LastP : Boolean = false //가장 처음 채팅까지 로딩
    var LastT : Long = 0 //가장 최근에 로딩한 채팅 시간
    var recentT : Long = 0 //가장 마지막에받은 메시지 시간
    var recentU : String = "" //가장 마지막에받은 채팅 유저
    var Dchange : Boolean = false //채팅중 날짜바뀜

    var lastB : Int = 0 //현재 바닥 상태
    var keyB : Int = 0 //키보드 높이
    var rState : Boolean = false //키보드 상태
    var isKeyOn : Boolean = false //키보드 움직임 여부
    var recY : Int = 0 //리사이클러뷰 내부의 Y 절대값

    val conn = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            isBind = true
            Hs = (p1 as HService.MqttBinder).getService()
            Hs?.registerCallback(this@ChattingActivity)
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            isBind = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatting)
        setSupportActionBar(chat_toolbar)
        
        RoomId = intent.extras.getString("id")
        val count = intent.extras.getInt("count")
        
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(realm.where(ChatRoom::class.java).equalTo("RoomId",RoomId).findFirst()?.RoomName)
        
        adapter = ChattingRecyclerAdapter(AlreadyData(realm.where(ChatList::class.java).equalTo("RoomId", RoomId).sort("Time", Sort.DESCENDING).findAll(),count))
        chat_text_list.adapter = adapter
        val layoutmanager = LinearLayoutManager(this)
        layoutmanager.reverseLayout = true
        layoutmanager.stackFromEnd = true
        chat_text_list.layoutManager = layoutmanager
        chat_text_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView?, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val p = (chat_text_list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                val s = adapter?.data?.size
                if(p + 1 == s && !LastP) {
                    val result =
                            if (LastT == 0.toLong())
                                realm.where(ChatList::class.java).equalTo("RoomId", RoomId).sort("Time", Sort.DESCENDING).findAll()
                            else
                                realm.where(ChatList::class.java).equalTo("RoomId", RoomId).lessThanOrEqualTo("Time", LastT).sort("Time", Sort.DESCENDING).findAll()
                    if(result != null) adapter?.addData(newData(result))
                }
            }
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if(isKeyOn) {
                    isKeyOn = false
                    if(rState) recY += keyB
                    else if(recY >= keyB) recY -= keyB
                    else recY -= recY
                }
                recY -= dy
            }
        })
        chat_text_list.addOnLayoutChangeListener { v, left, top, right, bottom, oleft, oright, obottom, otop ->
            if(lastB == 0) lastB = bottom
            if(lastB != bottom) {
                if(!rState) {
                    if(keyB == 0) keyB = lastB - bottom
                    chat_text_list.post { chat_text_list.smoothScrollBy(0,keyB) }
                    rState = true
                } else {
                    if(recY > keyB)
                        chat_text_list.post { chat_text_list.smoothScrollBy(0,-keyB) }
                    else
                        chat_text_list.post { chat_text_list.smoothScrollBy(0,-recY)}
                    rState = false
                }
                lastB = bottom
                isKeyOn = true
            }
        }
        chat_confirm.setOnClickListener { doChat() }

        if(count > 20) chat_text_list.scrollToPosition(count)
        checkAll()
    }

    override fun onResume() {
        super.onResume()
        if(!isBind) bindService(Intent(this,HService::class.java),conn,0)
        if(realm.isClosed) realm = Realm.getDefaultInstance()
    }

    override fun onDestroy() {
        super.onDestroy()
        if(isBind) {
            Hs?.unregisterCallback(this)
            Hs?.detectRoom(RoomId)
            unbindService(conn)
        }
        realm.close()
    }

    private fun doChat() {
        Hs?.pubChat(RoomId,chat_edit.text.toString())
        chat_edit.setText("")
    }

    private fun checkAll() {
        realm.executeTransaction {
            for (c in it.where(ChatList::class.java).equalTo("RoomId", RoomId).equalTo("check", false).findAll()) c.check = true
            it.where(ChatRoom::class.java).equalTo("RoomId", RoomId).findFirst()?.Count = 0
        }
        chat_text_list.scrollToPosition(0)
    }

    //초기에 안읽은 개수만큼 메세지 로딩
    private fun AlreadyData(contents: RealmResults<ChatList>, count : Int) : ArrayList<ChattingRecyclerItem> {
        val items: ArrayList<ChattingRecyclerItem> = ArrayList()
        val Check = count > 20
        var sTime: Long = 0 //새 유저 새 대화 시작 시간
        var sUser = "" //새 유저 새 대화 유저
        var cYear: Int = Calendar.getInstance().get(Calendar.YEAR) //올해
        var dTime: Calendar? = null //마지막 표시한 날짜
        var first = false //처음배열
        var start: Int
        if(contents.size <= count + 20) {
            start = count - 1
            LastP = true
        } else {
            start = count
        }
        if(contents.size > 0) LastT = contents[count]!!.Time
        for(i in start downTo 0) {
            val item = ChattingRecyclerItem()
            val t = Calendar.getInstance()
            t.timeInMillis = contents[i]!!.Time
            recentT = contents[i]!!.Time
            recentU = contents[i]!!.UserId
            if (dTime == null) dTime = t
            item.contents = contents[i]!!.Content
            if ((!LastP && i == start - 1) || (LastP && i == start)) {
                LastT = contents[i]!!.Time
                if (Check) {
                    val ditem = ChattingRecyclerItem()
                    ditem.itemtype = ChattingRecyclerItem.noti
                    ditem.contents = resources.getString(R.string.here)
                    items.add(0, ditem)
                }
            }
            if (cYear != t.get(Calendar.YEAR) && dTime!!.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)) { //날짜 표시
                sUser = ""
                val ditem = ChattingRecyclerItem()
                ditem.itemtype = ChattingRecyclerItem.day
                ditem.contents = " " + t.get(Calendar.YEAR) + "년 " + (t.get(Calendar.MONTH) + 1) + "월 " + t.get(Calendar.DATE) + "일 "
                items.add(0, ditem)
                dTime.timeInMillis += 24 * 60 * 60 * 1000
            } else if (dTime!!.get(Calendar.DAY_OF_YEAR) != t.get(Calendar.DAY_OF_YEAR)) {
                sUser = ""
                val ditem = ChattingRecyclerItem()
                ditem.itemtype = ChattingRecyclerItem.day
                ditem.contents =
                        if (cYear != t.get(Calendar.YEAR))
                            " " + t.get(Calendar.YEAR) + "년 " + (t.get(Calendar.MONTH) + 1) + "월 " + t.get(Calendar.DATE) + "일 "
                        else
                            (t.get(Calendar.MONTH) + 1).toString() + "월 " + t.get(Calendar.DATE) + "일 "
                items.add(0, ditem)
                dTime.timeInMillis += 24 * 60 * 60 * 1000
            }
            if (contents[i]!!.ChatId <= 0 && ((!LastP && i <= start - 1) || LastP)) { //들어옴,나감 등 표시
                sUser = ""
                item.itemtype = ChattingRecyclerItem.noti
                items.add(0, item)
            } else { //채팅표시
                val c = Calendar.getInstance()
                c.timeInMillis = sTime
                item.time = t.timeInMillis
                val isMe = contents[i]?.UserId == Singleton.MyId
                if (!first) {
                    first = true
                    sTime = contents[i]!!.Time
                    sUser = contents[i]!!.UserId
                    if ((!LastP && i <= start - 1) || LastP) {
                        if(isMe) item.itemtype = ChattingRecyclerItem.Istarted
                        else {
                            item.itemtype = ChattingRecyclerItem.Ystarted
                            item.name = realm.where(Peoples::class.java).equalTo("UserId", contents[i]?.UserId).findFirst()!!.UserName
                        }
                        items.add(0, item)
                    }
                } else if (contents[i]!!.Time - sTime <= 60 * 1000 && sUser == contents[i]!!.UserId && c.get(Calendar.MINUTE) == t.get(Calendar.MINUTE)) {
                    if(isMe) item.itemtype = ChattingRecyclerItem.Icontinued
                    else item.itemtype = ChattingRecyclerItem.Ycontinued
                    items.add(0, item)
                } else {
                    sTime = contents[i]!!.Time
                    sUser = contents[i]!!.UserId
                    if(isMe) item.itemtype = ChattingRecyclerItem.Istarted
                    else {
                        item.itemtype = ChattingRecyclerItem.Ystarted
                        item.name = realm.where(Peoples::class.java).equalTo("UserId", contents[i]?.UserId).findFirst()!!.UserName
                    }
                    items.add(0, item)
                }
            }
        }

        items.addAll(newData(realm.where(ChatList::class.java).equalTo("RoomId", RoomId).lessThanOrEqualTo("Time", LastT).sort("Time", Sort.DESCENDING).findAll()))
        return items
    }

    //20개씩 메시지 로딩
    private fun newData(contents : RealmResults<ChatList>) : ArrayList<ChattingRecyclerItem> {
        val items: ArrayList<ChattingRecyclerItem> = ArrayList() //리턴
        var sTime: Long = 0 //새 유저 새 대화 시작 시간
        var sUser = "" //새 유저 새 대화 유저
        var cYear: Int = Calendar.getInstance().get(Calendar.YEAR) //올해
        var dTime: Calendar? = null //마지막 표시한 날짜
        var first = false //처음배열
        var count: Int //추가할 아이템 개수
        if (contents.size > 22) {
            count = 22
        } else {
            count = contents.size
            LastP = true
        }
        for (i in count - 1 downTo 0) {
            val item = ChattingRecyclerItem()
            val t = Calendar.getInstance()
            t.timeInMillis = contents[i]!!.Time
            if(dTime == null) dTime = t
            item.contents = contents[i]!!.Content
            if ((!LastP && i == count - 2) || (LastP && i == count - 1)) LastT = contents[i]!!.Time
            if(cYear != t.get(Calendar.YEAR) && dTime!!.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)) { //날짜 표시
                sUser = ""
                val ditem = ChattingRecyclerItem()
                ditem.itemtype = ChattingRecyclerItem.day
                ditem.contents = " " + t.get(Calendar.YEAR) + "년 " + (t.get(Calendar.MONTH)+1) + "월 " + t.get(Calendar.DATE) + "일 "
                items.add(0,ditem)
                dTime.timeInMillis += 24*60*60*1000
            } else if(dTime!!.get(Calendar.DAY_OF_YEAR) != t.get(Calendar.DAY_OF_YEAR)) {
                sUser = ""
                val ditem = ChattingRecyclerItem()
                ditem.itemtype = ChattingRecyclerItem.day
                ditem.contents =
                        if(cYear != t.get(Calendar.YEAR))
                            " " + t.get(Calendar.YEAR) + "년 " + (t.get(Calendar.MONTH)+1) + "월 " + t.get(Calendar.DATE) + "일 "
                        else
                            (t.get(Calendar.MONTH)+1).toString() + "월 " + t.get(Calendar.DATE) + "일 "
                items.add(0,ditem)
                dTime.timeInMillis += 24*60*60*1000
            }
            if (contents[i]!!.ChatId <= 0 && ((!LastP && i <= count - 2) || LastP)) { //들어옴,나감 등 표시
                sUser = ""
                item.itemtype = ChattingRecyclerItem.noti
                items.add(0,item)
            } else {
                val c = Calendar.getInstance()
                c.timeInMillis = sTime
                item.time = t.timeInMillis
                val isMe = contents[i]?.UserId == Singleton.MyId
                if (!first) {
                    first = true
                    sTime = contents[i]!!.Time
                    sUser = contents[i]!!.UserId
                    if ((!LastP && i <= count - 2) || LastP) {
                        if(isMe) item.itemtype = ChattingRecyclerItem.Istarted
                        else {
                            item.itemtype = ChattingRecyclerItem.Ystarted
                            item.name = realm.where(Peoples::class.java).equalTo("UserId", contents[i]?.UserId).findFirst()!!.UserName
                        }
                        items.add(0,item)
                    }
                } else if (contents[i]!!.Time - sTime <= 60 * 1000 && sUser == contents[i]!!.UserId && c.get(Calendar.MINUTE) == t.get(Calendar.MINUTE)) {
                    if(isMe) item.itemtype = ChattingRecyclerItem.Icontinued
                    else item.itemtype = ChattingRecyclerItem.Ycontinued
                    items.add(0,item)
                } else {
                    sTime = contents[i]!!.Time
                    sUser = contents[i]!!.UserId
                    if(isMe) item.itemtype = ChattingRecyclerItem.Istarted
                    else {
                        item.itemtype = ChattingRecyclerItem.Ystarted
                        item.name = realm.where(Peoples::class.java).equalTo("UserId", contents[i]?.UserId).findFirst()!!.UserName
                    }
                    items.add(0,item)
                }
            }
            if(i == 0 && recentT == 0.toLong()) recentT = contents[i]!!.Time
        }
        if(recentT == 0.toLong()) recentT = Calendar.getInstance().timeInMillis
        return items
    }

    override fun newChat(RoomId: String, UserId: String, UserName: String, Content: String, Time: Long) {
        if(RoomId == this.RoomId) {
            val c = Calendar.getInstance()
            c.timeInMillis = Time
            val t = Calendar.getInstance()
            t.timeInMillis = recentT
            if (c.get(Calendar.DAY_OF_YEAR) != t.get(Calendar.DAY_OF_YEAR)) {
                val dtime = ChattingRecyclerItem()
                dtime.itemtype = ChattingRecyclerItem.day
                dtime.contents = if (Calendar.getInstance().get(Calendar.YEAR) != c.get(Calendar.YEAR))
                    " " + c.get(Calendar.YEAR) + "년 " + (c.get(Calendar.MONTH) + 1) + "월 " + c.get(Calendar.DATE) + "일 "
                else
                    (c.get(Calendar.MONTH) + 1).toString() + "월 " + c.get(Calendar.DATE) + "일 "
                adapter?.addData(0, dtime)
                Dchange = true
            }
            val item = ChattingRecyclerItem()
            item.contents = Content
            item.time = Time
            val isMe = UserId == Singleton.MyId
            if (!Dchange && Time - recentT <= 60 * 1000 && UserId == recentU && c.get(Calendar.MINUTE) == t.get(Calendar.MINUTE)) {
                if(isMe) item.itemtype = ChattingRecyclerItem.Icontinued
                else item.itemtype = ChattingRecyclerItem.Ycontinued
            } else {
                if (Dchange) Dchange = false
                if(isMe) item.itemtype = ChattingRecyclerItem.Istarted
                else {
                    item.itemtype = ChattingRecyclerItem.Ystarted
                    item.name = UserName
                }
            }
            recentT = Time
            recentU = UserId
            adapter?.addData(0, item)
            recY = 0
            checkAll()
        }
    }

    override fun newNoti(RoomId: String, Content: String , Time : Long) {
        if(RoomId == this.RoomId) {
            val c = Calendar.getInstance()
            c.timeInMillis = Time
            val t = Calendar.getInstance()
            t.timeInMillis = recentT
            if(c.get(Calendar.DAY_OF_YEAR) != t.get(Calendar.DAY_OF_YEAR)) {
                val dtime = ChattingRecyclerItem()
                dtime.itemtype = ChattingRecyclerItem.day
                dtime.contents = if(Calendar.getInstance().get(Calendar.YEAR) != c.get(Calendar.YEAR))
                    " " + c.get(Calendar.YEAR) + "년 " + (c.get(Calendar.MONTH)+1) + "월 " + c.get(Calendar.DATE) + "일 "
                else
                    (c.get(Calendar.MONTH)+1).toString() + "월 " + c.get(Calendar.DATE) + "일 "
                adapter?.addData(0,dtime)
                recentT = Time
                Dchange = true
            }
            val item = ChattingRecyclerItem()
            item.itemtype = ChattingRecyclerItem.noti
            item.contents = Content
            adapter?.addData(0,item)
            recY = 0
            checkAll()
        }
    }

    override fun DetectChange(RoomId: String) {
        if(RoomId == this.RoomId) {
            if(realm.where(ChatRoom::class.java).equalTo("RoomId",RoomId).findFirst() == null) finish()
            else {
                val po = (chat_text_list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                adapter?.replaceData(AlreadyData(realm.where(ChatList::class.java).equalTo("RoomId", RoomId).sort("Time", Sort.DESCENDING).findAll(),po-1))
                chat_text_list.scrollToPosition(po)
            }
        }
    }
}