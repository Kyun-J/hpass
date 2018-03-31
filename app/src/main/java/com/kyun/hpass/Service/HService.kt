package com.kyun.hpass.Service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.*
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.kyun.hpass.objects.Singleton
import org.eclipse.paho.client.mqttv3.*

import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import com.kyun.hpass.Chatting.ChattingActivity
import com.kyun.hpass.Main.CustomApplication
import com.kyun.hpass.R
import com.kyun.hpass.objects.IgnoreValues
import com.kyun.hpass.realmDb.ChatList
import com.kyun.hpass.realmDb.ChatMember
import com.kyun.hpass.realmDb.ChatRoom
import com.kyun.hpass.realmDb.Peoples
import io.realm.Realm
import org.eclipse.paho.client.mqttv3.internal.ClientComms
import java.util.Calendar
import kotlin.collections.ArrayList


/**
 * Created by kyun on 2018. 3. 13..
 */
class HService : Service() {

    private val mBinder : IBinder = MqttBinder()
    private val mChats = ArrayList<ChatCallBack>()

    private var mqttclient : MqttAsyncClient? = null

    private val chatS = resources.getString(R.string.chat)
    private val inviteRS = resources.getString(R.string.inviteR)
    private val updateRS = resources.getString(R.string.updateR)
    private val newFS = resources.getString(R.string.newF)

    private var nid : Int = 0
    private var myId : String = ""

    private var RSup = false;

    override fun onBind(p0: Intent): IBinder { return mBinder }

    inner class MqttBinder : Binder() {
        fun getService() : HService { return this@HService }
    }

    interface ChatCallBack {
        fun ArriveChat(RoomId: String, UserName : String, Content : String, Time : String)
        fun DetectChange(RoomId : String)
    }

    fun registerCallback(callback : ChatCallBack) {
        mChats.add(callback)
    }

