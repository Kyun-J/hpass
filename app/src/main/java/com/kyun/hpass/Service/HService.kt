package com.kyun.hpass.Service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.*
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kyun.hpass.App
import com.kyun.hpass.Chatting.Activity.ChattingActivity
import com.kyun.hpass.Main.Splash.SplashActivity
import org.eclipse.paho.client.mqttv3.*

import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import com.kyun.hpass.R
import com.kyun.hpass.realmDb.Basic.LocationMap
import com.kyun.hpass.realmDb.Nomal.*
import com.kyun.hpass.util.objects.*
import io.realm.Realm
import io.realm.Sort

import org.eclipse.paho.client.mqttv3.internal.ClientComms
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*
import kotlin.collections.ArrayList


/**
 * Created by kyun on 2018. 3. 13..
 */
class HService : Service() {

    //바인드 관련
    private val mBinder : IBinder = MqttBinder()
    private val mChats = Singleton.mChats
    private val mMaps = ArrayList<MapCallBack>()

    //주요 기능
    private lateinit var mqttclient : MqttAsyncClient
    private lateinit var realm : Realm
    private val trealm = Singleton.getBasicDB()
    private val MainHandler : Handler = Handler()
    private val SubThread = HandlerThread("SubThread")
    private lateinit var SubHandler : Handler
    private val fusedLocationClient: FusedLocationProviderClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    /** topics **/
    private val lastIdS = IgnoreValues.lastIdS
    private val reqChatS = IgnoreValues.reqChatS
    private val chatS = IgnoreValues.chatS
    private val inviteRS = IgnoreValues.inviteRS
    private val exitRS = IgnoreValues.exitRS
    private val isUserS = IgnoreValues.isUserS
    private val newFS = IgnoreValues.newFS
    private val toServer = IgnoreValues.toServer

    //일반
    private lateinit var pref : SharedPreferences
    private lateinit var notiM : NotificationManager
    private lateinit var notichann : String
    private lateinit var myId : String //user id
    private lateinit var myN : String //user name
    private val reqChatList = JsonObject()

    private val AllTopics : ArrayList<String> = ArrayList()

    private lateinit var RecAsync : AsyncReconect

    //바인드 세팅
    override fun onBind(p0: Intent): IBinder { return mBinder }

    inner class MqttBinder : Binder() {
        fun getService() : HService { return this@HService }
    }

    fun registerChatCallback(callback : ChatCallBack) {
        mChats.add(callback)
    }

    fun unregisterChatCallback(callback : ChatCallBack) {
        mChats.remove(callback)
    }

    interface ChatCallBack {
        fun newChat(RoomId: String, UserId: String, UserName : String, Content : String, Time : Long)
        fun newNoti(RoomId: String, Content: String, Time : Long)
        fun DetectChange(RoomId : String)
    }

    fun registerMapCallback(callback : MapCallBack) {
        mMaps.add(callback)
    }

    fun unregisterMapCallback(callback : MapCallBack) {
        mMaps.remove(callback)
    }

    interface MapCallBack {
        fun MyUpdate(loca : LocationMap)
    }

    fun isMqttAlive() : Boolean {
        if(mqttclient.isConnected) return true
        else {
            Singleton.noMqttErrToast(applicationContext)
            return false
        }
    }

