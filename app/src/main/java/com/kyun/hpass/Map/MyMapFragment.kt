package com.kyun.hpass.Map

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.kyun.hpass.R
import com.kyun.hpass.Service.HService
import com.kyun.hpass.realmDb.Basic.LocationMap
import com.kyun.hpass.util.objects.Singleton
import kotlinx.android.synthetic.main.fragment_mymap.*


@SuppressLint("ValidFragment")
class MyMapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mContext: Context

    private val trealm = Singleton.getBasicDB()

    private val fusedLocationClient: FusedLocationProviderClient by lazy { LocationServices.getFusedLocationProviderClient(mContext) }

    private val Map : MapView by lazy { mymap_map }
    private lateinit var lastLoca : Array<Double>

    private lateinit var Hs : HService
    private var isBind : Boolean = false

    private lateinit var mCallback : HService.MapCallBack

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            isBind = true
            Hs = (p1 as HService.MqttBinder).getService()
            if(::mCallback.isInitialized) Hs.registerMapCallback(mCallback)
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            isBind = false
            if(::mCallback.isInitialized) Hs.unregisterMapCallback(mCallback)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
        mContext.bindService(Intent(mContext,HService::class.java),conn,0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_mymap,null)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Map.onCreate(savedInstanceState)
        Map.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isIndoorLevelPickerEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false

        val myloca = trealm.where(LocationMap::class.java)
                .equalTo("UserId",Singleton.MyId)
                .greaterThan("time",System.currentTimeMillis() - 24*60*60*1000)
                .sort("time")
                .findAll()

        for(l in myloca) {
            map.addMarker(MarkerOptions().position(LatLng(l.latitude,l.longtitude)).title(Singleton.longToDateTimeString(l.time)))
            lastLoca = arrayOf(l.latitude,l.longtitude)
        }

        mCallback = object : HService.MapCallBack {
            override fun MyUpdate(loca : LocationMap) {
                map.addMarker(MarkerOptions().position(LatLng(loca.latitude,loca.longtitude)).title(Singleton.longToDateTimeString(loca.time)))
                lastLoca = arrayOf(loca.latitude,loca.longtitude)
            }
        }

        if (checkPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener {
                if(it != null) {
                    map.moveCamera(
                            CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.fromLatLngZoom(
                                            LatLng(it.latitude, it.longitude), 16.toFloat())))
                }
            }
        } else {
            // Show rationale and request permission.
        }

        val track = Button(mContext)
        Map.addView(track)
        val tlayoutparam = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT)
        track.layoutParams = tlayoutparam
        tlayoutparam.gravity = Gravity.RIGHT
        track.setOnClickListener {
            map.isMyLocationEnabled = !map.isMyLocationEnabled
            if (checkPermission() && map.isMyLocationEnabled) {
                fusedLocationClient.lastLocation.addOnSuccessListener {
                    if(it != null)
                        map.animateCamera(
                                CameraUpdateFactory.newCameraPosition(
                                        CameraPosition.fromLatLngZoom(
                                                LatLng(it.latitude, it.longitude), 17.toFloat())))
                }
            }
        }

        val tracklast = Button(mContext)
        Map.addView(tracklast)
        val tllayoutparam = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT)
        tracklast.layoutParams = tllayoutparam
        tllayoutparam.gravity = Gravity.LEFT
        tracklast.setOnClickListener {
            if(::lastLoca.isInitialized)
                map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                                CameraPosition.fromLatLngZoom(
                                        LatLng(lastLoca[0], lastLoca[1]), 15.toFloat())))
        }
    }


    override fun onResume() {
        super.onResume()
        Map.onResume()
    }

    private fun checkPermission() : Boolean {
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        Map.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if(isBind) {
            if(::mCallback.isInitialized) Hs.unregisterMapCallback(mCallback)
            mContext.unbindService(conn)
        }
        if(!trealm.isClosed) trealm.close()
    }

    override fun onStart() {
        super.onStart()
        Map.onStart()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Map.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        Map.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Map.onLowMemory()
    }
}