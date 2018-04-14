package com.kyun.hpass.Service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.*
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.telephony.TelephonyManager
import android.util.Log
import com.google.gson.JsonObject
import com.kyun.hpass.util.objects.Singleton
import org.eclipse.paho.client.mqttv3.*

import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import com.kyun.hpass.R
import com.kyun.hpass.util.objects.IgnoreValues
import com.kyun.hpass.realmDb.*
import io.realm.Realm

import io.realm.kotlin.deleteFromRealm
import org.eclipse.paho.client.mqttv3.internal.ClientComms
import java.util.*
import kotlin.collections.ArrayList


/**
 * Created by kyun on 2018. 3. 13..
 */
class HService : Service() {

    //바인드 관련
    private val mBinder : IBinder = MqttBinder()
    private val mChats = ArrayList<ChatCallBack>()

    //주요 기능
    private var mqttclient : MqttAsyncClient? = null
    private var realm : Realm = Realm.getDefaultInstance()
    private val handler : Handler = Handler()

    /** topics **/
    private val lastIdS = "li"
    private val reqChatS = "rc"
    private val chatS = "ca"
    private val inviteRS = "ir"
    private val exitRS = "er"
    private val isUserS = "iu"
    private val newFS = "nf"
    private val changeNS = "cn"

//    <!--topics-->
//    <!--users-->
//    <string name="newF">nf</string>
//    <string name="changeN">cn</string>
//    <!--chat-->
//    <string name="lastId">li</string>
//    <string name="reqChat">rc</string>
//    <string name="chat">ca</string>
//    <string name="inviteR">ir</string>
//    <string name="exitR">er</string>
//    <string name="isUser">iu</string>
//    <!--emergency-->
//    <string name="emergency">em</string>
//    <string name="location">lo</string>

    //일반
    private var nid : Int = 0 //notification id
    private var myId : String = "" //user id
    private var myN : String = "" //user name
    private val reqChatList : JsonObject = JsonObject()

    //바인드 세팅
    override fun onBind(p0: Intent): IBinder { return mBinder }

    inner class MqttBinder : Binder() {
        fun getService() : HService { return this@HService }
    }

    fun registerCallback(callback : ChatCallBack) {
        mChats.add(callback)
    }

    fun unregisterCallback(callback : ChatCallBack) {
        mChats.remove(callback)
    }

    interface ChatCallBack {
        fun newChat(RoomId: String, UserId: String, UserName : String, Content : String, Time : Long)
        fun newNoti(RoomId: String, Content: String, Time : Long)
        fun DetectChange(RoomId : String)
    }

    fun detectRoom(roomid : String) { //강제 변경 감지
        for(c in mChats) c.DetectChange(roomid)
    }

    //생명주기
    override fun onCreate() { //초기값
        super.onCreate()
        Log.i("HService","Create")
        if(PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE))
            myId = (getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).line1Number
        else
            myId = IgnoreValues.testUser
        Singleton.MyId = myId
        myN = realm.where(MyInfo::class.java).findFirst()!!.Name
        nid = PreferenceManager.getDefaultSharedPreferences(this).getInt("nid",0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { //realm mqtt 세팅
        Log.i("HService","Start")
        mqttSetting()
        if(realm.isClosed) realm = Realm.getDefaultInstance()
        return Service.START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() { //종료시
        super.onDestroy()
        Log.i("HService","Destroy")
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt("nid",nid).commit()
        if(!realm.isClosed) realm.close()
        mqttclient?.isConnected?.let { if(it) mqttclient?.disconnect() }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //재시작 알람
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(AlarmManager.RTC, Calendar.getInstance().timeInMillis + 5000,
                    PendingIntent.getService(this, 1, Intent(this, HService::class.java), 0))
        } else {
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, Calendar.getInstance().timeInMillis + 5000,
                    PendingIntent.getService(this, 1, Intent(this, HService::class.java), 0))
        }
    }