    //생명주기
    override fun onCreate() { //초기값
        super.onCreate()
        Log.i("HService","Create")
        pref = PreferenceManager.getDefaultSharedPreferences(this)
        notiM = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notichann = resources.getString(R.string.noti_chat)
        if((application as App).isBackground()) {Singleton.init(this)}
        Singleton.keyCheck {
            if(!::realm.isInitialized) realm = Singleton.getNomalDB()
            myId = Singleton.MyId
            myN = Singleton.MyN
            SubThreadSet()
            if(!::mqttclient.isInitialized) mqttSetting()
            else roomCheck()
        }
        (application as App).HServiceForegroundListener {
            if(::mqttclient.isInitialized && !mqttclient.isConnected) {
                if(::RecAsync.isInitialized && RecAsync.status == AsyncTask.Status.RUNNING) {
                    RecAsync.cancel(true)
                }
                RecAsync = AsyncReconect()
                RecAsync.execute()
            }
        }
        isNetwork.Hlisten {

        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { //realm mqtt 세팅
        Log.i("HService","Start")
        return Service.START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() { //종료시
        super.onDestroy()
        Log.i("HService","Destroy")

        isNetwork.release(applicationContext)
        if(::realm.isInitialized && !realm.isClosed) realm.close()
        if(::mqttclient.isInitialized) mqttclient.isConnected.let { if(it) mqttclient.disconnect() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //재시작 알람
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + 5000,
                    PendingIntent.getService(this, 1, Intent(this, HService::class.java), 0))
        } else {
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, System.currentTimeMillis() + 5000,
                    PendingIntent.getService(this, 1, Intent(this, HService::class.java), 0))
        }
    }

    private fun SubThreadSet() {
        SubThread.start()
        SubHandler = Handler(SubThread.looper)
        val myloca = trealm.where(LocationMap::class.java).equalTo("UserId",myId).sort("time",Sort.DESCENDING).findAll()
        val now = System.currentTimeMillis()
        val next =
                if(myloca.size > 0 && now - myloca[0]!!.time > 0)
                    Status.findLocationInterval - (now - myloca[0]!!.time)
                else 0
        SubHandler.postDelayed(object : Runnable {
            override fun run() {
                if(ContextCompat.checkSelfPermission(this@HService,
                                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if(location != null){
                            Log.i("MyLoca","new Loca")
                            trealm.executeTransaction {
                                val loca = LocationMap().set(myId,location.latitude,location.longitude,System.currentTimeMillis())
                                it.insert(loca)
                                for(m in mMaps) m.MyUpdate(loca)
                            }
                        }
                    }
                }
                SubHandler.postDelayed(this,Status.findLocationInterval)
            }
        },next)
    }

    private fun mqttSetting() { //mqtt세팅
        if(!::mqttclient.isInitialized) {
            Log.i("mqtt", "mqtt setting start")
            mqttclient = MqttAsyncClient(IgnoreValues.BrokerUrl, myId, MemoryPersistence(), customSender())
            mqttclient.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String) {
                    Log.i("mqttconnect", "connect Success")
                    MainHandler.post {
                        SubscribeProcess()
                        roomCheck()
                    }
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    Log.i("mqttarrive", "topic : " + topic + " msg : " + message.toString() + " id : " + message.id + ", duplicate :" + message.isDuplicate + ", retain : " + message.isRetained + ", qos : " + message.qos)
                    MainHandler.post {
                        ArriveProcess(topic, message)
                    }
                }

                override fun connectionLost(cause: Throwable) {
                    Log.e("mqttlost", cause.toString())
                    MainHandler.post {
                        isNetwork.listen {
                            if (it && Singleton.realmKey.isNotEmpty() && (!::RecAsync.isInitialized || RecAsync.status == AsyncTask.Status.FINISHED)) {
                                RecAsync = AsyncReconect()
                                RecAsync.execute()
                            }
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.i("mqttpub", "success pub : " + token?.topics)
                }
            })
            val ConnectOption = MqttConnectOptions()
            ConnectOption.keepAliveInterval = 600
            ConnectOption.isCleanSession = false
            mqttclient.connect(ConnectOption)
        }
    }