    fun unregisterCallback(callback : ChatCallBack) {
        mChats.remove(callback)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("HService","Destroy")
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt("nid",nid).commit()
        mqttclient!!.disconnect()
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(AlarmManager.RTC, Calendar.getInstance().timeInMillis + 5000,
                    PendingIntent.getService(this, 1, Intent(this, HService::class.java), 0))
        } else {
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, Calendar.getInstance().timeInMillis + 5000,
                    PendingIntent.getService(this, 1, Intent(this, HService::class.java), 0))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("HService","Start")
        myId = Singleton.getUserId(this)
        nid = PreferenceManager.getDefaultSharedPreferences(this).getInt("nid",0)
        mqttSetting()
        return Service.START_STICKY
    }

    private fun mqttSetting() {
        if(mqttclient == null) {
            mqttclient = MqttAsyncClient(IgnoreValues.BrokerUrl, myId, MemoryPersistence(), customSender())
            mqttclient!!.setCallback(object : MqttCallbackExtended{
                override fun connectComplete(reconnect: Boolean, serverURI: String) {
                    Log.i("mqttconnect","connect Success")
                    SubscribeProcess()
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    Log.i("mqttarrive","topic : " + topic + " msg : " + message.toString() + " id : " + message.id + ", duplicate :" + message.isDuplicate + ", retain : " + message.isRetained + ", qos : " + message.qos)
                    ArriveProcess(topic,message)
                }

                override fun connectionLost(cause: Throwable) {
                    Log.e("mqttlost",cause.toString())
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.i("mqttpub","success pub : " + token!!.message.toString())
                }
            })
        }

        if(!mqttclient!!.isConnected) {
            val ConnectOption = MqttConnectOptions()
            ConnectOption.isAutomaticReconnect = true
            ConnectOption.connectionTimeout = 600
            ConnectOption.isCleanSession = false
            mqttclient!!.connect(ConnectOption)
        }
    }

    private fun SubscribeProcess() {
        val realm = Realm.getInstance(Singleton.mConfig)

        val chatrooms = realm.where(ChatRoom::class.java).findAllAsync()

        val topics = Array<String>(chatrooms.size*3) {""}
        for(po in chatrooms.indices) {
            if(chatrooms.size / po == 0) topics.set(po,chatrooms[po]!!.RoomId+'/'+chatS)
            else if(chatrooms.size / po == 1) topics.set(po,chatrooms[chatrooms.size%po]!!.RoomId+'/'+inviteRS)
            else if(chatrooms.size / po == 2) topics.set(po,chatrooms[chatrooms.size%po]!!.RoomId+'/'+updateRS)
        }
        val qoses = IntArray(topics.size) {1}
        //....
        mqttclient!!.subscribe(topics,qoses,this,object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i("mqttsub","mqtt subscribe success")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("mqttsub",exception.toString())
                mqttclient!!.reconnect()
            }
        })
    }

    private fun SubscribeRoom(roomid: String) {
        val topics = Array<String>(3) {""}
        topics.set(0,roomid+'/'+chatS)
        topics.set(1,roomid+'/'+inviteRS)
        topics.set(2,roomid+'/'+updateRS)

        mqttclient!!.subscribe(topics,IntArray(topics.size) {1},this,object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i("mqttsub","mqtt subscribe success")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("mqttsub",exception.toString())
                mqttclient!!.reconnect()
            }
        })
    }

    private fun ArriveProcess(topic : String, message : MqttMessage) {
        val Atopic = topic.split('/')
        val Amsg = message.toString().split('/')
        val key = Atopic[0]
        val kind = Atopic[1]

        val realm = Realm.getInstance(Singleton.mConfig)

        if(kind == chatS) { //채팅 메세지 받음
            val result = realm.where(ChatRoom::class.java).equalTo("RoomId",key).findFirst()
            if(result != null) { // 자신의 채팅방 여부
                realm.executeTransactionAsync(Realm.Transaction {
                    realm.insert(ChatList().set(key, Integer.parseInt(Amsg[0]), Amsg[1], Amsg[2], Amsg[3])) // 채팅 내용 저장
                }, object : Realm.Transaction.OnSuccess {
                    override fun onSuccess() {
                        Log.i("realmchat","insert chat in : " + result.RoomName)
                        val username = realm.where(Peoples::class.java).equalTo("UserId",Amsg[1]).findFirst()!!.UserName
                        for(c in mChats) c.ArriveChat(key,username,Amsg[2],Amsg[3])
                        if(!((application as CustomApplication).TopActivity is ChattingActivity)||
                            ((application as CustomApplication).TopActivity as ChattingActivity).RoomId != key) { // 알람 울림
                                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(nid,
                                        NotificationCompat.Builder(this@HService, "mqtt")
                                                .setSmallIcon(R.mipmap.ic_launcher_round)
                                                .setVibrate(longArrayOf(500, 500, 500)).setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                                                .setPriority(NotificationCompat.PRIORITY_HIGH).setContentTitle(Amsg[2])
                                                .setContentText(message.toString()).build())
                                if(nid + 1 == Int.MAX_VALUE) nid = 0
                                else nid ++
                        }
                        realm.close()
                    }
                }, object : Realm.Transaction.OnError {
                    override fun onError(error: Throwable) {
                        Log.e("realmchat","insert chat err : ",error)
                        realm.close()
                    }
                })
            } else { // 잘못된 메세지
                Log.e("realmchat","Incorrect msg")
                realm.close()
            }
        } else if(kind == inviteRS) {// 채팅방 초대
            val result = realm.where(ChatRoom::class.java).equalTo("RoomId",key).findFirst()
            if(result != null) {// 새 멤버 추가
                realm.executeTransactionAsync(Realm.Transaction {
                    realm.insert(ChatMember().set(key,Amsg[0]))
                },object : Realm.Transaction.OnSuccess {
                    override fun onSuccess() {
                        for(c in mChats) c.DetectChange(key)
                        Log.i("realmchat","newUser in : " + result.RoomName)
                    }
                },object : Realm.Transaction.OnError{
                    override fun onError(error: Throwable?) {
                        Log.e("realmchat","invite user err : ",error)
                    }
                })
                realm.close()
            } else if(key == myId){ // 초대 받음
                val users = Amsg[2].split(',')
                val umodel = ArrayList<ChatMember>()
                for(u in users) umodel.add(ChatMember().set(Amsg[0],u))
                realm.executeTransactionAsync(Realm.Transaction {
                    realm.insert(ChatRoom().set(Amsg[0],Amsg[1]))
                    realm.insert(umodel)
                },object : Realm.Transaction.OnSuccess {
                    override fun onSuccess() {
                        for(c in mChats) c.DetectChange(Amsg[0])
                        SubscribeRoom(Amsg[0])
                        Log.i("realmchat","join in : " + Amsg[1])
                    }
                },object : Realm.Transaction.OnError{
                    override fun onError(error: Throwable?) {
                        Log.e("realmchat","join err : ",error)
                    }
                })
                realm.close()
            } else { // 잘못된 메세지
                Log.e("realmchat","Incorrect msg")
                realm.close()
            }
        } else if(kind == updateRS) {


        }
    }

    fun pubChat(roomid : String, content : String) { // 채팅 보내기


    }

    fun inviteUser(userid : String, roomid : String, roomname : String) { // 새 유저 초대
        mqttclient!!.publish(roomid+'/'+inviteRS,userid.toByteArray(),1,true,this,object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                val realm = Realm.getInstance(Singleton.mConfig)
                val result = realm.where(ChatMember::class.java).equalTo("RoomId",roomid).findAll()
                var payload = roomid + '/' + roomname + '/'
                for(i in result) payload += i.UserID + ','
                mqttclient!!.publish(userid+'/'+inviteRS,payload.toByteArray(),1,true,this,object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("realmchat","invite success : " + userid)
                        realm.executeTransactionAsync { realm.insert(ChatMember().set(roomid,userid)) }
                        realm.close()
                    }
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("realmchat","invite user err : ",exception)
                        //err dialog
                    }
                })
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                //err dialog
            }
        })
    }

    fun requestUpdate(roomid : String) {
        mqttclient!!.publish(roomid+'/'+updateRS,myId.toByteArray(),1,false,this,object : IMqttActionListener{
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                mqttclient!!.subscribe(roomid+'/'+updateRS+'/'+myId,1)
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        })
    }


    private inner class customSender : MqttPingSender {
        val wakelock = (this@HService.getSystemService(Service.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "")
        var mComms : ClientComms? = null
        val pingCheck = Handler()
        val run = Runnable {
            wakelock.acquire()
            mComms!!.checkForActivity(object : IMqttActionListener {
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
            pingCheck.postDelayed(run,mComms!!.keepAlive)
        }

        override fun stop() {
            pingCheck.removeCallbacks(run)
        }

        override fun init(comms: ClientComms) {
            mComms = comms
        }
    }
}