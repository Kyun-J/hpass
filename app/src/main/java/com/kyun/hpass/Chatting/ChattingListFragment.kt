package com.kyun.hpass.Chatting

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.kyun.hpass.R
import com.kyun.hpass.Service.HService
import com.kyun.hpass.realmDb.Nomal.ChatList
import com.kyun.hpass.realmDb.Nomal.ChatMember
import com.kyun.hpass.realmDb.Nomal.ChatRoom
import com.kyun.hpass.util.objects.Singleton
import io.realm.Realm
import io.realm.Sort
import kotlinx.android.synthetic.main.fragment_chatting_list.*

/**
 * Created by kyun on 2018. 3. 13..
 */
@SuppressLint("ValidFragment")
class ChattingListFragment : Fragment(),HService.ChatCallBack {

    var mContext : Context? = null
    var adapter : ChattingListRecyclerAdapter? = null
    val realm : Realm = Singleton.getNomalDB()

    var Hs : HService? = null

    var isBind : Boolean = false

    val conn = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            isBind = true
            Hs = (p1 as HService.MqttBinder).getService()
            Hs?.registerCallback(this@ChattingListFragment)
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            isBind = false
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        mContext = context
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chatting_list,null)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val rooms = realm.where(ChatRoom::class.java).findAll()
        val roomlist = ArrayList<ChattingListRecyclerItem>()

        for(r in rooms) {
            val chatl = realm.where(ChatList::class.java).equalTo("RoomId",r.RoomId)
            val max = chatl.max("Time")?.toLong()
            val chat = if(max != null) realm.where(ChatList::class.java).equalTo("RoomId",r.RoomId).equalTo("Time",max).findFirst() else null
            val item = ChattingListRecyclerItem()
            item.id = r.RoomId
            item.title = r.RoomName
            if(chat != null) {
                item.recent = chat.Content
                item.time = chat.Time
            }
            item.users = realm.where(ChatMember::class.java).equalTo("RoomId",r.RoomId).findAll().size - 1
            item.stack = r.Count
            item.alarm = r.isAlarm
            roomlist.add(item)
        }
        sort(roomlist)
        adapter = ChattingListRecyclerAdapter(roomlist)
        //adapter?.emptyView
        chat_list_recycler.layoutManager = LinearLayoutManager(mContext)
        chat_list_recycler.adapter = adapter
    }

    override fun newChat(RoomId: String, UserId: String, UserName: String, Content: String, Time: Long) { // 새 대화 도착
        val data = adapter?.data
        if (data != null) {
            for(i in data.indices) {
                val d = data[i]
                if(d.id == RoomId) {
                    val r = realm.where(ChatRoom::class.java).equalTo("RoomId",RoomId).findFirst()
                    d.recent = Content
                    d.time = Time
                    d.stack = r!!.Count
                    adapter?.remove(i)
                    adapter?.addData(0,d)
                    break
                }
            }
        }
    }

    override fun DetectChange(RoomId: String) { // 목록 갱신
        val data = adapter?.data
        if(data != null) {
            for(i in data.indices) {
                val d = data[i]
                val r = realm.where(ChatRoom::class.java).equalTo("RoomId",RoomId).findFirst()
                if(d.id == RoomId && r == null) { //채팅방 삭제됨
                    adapter?.remove(i)
                    break
                } else if(d.id == r?.RoomId) { //채팅방 업뎃
                    val chatl = realm.where(ChatList::class.java).equalTo("RoomId",r.RoomId)
                    val max = chatl.max("Time")?.toLong()
                    if(max != null) {
                        val chat = realm.where(ChatList::class.java).equalTo("RoomId", r.RoomId).equalTo("Time", max).findFirst()
                        val item = ChattingListRecyclerItem()
                        item.id = r.RoomId
                        item.title = r.RoomName
                        item.recent = if (chat == null) "" else chat.Content
                        item.time = if (chat == null) 0 else chat.Time
                        item.users = realm.where(ChatMember::class.java).equalTo("RoomId", r.RoomId).findAll().size - 1
                        item.stack = r.Count
                        item.alarm = r.isAlarm
                        adapter!!.setData(i, item)
                    }
                    break
                } else if(i == data.size - 1 && r != null) { //채팅방 추가됨
                    val chat = realm.where(ChatList::class.java).equalTo("RoomId",r.RoomId).sort("Time",Sort.DESCENDING).findFirst()
                    val item = ChattingListRecyclerItem()
                    item.id = r.RoomId
                    item.title = r.RoomName
                    item.recent = if(chat == null) "" else chat.Content
                    item.time = if(chat == null) 0 else chat.Time
                    item.users = realm.where(ChatMember::class.java).equalTo("RoomId",r.RoomId).findAll().size - 1
                    item.stack = r.Count
                    item.alarm = r.isAlarm
                    adapter!!.addData(0,item)
                }
            }
        }
    }

    override fun newNoti(RoomId: String, Content: String, Time: Long) {
        //암것도안함
    }

    fun sort(itmes : MutableList<ChattingListRecyclerItem>) {
        if(itmes.size > 1)
            Qsort(itmes ,0,itmes.size-1)
    }

    fun Qsort(itmes : MutableList<ChattingListRecyclerItem>, l : Int, r : Int) {
        var left = l
        var right = r
        val pivot : Int = (l+r)/2
        do {
            while(itmes[left].time < itmes[pivot].time) left++
            while(itmes[right].time > itmes[pivot].time) right--;
            if(left <= right) {
                val d1 = itmes[left]
                itmes[left] = itmes[right]
                itmes[right] = d1
                left++
                right--
            }
        } while(left <= right)

        if(l < right) Qsort(itmes,l,right)
        if(r > left) Qsort(itmes, left, r)
    }

    override fun onResume() {
        super.onResume()
        if(!isBind)
            mContext?.bindService(Intent(mContext,HService::class.java),conn,0)
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
        Hs?.unregisterCallback(this)
        if(isBind) mContext?.unbindService(conn)
    }
}