    private fun roomCheck() {
        Singleton.RetroService.findMyRooms(Singleton.userToken).enqueue(object : Callback<JsonElement> {
            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                if(response.code() == 200) {
                    val json = response.body()!!.asJsonArray
                    for(j in json) {
                        val obj = j.asJsonObject
                        val rid = obj.get("_id").asString
                        val room = realm.where(ChatRoom::class.java).equalTo("RoomId",rid).findFirst()
                        if(room == null && obj.get("isGroup").asBoolean) {
                            val rname = obj.get("name").asString
                            realm.executeTransaction {
                                it.insert(ChatRoom().set(obj.get("_id").asString,rname))
                                for(u in obj.get("users").asJsonArray) it.insert(ChatMember().set(rid,u.asJsonObject.get("_id").asString))
                            }
                            for(c in mChats) c.DetectChange(rid)
                        } else {
                            val susers = obj.get("users").asJsonArray
                            val u1 = susers[0].asJsonObject.get("_id").asString
                            val u2 = susers[1].asJsonObject.get("_id").asString
                            if(u1 == Singleton.MyId) {
                                realm.executeTransaction {
                                    realm.where(Peoples::class.java).equalTo("UserId",u2).findFirst()?.pChatId = rid
                                }
                            } else {
                                realm.executeTransaction {
                                    realm.where(Peoples::class.java).equalTo("UserId",u1).findFirst()?.pChatId = rid
                                }
                            }
                        }
                    }
                } else if(response.code() == 202) {
                    if(response.body()!!.toString() == Codes.expireToken) {
                        Singleton.resetToken(this@HService, {
                            if(!it) {
                                Singleton.loginErrToast(this@HService)
                            } else {
                                Singleton.RetroService.findMyRooms(Singleton.userToken).enqueue(this)
                            }
                        })
                    } else Singleton.serverErrToast(this@HService, response.body()!!.toString())
                } else Singleton.serverErrToast(this@HService, Codes.serverErr)
            }

            override fun onFailure(call: Call<JsonElement>, t: Throwable) {
                Singleton.notOnlineToast(this@HService)
                Singleton.resetToken(this@HService, {
                    if(!it) {
                        Singleton.loginErrToast(this@HService)
                    } else {
                        Singleton.RetroService.findMyRooms(Singleton.userToken).enqueue(this)
                    }
                })
            }
        })
    }

    //mqtt 관련 함수들
    private fun SubscribeProcess() {
        val chatrooms = realm.where(ChatRoom::class.java).findAll()
        val ctsi = chatrooms.size

        val topics = Array(ctsi*5 + 3) {""}
        val qoses = IntArray(topics.size) {0}
        for(po in chatrooms.indices) {
            val rid = chatrooms[po]?.RoomId
            topics[po*5] = rid+'/'+lastIdS+'/'+myId
            topics[po*5+1] = rid+'/'+reqChatS+'/'+myId
            topics[po*5+2] = rid+'/'+chatS
            topics[po*5+3] = rid+'/'+inviteRS
            topics[po*5+4] = rid+'/'+exitRS
            qoses[po*5+3] = 1
            qoses[po*5+4] = 1
        }

        topics[topics.size-3] = myId+'/'+newFS
        topics[topics.size-2] = myId+'/'+inviteRS
        topics[topics.size-1] = myId+'/'+isUserS

        Subscribe(topics,qoses)
        AllTopics.addAll(topics)
    }

    private fun SubscribeRoom(roomid: String) {
        val topics = Array(5) {""}
        topics[0] = roomid+'/'+lastIdS+'/'+myId
        topics[1] = roomid+'/'+reqChatS+'/'+myId
        topics[2] = roomid+'/'+chatS
        topics[3] = roomid+'/'+inviteRS
        topics[4] = roomid+'/'+exitRS

        Subscribe(topics, intArrayOf(0,0,0,1,1))
        AllTopics.addAll(topics)
    }

    private fun unSubscribeRoom(roomid: String) {
        val topics = Array(5) {""}
        topics[0] = roomid+'/'+lastIdS+'/'+myId
        topics[1] = roomid+'/'+reqChatS+'/'+myId
        topics[2] = roomid+'/'+chatS
        topics[3] = roomid+'/'+inviteRS
        topics[4] = roomid+'/'+exitRS

        mqttclient.unsubscribe(topics)
        AllTopics.removeAll(topics)
    }


    private fun Subscribe(topics: Array<String>, qoses: IntArray) {
        mqttclient.subscribe(topics,qoses,this,object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i("mqttsub","subscribe " + asyncActionToken?.topics?.size)
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("mqttsub",exception.toString())
            }
        })
    }

    private fun Subscribe(topic : String,qos : Int) {
        mqttclient.subscribe(topic,qos,this,object : IMqttActionListener {
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
                    mqttclient.publish(key+'/'+lastIdS+'/'+myId,lastid.toString().toByteArray(),0,false)
                } else if(Amsg.size != 0){ //응답을 받음
                    var lastid = realm.where(ChatList::class.java).equalTo("RoomId",key).equalTo("UserId",Atopic[2]).max("ChatId")?.toInt()
                    Subscribe(key+'/'+reqChatS+'/'+Amsg[0],1)
                    mqttclient.unsubscribe(key+'/'+lastIdS+'/'+Amsg[0])
                    AllTopics.remove(key+'/'+lastIdS+'/'+Amsg[0])
                    val start = if(lastid == null) 0 else 1
                    if(lastid == null) lastid = Amsg[0].toInt()
                    reqChatList.addProperty(key,Amsg[0].toInt() - lastid)
                    for(i in start..(Amsg[0].toInt() - lastid)) //내 최대아이디가 낮으면 차이만큼 메세지 내용 요청
                        mqttclient.publish(key+'/'+reqChatS+'/'+Amsg[0],(lastid+i).toString().toByteArray(),0,false)
                }
            } else {
                Log.e("chat","Incorrect msg")
            }
        }
        else if(kind == reqChatS) {
            val realkey = realm.where(ChatRoom::class.java).equalTo("RoomId",key).findFirst()
            if(realkey != null && Atopic.size == 3 && Amsg.size != 0) {
                if (Atopic[2] == myId) {
                    val cons = realm.where(ChatList::class.java).equalTo("RoomId", key).equalTo("UserId", myId).equalTo("ChatId", Amsg[0]).findFirst()
                    if (cons != null) mqttclient.publish(key + '/' + reqChatS + '/' + myId, (Amsg[0] + '/' + cons.Content + '/' + cons.Time).toByteArray(), 0, false)
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
                                    mqttclient.unsubscribe(key+'/'+reqChatS+'/'+Atopic[2])
                                    AllTopics.remove(key+'/'+reqChatS+'/'+Atopic[2])
                                }
                                if(Singleton.isChattingRoom(application,key)) for(c in mChats) c.DetectChange(key)
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
        else if(kind == chatS) { //채팅 메세지 받음
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
                            var username = realm.where(Peoples::class.java).equalTo("UserId", Amsg[1]).findFirst()?.UserName
                            if (username == null) username = resources.getString(R.string.unknown)!!
                            if (!Singleton.isChattingRoom(application, key) && realkey.isAlarm && Amsg[1] != myId) { // 알람 발생
                                var nm = pref.getInt(key, -1)
                                if (nm == -1) {
                                    nm = pref.getInt("notimax", 0)
                                    val edit = pref.edit()
                                    edit.putInt(key, nm)
                                    edit.putInt("notimax", nm + 1)
                                    edit.commit()
                                }
                                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    notiM.notify(nm, NotificationCompat.Builder(this@HService,notichann)
                                            .setContentIntent(PendingIntent.getActivity(this@HService, nm,
                                                    Intent(this@HService, SplashActivity::class.java).putExtra("chatid", key),
                                                    PendingIntent.FLAG_UPDATE_CURRENT))
                                            .setSmallIcon(R.mipmap.ic_launcher_round)
                                            .setPriority(NotificationCompat.PRIORITY_MAX)
                                            .setContentTitle(username)
                                            .setContentText(Amsg[2]).build())

                                } else {
                                    notiM.notify(nm, NotificationCompat.Builder(this@HService)
                                            .setContentIntent(PendingIntent.getActivity(this@HService, nm,
                                                    Intent(this@HService, SplashActivity::class.java).putExtra("chatid", key),
                                                    PendingIntent.FLAG_UPDATE_CURRENT))
                                            .setPriority(NotificationCompat.PRIORITY_MAX)
                                            .setSmallIcon(R.mipmap.ic_launcher_round)
                                            .setVibrate(longArrayOf(500, 500, 500)).setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                                            .setContentTitle(username)
                                            .setContentText(Amsg[2]).build())
                                }
                            }
                            for (c in mChats) c.newChat(key, Amsg[1], username, Amsg[2], Amsg[3].toLong())
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
        }
        else if(kind == inviteRS) {// 채팅방 초대
            val realkey = realm.where(ChatRoom::class.java).equalTo("RoomId",key).findFirst()
            if(realkey != null && Amsg[0] != myId) {//초대된 유저 확인
                if(realm.where(ChatMember::class.java).equalTo("RoomId",key).equalTo("UserId",Amsg[0]).findFirst() == null) {
                    val user = realm.where(Peoples::class.java).equalTo("UserId",Amsg[0]).findFirst()
                    if(user == null) {
                        realm.executeTransactionAsync(Realm.Transaction {
                            it.insert(ChatMember().set(key, Amsg[0]))
                            it.insert(Peoples().set(Amsg[0],Amsg[1]))
                            it.insert(ChatList().set(key, 0, Amsg[0], Amsg[1]+resources.getString(R.string.newin), Amsg[2].toLong()))
                        }, object : Realm.Transaction.OnSuccess {
                            override fun onSuccess() {
                                Log.i("chat", "newUser in : " + realkey.RoomName)
                                for(c in mChats) c.newNoti(key,Amsg[1]+resources.getString(R.string.newin),Amsg[2].toLong())
                            }
                        }, object : Realm.Transaction.OnError {
                            override fun onError(error: Throwable?) {
                                Log.e("chat", "invite user err : ", error)
                            }
                        })
                    } else {
                        realm.executeTransactionAsync(Realm.Transaction {
                            it.insert(ChatMember().set(key, Amsg[0]))
                            it.insert(ChatList().set(key, 0, Amsg[0], user.UserName+resources.getString(R.string.newin), Amsg[2].toLong()))
                        }, object : Realm.Transaction.OnSuccess {
                            override fun onSuccess() {
                                Log.i("chat", "newUser in : " + realkey.RoomName)
                                for(c in mChats) c.newNoti(key,user.UserName+resources.getString(R.string.newin),Amsg[2].toLong())
                            }
                        }, object : Realm.Transaction.OnError {
                            override fun onError(error: Throwable?) {
                                Log.e("chat", "invite user err : ", error)
                            }
                        })
                    }
                }
            } else if(key == myId)  { //초대를 받음
                if(realm.where(ChatRoom::class.java).equalTo("RoomId",Amsg[0]).findFirst() == null) {
                    val users = Amsg[2].split(',')
                    val umodel = ArrayList<ChatMember>()
                    val pmodel = ArrayList<Peoples>()
                    val nowtime = System.currentTimeMillis()
                    var roomname = ""
                    if(users.size == 3) {
                        val info = users[0].split('&')
                        umodel.add(ChatMember().set(Amsg[0],myId))
                        umodel.add(ChatMember().set(Amsg[0],info[0]))
                        realm.executeTransaction {
                            var p = it.where(Peoples::class.java).equalTo("UserId", info[0]).findFirst()
                            if (p == null) {
                                p = Peoples().set(info[0], info[1])
                                p.pChatId = Amsg[0]
                                it.insert(p)
                                roomname = "알수없음"
                            } else {
                                p.pChatId = Amsg[0]
                                roomname = p.UserName
                            }
                        }
                    } else {
                        roomname = Amsg[1]
                        for (u in users) {
                            val info = u.split('&')
                            if (info.size == 2 && info[0] != myId) {
                                umodel.add(ChatMember().set(Amsg[0], info[0]))
                                if (realm.where(Peoples::class.java).equalTo("UserId", info[0]).findFirst() == null)
                                    pmodel.add(Peoples().set(info[0], info[1]))
                            }
                        }
                    }
                    realm.executeTransactionAsync(Realm.Transaction {
                        it.insert(ChatRoom().set(Amsg[0], roomname))
                        it.insert(umodel)
                        it.insert(pmodel)
                        it.insert(ChatList().set(Amsg[0], 0, myId, myN + resources.getString(R.string.newin), nowtime))
                    }, object : Realm.Transaction.OnSuccess {
                        override fun onSuccess() {
                            Log.i("chat", "join suc")
                            for (c in mChats) c.DetectChange(Amsg[0])
                            mqttclient.publish(Amsg[0] + '/' + inviteRS, (myId + '/' + myN + '/' + nowtime).toByteArray(), 1, false) //내가 초대된 사실 알림
                            SubscribeRoom(Amsg[0])
                        }
                    }, object : Realm.Transaction.OnError {
                        override fun onError(error: Throwable?) {
                            Log.e("chat", "join err : ", error)
                        }
                    })
                }
            } else { // 잘못된 메세지
                Log.e("chat","Incorrect msg")
            }
        }
        else if(kind == exitRS) {
            val realkey = realm.where(ChatRoom::class.java).equalTo("RoomId",key).findFirst()
            if(realkey != null && Amsg.size == 3) {
                if(Amsg[0] != myId) { //남이나감
                    val user = realm.where(ChatMember::class.java).equalTo("RoomId", key).equalTo("UserId", Amsg[0]).findFirst()
                    val isOnO = realm.where(Peoples::class.java).equalTo("pChatId",key).findFirst()
                    if (user != null && isOnO == null) {
                        realm.executeTransactionAsync(Realm.Transaction {
                            it.where(ChatMember::class.java).equalTo("RoomId", key).equalTo("UserId", Amsg[0]).findFirst()?.deleteFromRealm()
                            it.insert(ChatList().set(key, -1, Amsg[0], Amsg[1], Amsg[2].toLong()))
                        }, object : Realm.Transaction.OnSuccess {
                            override fun onSuccess() {
                                Log.i("chat", "exit user suc")
                                for(c in mChats) c.newNoti(key,realm.where(Peoples::class.java).equalTo("UserId",Amsg[0]).findFirst()!!.UserName+resources.getString(R.string.chatout),Amsg[2].toLong())
                            }
                        }, object : Realm.Transaction.OnError {
                            override fun onError(error: Throwable?) {
                                Log.e("chat", "delete err : ", error)
                            }
                        })
                    }
                } else { //내가나감
                    unSubscribeRoom(key)
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
        }
        else if(kind == isUserS) {
            if(Atopic.size == 2 && Amsg.size == 1 && key == myId) {
                val realkey = realm.where(ChatRoom::class.java).equalTo("RoomId",Amsg[0]).findFirst()
                if(realkey == null)
                    mqttclient.publish(Amsg[0]+'/'+isUserS+'/'+myId,"no".toByteArray(),0,false)
                else
                    mqttclient.publish(Amsg[0]+'/'+isUserS+'/'+myId,"yes".toByteArray(),0,false)
            } else if(Atopic.size == 3 && Amsg.size == 1 && Atopic[2] != myId) {
                if(Amsg[0] == "no") {
                    val user = realm.where(ChatMember::class.java).equalTo("RoomId",key).equalTo("UserId",Atopic[2]).findFirst()
                    if(user != null) {
                        realm.executeTransactionAsync(Realm.Transaction {
                            user.deleteFromRealm()
                        },object : Realm.Transaction.OnSuccess {
                            override fun onSuccess() {
                                Log.i("chat","exit user suc")
                                mqttclient.unsubscribe(key+'/'+isUserS+'/'+Atopic[2])
                                AllTopics.remove(key+'/'+isUserS+'/'+Atopic[2])
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
        }
    }

    //bind된 곳에서 사용하는 public 함수들
    fun makeOnO(user : Peoples) {
        val newRid =  UUID.randomUUID().toString()
        realm.executeTransaction {
            it.insert(ChatRoom().set(newRid,user.UserName))
            it.insert(ChatMember().set(newRid,myId))
            it.insert(ChatMember().set(newRid,user.UserId))
            it.insert(ChatList().set(newRid,0,myId,myN+resources.getString(R.string.newin),System.currentTimeMillis()))
            it.where(Peoples::class.java).equalTo("UserId",user.UserId).findFirst()!!.pChatId = newRid
        }
        inviteUser(user.UserId,newRid)
        for (c in mChats) c.DetectChange(newRid)
        SubscribeRoom(newRid)
        startActivity(Intent(this,ChattingActivity::class.java).putExtra("id",newRid).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun makeGroup(users : ArrayList<String>, name : String?) {
        var n = name
        val newrId = UUID.randomUUID().toString()
        if(n == null) {
            n = realm.where(Peoples::class.java).equalTo("UserId",users[0]).findFirst()?.UserName + " 외 " + (users.size) + "명"
        }
        realm.executeTransaction {
            it.insert(ChatRoom().set(newrId,n))
            it.insert(ChatMember().set(newrId,myId))
            it.insert(ChatList().set(newrId,0,myId,myN+resources.getString(R.string.newin),System.currentTimeMillis()))
            for(u in users) it.insert(ChatMember().set(newrId,u))
        }
        for(u in users) inviteUser(u,newrId)
        for (c in mChats) c.DetectChange(newrId)
        SubscribeRoom(newrId)
        startActivity(Intent(this,ChattingActivity::class.java).putExtra("id",newrId))
    }

    fun remakeOnO(user : Peoples, rid : String) {
        realm.executeTransaction {
            it.insert(ChatRoom().set(rid,user.UserName))
            it.insert(ChatMember().set(rid,myId))
            it.insert(ChatMember().set(rid,user.UserId))
            it.insert(ChatList().set(rid,0,myId,myN+resources.getString(R.string.newin),System.currentTimeMillis()))
        }
        for (c in mChats) c.DetectChange(rid)
        SubscribeRoom(rid)
        startActivity(Intent(this,ChattingActivity::class.java).putExtra("id",rid).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun CheckChats(roomid : String, userid : String) {
        mqttclient.subscribe(roomid+'/'+lastIdS+'/'+userid,0,this,object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i("mqttsub","mqtt subscribe success")
                mqttclient.publish(roomid+'/'+lastIdS+'/'+userid,null,0,false)
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("mqttsub",exception.toString())
            }
        })
        AllTopics.add(roomid+'/'+lastIdS+'/'+userid)
    }

    fun pubChat(roomid : String, content : String) { // 채팅 보내기
        var max = realm.where(ChatList::class.java).equalTo("RoomId",roomid).equalTo("UserId",myId).max("ChatId")?.toInt()
        if(max == null) max = 1
        else max++
        mqttclient.publish(roomid+'/'+chatS,(max.toString()+'/'+myId+'/'+content+'/'+System.currentTimeMillis()).toByteArray(),0,false)
    }

    private fun inviteUser(userid : String, roomid : String) { // 새 유저 초대
        val roomN = realm.where(ChatRoom::class.java).equalTo("RoomId",roomid).findFirst()?.RoomName
        val roomU = realm.where(ChatMember::class.java).equalTo("RoomId",roomid).findAll()
        var users = myId+'&'+myN+','
        for(u in roomU) {
            if(u.UserId != myId) {
                val uN = realm.where(Peoples::class.java).equalTo("UserId", u.UserId).findFirst()?.UserName
                users += u.UserId + '&' + uN + ','
            }
        }
        mqttclient.publish(userid+'/'+inviteRS,(roomid+'/'+roomN+'/'+users).toByteArray(),1,false)
    }

    fun exitRoom(roomid : String) {
        mqttclient.publish(roomid+'/'+exitRS,(myId+'/'+myN+resources.getString(R.string.chatout)+'/'+System.currentTimeMillis()).toByteArray(),1,false)
    }

    fun checkisUser(roomid : String, userid : String) {
        mqttclient.subscribe(roomid+'/'+isUserS+'/'+userid,0,this,object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                mqttclient.publish(userid+'/'+isUserS,roomid.toByteArray(),0,false)
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("chat", "check user err : ", exception)
            }
        })
        AllTopics.add(roomid+'/'+isUserS+'/'+userid)
    }

    fun logout() {
        mqttclient.unsubscribe(AllTopics.toArray(Array(AllTopics.size) {""}))
        mqttclient.disconnect()
        if(!realm.isClosed) realm.close()
        if(!trealm.isClosed) trealm.close()
        Singleton.keyCheck {
            realm = Singleton.getNomalDB()
            myId = Singleton.MyId
            myN = Singleton.MyN
            RecAsync = AsyncReconect()
            RecAsync.execute()
        }
    }

    private inner class AsyncReconect : AsyncTask<Void,Void,Void>() {
        private var delay : Long = 5000
        override fun doInBackground(vararg p0: Void?): Void? {
            try {
                while (!mqttclient.isConnected) {
                    publishProgress()
                    Thread.sleep(delay)
                    if (delay < 300000) delay += 1000
                }
            } catch(e : InterruptedException) {
                Log.e("mqttreconnect","reconnect interrupt")
            }
            return null
        }

        override fun onProgressUpdate(vararg values: Void?) {
            super.onProgressUpdate(*values)
            try{
                Log.i("mqttreconnect","try reconnect...")
                mqttclient.reconnect()
            } catch (e : MqttException){
                Log.e("mqttreconnect","reconnect exception ",e)
            }
        }
    }

    //mqtt ping check with Thread and wakelock
    private inner class customSender : MqttPingSender {
        private lateinit var mComms : ClientComms

        private var isStop : Boolean = false

        override fun schedule(delayInMilliseconds: Long) {
            SubHandler.postDelayed({
                if(!isStop) {
                    val wakelock = (this@HService.getSystemService(Service.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MqttPing")
                    wakelock.acquire()
                    mComms.checkForActivity(object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.i("mqttping", "Pinging success")
                            wakelock.release()
                        }

                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.e("mqttping", "Pinging fail : ", exception)
                            wakelock.release()
                        }
                    })
                }
            },delayInMilliseconds)

//            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
//            val nextInterval : Long =
//                    if(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1) != 0) {
//                        20*1000
//                    } else {
//                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)
//                        if(level >= 75) (60*1000).toLong()
//                        else if(level >= 50)  (300*1000).toLong()
//                        else 600*1000
//                    }
//            if(mComms.keepAlive != nextInterval)
//                mComms.clientState.setKeepAliveInterval(nextInterval)
        }

        override fun start() {
            isStop = false
            schedule(mComms.keepAlive)
        }

        override fun stop() {
            Log.i("mqttping","stop")
            isStop = true
            SubHandler.postAtFrontOfQueue {
                val wakelock = (this@HService.getSystemService(Service.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MqttPing")
                if(wakelock.isHeld) wakelock.release()
            }

        }

        override fun init(comms: ClientComms) {
            mComms = comms
        }
    }
}