    private fun mqttSetting() { //mqtt세팅
        if(mqttclient == null) {
            mqttclient = MqttAsyncClient(IgnoreValues.BrokerUrl, myId, MemoryPersistence(), customSender())
            mqttclient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String) {
                    Log.i("mqttconnect", "connect Success")
                    handler.post {
                        SubscribeProcess()
                    }
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    Log.i("mqttarrive", "topic : " + topic + " msg : " + message.toString() + " id : " + message.id + ", duplicate :" + message.isDuplicate + ", retain : " + message.isRetained + ", qos : " + message.qos)
                    handler.post {
                        ArriveProcess(topic, message)
                    }
                }

                override fun connectionLost(cause: Throwable) {
                    Log.e("mqttlost", cause.toString())
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.i("mqttpub", "success pub : " + token?.topics)
                }
            })
            mqttclient?.isConnected?.let {
                if(!it) {
                    val ConnectOption = MqttConnectOptions()
                    ConnectOption.isAutomaticReconnect = true
                    ConnectOption.connectionTimeout = 600
                    //ConnectOption.isCleanSession = false
                    mqttclient?.connect(ConnectOption)
                }
            }
        }
    }

    //mqtt 관련 함수들
    private fun SubscribeProcess() {
        val chatrooms = realm.where(ChatRoom::class.java).findAll()
        val users = realm.where(Peoples::class.java).findAll()
        val ctsi = chatrooms.size
        val ussi = users.size

        val topics = Array(ctsi*5 + ussi*1 + 3) {""}
        val qoses = IntArray(topics.size) {1}
        for(po in chatrooms.indices) {
            val rid = chatrooms[po]?.RoomId
            topics[po*5] = rid+'/'+lastIdS+'/'+myId
            topics[po*5+1] = rid+'/'+reqChatS+'/'+myId
            topics[po*5+2] = rid+'/'+chatS
            topics[po*5+3] = rid+'/'+inviteRS
            topics[po*5+4] = rid+'/'+exitRS
        }
        for(po in users.indices) {
            val uid = users[po]?.UserId
            topics[ctsi*5+po*1] = uid+'/'+changeNS
        }
        topics[topics.size-3] = myId+'/'+newFS
        topics[topics.size-2] = myId+'/'+inviteRS
        topics[topics.size-1] = myId+'/'+isUserS

        multiSubscribe(topics,qoses)
    }

    private fun SubscribeRoom(roomid: String) {
        val topics = Array(5) {""}
        topics[0] = roomid+'/'+lastIdS+'/'+myId
        topics[1] = roomid+'/'+reqChatS+'/'+myId
        topics[2] = roomid+'/'+chatS
        topics[3] = roomid+'/'+inviteRS
        topics[4] = roomid+'/'+exitRS

        multiSubscribe(topics,IntArray(topics.size){0})
    }

    private fun unSubscribeRoom(roomid: String) {
        val topics = Array(5) {""}
        topics[0] = roomid+'/'+lastIdS+'/'+myId
        topics[1] = roomid+'/'+reqChatS+'/'+myId
        topics[2] = roomid+'/'+chatS
        topics[3] = roomid+'/'+inviteRS
        topics[4] = roomid+'/'+exitRS

        mqttclient?.unsubscribe(topics)
    }

    private fun SubscribePeople(userid : String) {
        val topics = Array(1) {""}
        topics[0] = userid+'/'+changeNS

        multiSubscribe(topics, IntArray(topics.size){0})
    }

    private fun unSubscribePeople(userid : String) {
        val topics = Array(1) {""}
        topics[0] = userid+'/'+changeNS

        mqttclient?.unsubscribe(topics)
    }

    private fun multiSubscribe(topics: Array<String>, qoses: IntArray) {
        mqttclient?.subscribe(topics,qoses,this,object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i("mqttsub","subscribe " + asyncActionToken?.topics?.size)
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("mqttsub",exception.toString())
            }
        })
    }

    private fun singleSubscribe(topic : String,qos : Int) {
        mqttclient?.subscribe(topic,qos,this,object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i("mqttsub","subscribe " + asyncActionToken?.topics?.size)
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("mqttsub",exception.toString())
            }
        })
    }

    private fun ArriveProcess(topic : String, message : MqttMessage) {
        val Atopic = topic.split('/')
        if(Atopic.size <= 1) return
        val Amsg = message.toString().split('/')
        val key = Atopic[0]
        val kind = Atopic[1]

        if(kind == lastIdS) { //내용 동기화 시도 마지막 아이디 기준
            val realkey = realm.where(ChatRoom::class.java).equalTo("RoomId",key).findFirst()
            if(realkey != null && Atopic.size == 3 ) {
                if(Atopic[2] == myId) { //요청을 받음 내 최대 id를 알려줌
                    var lastid = realm.where(ChatList::class.java).equalTo("RoomId",key).equalTo("UserId",myId).max("ChatId")?.toInt()
                    if(lastid == null) lastid = 0
                    mqttclient?.publish(key+'/'+lastIdS+'/'+myId,lastid.toString().toByteArray(),1,false)
                } else if(Amsg.size != 0){ //응답을 받음
                    var lastid = realm.where(ChatList::class.java).equalTo("RoomId",key).equalTo("UserId",Atopic[2]).max("ChatId")?.toInt()
                    singleSubscribe(key+'/'+reqChatS+'/'+Amsg[0],1)
                    mqttclient?.unsubscribe(key+'/'+lastIdS+'/'+Amsg[0])
                    val start = if(lastid == null) 0 else 1
                    if(lastid == null) lastid = Amsg[0].toInt()
                    reqChatList.addProperty(key,Amsg[0].toInt() - lastid)
                    for(i in start..(Amsg[0].toInt() - lastid)) //내 최대아이디가 낮으면 차이만큼 메세지 내용 요청
                        mqttclient?.publish(key+'/'+reqChatS+'/'+Amsg[0],(lastid+i).toString().toByteArray(),1,false)
                }
            } else {
                Log.e("chat","Incorrect msg")
            }
        }
        if(kind == reqChatS) {
            val realkey = realm.where(ChatRoom::class.java).equalTo("RoomId",key).findFirst()
            if(realkey != null && Atopic.size == 3 && Amsg.size != 0) {
                if (Atopic[2] == myId) {
                    val cons = realm.where(ChatList::class.java).equalTo("RoomId", key).equalTo("UserId", myId).equalTo("ChatId", Amsg[0]).findFirst()
                    if (cons != null) mqttclient?.publish(key + '/' + reqChatS + '/' + myId, (Amsg[0] + '/' + cons.Content + '/' + cons.Time).toByteArray(), 1, false)
                    else Log.e("chat", "Incorrect chatId")
                } else {
                    val thatid = realm.where(ChatList::class.java).equalTo("RoomId",key).equalTo("UserId",Atopic[1]).equalTo("ChatId",Amsg[0].toInt()).findFirst()
                    if(thatid == null) {
                        realm.executeTransactionAsync(Realm.Transaction {
                            realm.insert(ChatList().set(key, Amsg[0].toInt(), Atopic[2], Amsg[1], Amsg[2].toLong()))
                        }, object : Realm.Transaction.OnSuccess {
                            override fun onSuccess() {
                                Log.i("chat", "insert chat in : " + realkey.RoomName)
                                val count = reqChatList.get(key).asInt
                                if(reqChatList.get(key).asInt != 0) reqChatList.addProperty(key,count - 1)
                                else {
                                    reqChatList.remove(key)
                                    mqttclient?.unsubscribe(key+'/'+reqChatS+'/'+Atopic[2])
                                }
                                if(Singleton.isChattingRoom(this@HService,key)) for(c in mChats) c.DetectChange(key)
                            }
                        }, object : Realm.Transaction.OnError {
                            override fun onError(error: Throwable?) {
                                Log.e("chat", "insert chat err : ", error)
                            }
                        })
                    } else{
                        Log.i("chat", "Already have chatid : " + Amsg[0])
                    }
                }
            } else {
                Log.e("chat","Incorrect msg")
            }
        }
        if(kind == chatS) { //채팅 메세지 받음
            val realkey = realm.where(ChatRoom::class.java).equalTo("RoomId",key).findFirst()
            if(realkey != null) { // 자신의 채팅방 여부
                val thatid = realm.where(ChatList::class.java).equalTo("RoomId",key).equalTo("ChatId",Amsg[0].toInt()).equalTo("UserId",Amsg[1]).findFirst()
                if(thatid == null) {
                    realm.executeTransactionAsync(Realm.Transaction {
                        it.insert(ChatList().set(key, Amsg[0].toInt(), Amsg[1], Amsg[2], Amsg[3].toLong())) // 채팅 내용 저장
                        it.where(ChatRoom::class.java).equalTo("RoomId",key).findFirst()!!.Count++
                    }, object : Realm.Transaction.OnSuccess {
                        override fun onSuccess() {
                            Log.i("chat", "insert chat in : " + realkey.RoomName)
                            if(Amsg[0].toInt() > 0) {
                                var username = realm.where(Peoples::class.java).equalTo("UserId", Amsg[1]).findFirst()?.UserName
                                if (username == null) username = resources.getString(R.string.unknown)!!
                                for (c in mChats) c.newChat(key,Amsg[1], username, Amsg[2], Amsg[3].toLong())
                                if (!Singleton.isChattingRoom(this@HService, key)) { // 노티 발생
                                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(nid,
                                            NotificationCompat.Builder(this@HService, "mqtt")
                                                    .setSmallIcon(R.mipmap.ic_launcher_round)
                                                    .setVibrate(longArrayOf(500, 500, 500)).setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                                                    .setPriority(NotificationCompat.PRIORITY_HIGH).setContentTitle(Amsg[2])
                                                    .setContentText(message.toString()).build())
                                    if (nid + 1 == Int.MAX_VALUE) nid = 0
                                    else nid++
                                }
                            } else {
                                for(c in mChats) c.newNoti(key,Amsg[2],Amsg[3].toLong())
                            }
                        }
                    }, object : Realm.Transaction.OnError {
                        override fun onError(error: Throwable) {
                            Log.e("chat", "insert chat err : ", error)
                        }
                    })
                }
            } else { // 잘못된 메세지
                Log.e("chat","Incorrect msg")
            }
        } else if(kind == inviteRS) {// 채팅방 초대
            val realkey = realm.where(ChatRoom::class.java).equalTo("RoomId",key).findFirst()
            if(realkey != null && Atopic.size == 2 && Amsg[0] != myId) {//초대된 유저 확인
                val ruser = realm.where(ChatMember::class.java).equalTo("RoomId",key).equalTo("UserId",Amsg[0]).findFirst()
                if(ruser == null) {
                    val user = realm.where(Peoples::class.java).equalTo("UserId",Amsg[0]).findFirst()
                    if(user == null) {
                        realm.executeTransactionAsync(Realm.Transaction {
                            it.insert(ChatMember().set(key, Amsg[0]))
                            it.insert(Peoples().set(Amsg[0],Amsg[1]))
                        }, object : Realm.Transaction.OnSuccess {
                            override fun onSuccess() {
                                Log.i("chat", "newUser in : " + realkey.RoomName)
                                SubscribePeople(Amsg[0])
                            }
                        }, object : Realm.Transaction.OnError {
                            override fun onError(error: Throwable?) {
                                Log.e("chat", "invite user err : ", error)
                            }
                        })
                    } else {
                        realm.executeTransactionAsync(Realm.Transaction {
                            it.insert(ChatMember().set(key, Amsg[0]))
                        }, object : Realm.Transaction.OnSuccess {
                            override fun onSuccess() {
                                Log.i("chat", "newUser in : " + realkey.RoomName)
                            }
                        }, object : Realm.Transaction.OnError {
                            override fun onError(error: Throwable?) {
                                Log.e("chat", "invite user err : ", error)
                            }
                        })
                    }
                }
            } else if(key == myId && Amsg.size == 3)  { //초대를 받음
                val users = Amsg[2].split(',')
                val umodel = ArrayList<ChatMember>()
                val pmodel = ArrayList<Peoples>()
                for(u in users) {
                    val info = u.split('&')
                    umodel.add(ChatMember().set(key,info[0]))
                    if(realm.where(Peoples::class.java).equalTo("UserId",info[0]).findFirst() == null)
                        pmodel.add(Peoples().set(info[0],info[1]))
                }
                realm.executeTransactionAsync(Realm.Transaction {
                    it.insert(ChatRoom().set(Amsg[0],Amsg[1]))
                    it.insert(umodel)
                    it.insert(pmodel)
                },object : Realm.Transaction.OnSuccess {
                    override fun onSuccess() {
                        Log.i("chat","join suc")
                        for(c in mChats) c.DetectChange(Amsg[0])
                        for(u in pmodel) SubscribePeople(u.UserId)
                        SubscribeRoom(Amsg[0])
                        mqttclient?.publish(Amsg[0]+'/'+inviteRS,(myId+'/'+realm.where(MyInfo::class.java).findFirst()?.Name).toByteArray(),0,false,this,object : IMqttActionListener {
                            override fun onSuccess(asyncActionToken: IMqttToken?) {
                                Log.i("chat", "newin chat in : " + Amsg[1])
                                mqttclient?.publish(Amsg[0]+'/'+chatS,("0/"+myId+'/'+myN+resources.getString(R.string.newin)+'/'+Calendar.getInstance().timeInMillis).toByteArray(),0,false)
                            }

                            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                                Log.e("chat", "newin chat err : ", exception)
                            }
                        }) //내가 초대된 사실 알림
                    }
                },object : Realm.Transaction.OnError{
                    override fun onError(error: Throwable?) {
                        Log.e("chat","join err : ",error)
                    }
                })
            } else { // 잘못된 메세지
                Log.e("chat","Incorrect msg")
            }
        } else if(kind == exitRS) {
            val realkey = realm.where(ChatRoom::class.java).equalTo("RoomId",key).findFirst()
            if(realkey != null && Amsg.size == 3) {
                if(Amsg[0] != myId) { //남이나감
                    val user = realm.where(ChatMember::class.java).equalTo("RoomId", key).equalTo("UserId", Amsg[0]).findFirst()
                    if (user != null) {
                        realm.executeTransactionAsync(Realm.Transaction {
                            it.where(ChatMember::class.java).equalTo("RoomId", key).equalTo("UserId", Amsg[0]).findFirst()?.deleteFromRealm()
                            it.insert(ChatList().set(key, -1, Amsg[0], Amsg[1], Amsg[2].toLong()))
                        }, object : Realm.Transaction.OnSuccess {
                            override fun onSuccess() {
                                Log.i("chat", "exit user suc")
                            }
                        }, object : Realm.Transaction.OnError {
                            override fun onError(error: Throwable?) {
                                Log.e("chat", "delete err : ", error)
                            }
                        })
                    }
                } else { //내가나감
                    realm.executeTransactionAsync(Realm.Transaction {
                        it.where(ChatList::class.java).equalTo("RoomId", key).findAll()?.deleteAllFromRealm()
                        it.where(ChatMember::class.java).equalTo("RoomId", key).findAll()?.deleteAllFromRealm()
                        it.where(ChatRoom::class.java).equalTo("RoomId", key).findAll()?.deleteAllFromRealm()
                    }, object : Realm.Transaction.OnSuccess {
                        override fun onSuccess() {
                            Log.i("chat", "exit room suc")
                            for (c in mChats) c.DetectChange(key)
                        }
                    }, object : Realm.Transaction.OnError {
                        override fun onError(error: Throwable?) {
                            Log.e("chat", "exit room err : ", error)
                        }
                    })
                }
            } else { // 잘못된 메세지
                Log.e("chat","Incorrect msg")
            }
        } else if(kind == isUserS) {
            if(Atopic.size == 2 && Amsg.size == 1 && key == myId) {
                val realkey = realm.where(ChatRoom::class.java).equalTo("RoomId",Amsg[0]).findFirst()
                if(realkey == null)
                    mqttclient?.publish(Amsg[0]+'/'+isUserS+'/'+myId,"no".toByteArray(),1,false)
                else
                    mqttclient?.publish(Amsg[0]+'/'+isUserS+'/'+myId,"yes".toByteArray(),1,false)
            } else if(Atopic.size == 3 && Amsg.size == 1 && Atopic[2] != myId) {
                if(Amsg[0] == "no") {
                    val user = realm.where(ChatMember::class.java).equalTo("RoomId",key).equalTo("UserId",Atopic[2]).findFirst()
                    if(user != null) {
                        realm.executeTransactionAsync(Realm.Transaction {
                            user.deleteFromRealm()
                        },object : Realm.Transaction.OnSuccess {
                            override fun onSuccess() {
                                Log.i("chat","exit user suc")
                                mqttclient?.unsubscribe(key+'/'+isUserS+'/'+Atopic[2])
                            }
                        },object : Realm.Transaction.OnError {
                            override fun onError(error: Throwable?) {
                                Log.e("chat","delete err : ",error)
                            }
                        })
                    }
                }
            } else { // 잘못된 메세지
                Log.e("chat","Incorrect msg")
            }
        } else if(kind == newFS) { //친구요청받음
            if(key == myId && Amsg.size == 1) {
                val user = realm.where(Peoples::class.java).equalTo("UserId",Amsg[0]).findFirst()
                if(user != null && !user.isFriend) {
                    if(user.doRequest) { //내가 요청했었음
                        realm.executeTransactionAsync(Realm.Transaction {
                            user.isFriend = true
                            user.doRequest = false
                            user.Requested = false
                            user.isBan = false
                        }, object : Realm.Transaction.OnSuccess {
                            override fun onSuccess() {
                                Log.i("friends", "add friend : " + user.UserName)
                            }
                        }, object : Realm.Transaction.OnError {
                            override fun onError(error: Throwable?) {
                                Log.e("friends", "add friend err : ", error)
                            }
                        })
                    } else { //요청 받음
                        realm.executeTransactionAsync(Realm.Transaction {
                            user.Requested = true
                        }, object : Realm.Transaction.OnSuccess {
                            override fun onSuccess() {
                                Log.i("friends", "friend requested by : " + user.UserName)
                            }
                        }, object : Realm.Transaction.OnError {
                            override fun onError(error: Throwable?) {
                                Log.e("friends", "friend requested err : ", error)
                            }
                        })
                    }
                } else if(user == null) {
                    val user = Peoples().set(Amsg[0],Amsg[1])
                    user.Requested = true
                    realm.executeTransactionAsync(Realm.Transaction {
                        it.insert(user)
                    }, object : Realm.Transaction.OnSuccess {
                        override fun onSuccess() {
                            Log.i("friends", "friend requested by : " + Amsg[1])
                            SubscribePeople(Amsg[0])
                        }
                    }, object : Realm.Transaction.OnError {
                        override fun onError(error: Throwable?) {
                            Log.e("friends", "friend requested err : ", error)
                        }
                    })
                }
            } else {
                Log.e("friends","Incorrect msg")
            }
        } else if(kind == changeNS) {
            val realkey = realm.where(Peoples::class.java).equalTo("UserId",key).findFirst()
            if(realkey != null && Amsg.size == 1) {
                realm.executeTransactionAsync(Realm.Transaction {
                    realkey.UserName = Amsg[0]
                }, object : Realm.Transaction.OnSuccess {
                    override fun onSuccess() {
                        Log.i("friends", "friend requested by : " + Amsg[0])
                        for(c in mChats) c.DetectChange(key)
                    }
                }, object : Realm.Transaction.OnError {
                    override fun onError(error: Throwable?) {
                        Log.e("friends", "friend requested err : ", error)
                    }
                })
            } else {
                Log.e("friends","Incorrect msg")
            }
        }
    }

    fun makeRoom(users : Array<String>, name : String?) {
        var n = name
        val newrId = UUID.randomUUID().toString()
        if(n == null) {
            if(users.size == 1) n = realm.where(Peoples::class.java).equalTo("UserId",users[0]).findFirst()?.UserName
            else n = realm.where(Peoples::class.java).equalTo("UserId",users[0]).findFirst()?.UserName + " 외 " + (users.size-1) + "명"
        }
        realm.executeTransaction {
            it.insert(ChatRoom().set(newrId,n!!))
            for(u in users) it.insert(ChatMember().set(newrId,u))
        }
        SubscribeRoom(newrId)
        for(u in users) inviteUser(u,newrId)
        for (c in mChats) c.DetectChange(newrId)
    }

    fun CheckChats(roomid : String, userid : String) {
        mqttclient?.subscribe(roomid+'/'+lastIdS+'/'+userid,1,this,object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i("mqttsub","mqtt subscribe success")
                mqttclient?.publish(roomid+'/'+lastIdS+'/'+userid,null,1,false)
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("mqttsub",exception.toString())
            }
        })
    }

    fun pubChat(roomid : String, content : String) { // 채팅 보내기
        var max = realm.where(ChatList::class.java).equalTo("RoomId",roomid).equalTo("UserId",myId).max("ChatId")?.toInt()
        if(max == null) max = 1
        else max++
        val nowtime = Calendar.getInstance().timeInMillis
        mqttclient?.publish(roomid+'/'+chatS,(max.toString()+'/'+myId+'/'+content+'/'+nowtime).toByteArray(),1,false)
    }

    fun inviteUser(userid : String, roomid : String) { // 새 유저 초대
        val roomN = realm.where(ChatRoom::class.java).equalTo("RoomId",roomid).findFirst()?.RoomName
        val roomU = realm.where(ChatMember::class.java).equalTo("RoomId",roomid).findAll()
        var users = ""
        for(u in roomU) {
            val uN = realm.where(Peoples::class.java).equalTo("UserId",u.UserID).findFirst()?.UserName
            users += u.UserID+'&'+uN+','
        }
        mqttclient?.publish(userid+'/'+inviteRS,(roomid+'/'+roomN+'/'+users).toByteArray(),1,true)
        if(realm.where(Peoples::class.java).equalTo("UserId",userid).findFirst() == null) {
            realm.executeTransaction { it.insert(Peoples().set(userid,resources.getString(R.string.unknown))) }
        }
    }

    fun exitRoom(roomid : String) {
        mqttclient?.publish(roomid+'/'+exitRS,(myId+'/'+myN+resources.getString(R.string.chatout)+'/'+Calendar.getInstance().timeInMillis).toByteArray(),0,false)
    }

    fun checkisUser(roomid : String, userid : String) {
        mqttclient?.subscribe(roomid+'/'+isUserS+'/'+userid,1,this,object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                mqttclient?.publish(userid+'/'+isUserS,roomid.toByteArray(),1,false)
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("chat", "check user err : ", exception)
            }
        })
    }

    fun reqFriend(userid : String) {
        val user = realm.where(Peoples::class.java).equalTo("UserId",userid).findFirst()
        if(user != null && !user.isFriend && !user.isBan) {
            mqttclient?.publish(userid + '/' + newFS, (myId + '/' + myN).toByteArray(), 1, false, this, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    realm.executeTransactionAsync(Realm.Transaction {
                        user?.doRequest = true
                    }, object : Realm.Transaction.OnSuccess {
                        override fun onSuccess() {
                            Log.i("friends", "req friend")
                        }
                    }, object : Realm.Transaction.OnError {
                        override fun onError(error: Throwable?) {
                            Log.e("friends", "req friend err : ", error)
                        }
                    })
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("friends", "req friend err : ", exception)
                }
            })
        }
    }

    fun changeName(name : String) {
        mqttclient?.publish(myId+'/'+changeNS,name.toByteArray(),1,false,this,object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                realm.executeTransactionAsync(Realm.Transaction {
                    it.insert(realm.where(MyInfo::class.java).findFirst()?.set(name))
                }, object : Realm.Transaction.OnSuccess {
                    override fun onSuccess() {
                        Log.i("MyInfo", "change my name")
                    }
                }, object : Realm.Transaction.OnError {
                    override fun onError(error: Throwable?) {
                        Log.e("MyInfo", "change my name err : ", error)
                    }
                })
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("MyInfo", "change my name err : ", exception)
            }
        })
    }


    //핸들러를 통한 mqtt ping check
    private inner class customSender : MqttPingSender {
        val wakelock = (this@HService.getSystemService(Service.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "")
        var mComms : ClientComms? = null
        val pingCheck = Handler()
        val run = Runnable {
            wakelock.acquire()
            mComms?.checkForActivity(object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("mqttping","Pinging success")
                    wakelock.release()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.i("mqttping","Pinging fail : ", exception)
                    wakelock.release()
                }

            })
        }

        override fun schedule(delayInMilliseconds: Long) {
            pingCheck.postDelayed(run,delayInMilliseconds)
        }

        override fun start() {
            pingCheck.postDelayed(run, mComms?.keepAlive!!)
        }

        override fun stop() {
            pingCheck.removeCallbacks(run)
        }

        override fun init(comms: ClientComms) {
            mComms = comms
        }
    }
}