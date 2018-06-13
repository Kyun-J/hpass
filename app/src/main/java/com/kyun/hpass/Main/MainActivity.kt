package com.kyun.hpass.Main

import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v7.app.ActionBar
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.gson.JsonElement
import com.kyun.hpass.Chatting.Activity.ChattingActivity
import com.kyun.hpass.Main.FriendSearch.IdSearchActivity
import com.kyun.hpass.Main.FriendSearch.PhoneSearchActivity
import com.kyun.hpass.Main.MakeRoom.MakeRoomActivity
import com.kyun.hpass.R
import com.kyun.hpass.util.objects.Singleton
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.appbar_main.*
import kotlinx.android.synthetic.main.include_main_fab.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*


class MainActivity : AppCompatActivity() {

    private var pagePosition : Int = 0
    private var searchMode : Boolean = false
    private var isSearchVisible : Boolean = false
    private var isfabOn : Boolean = false
    private lateinit var imm : InputMethodManager

    private var fabOpen : Animation? = null
    private var fabClose : Animation? = null

    private var backPressTime : Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backPressTime = Calendar.getInstance().timeInMillis
        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        setContentView(R.layout.activity_main)
        setSupportActionBar(main_toolbar)
        supportActionBar!!.setDisplayShowTitleEnabled(false)
        supportActionBar!!.setDisplayShowCustomEnabled(true)
        supportActionBar!!.setCustomView(
                layoutInflater.inflate(R.layout.appbar_main,null),
                ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
        appbar_main_title.text = "지인"
        appbar_main_search.setOnClickListener {
            if(!searchMode) {
                searchMode = true
                isSearchVisible = true
                appbar_main_title.visibility = View.GONE
                appbar_main_search_mode.visibility = View.VISIBLE
                appbar_main_search_edit.requestFocus()
                imm.showSoftInput(appbar_main_search_edit,InputMethodManager.SHOW_FORCED)
            } else {
                search(appbar_main_search_edit.text.toString())
            }
        }
        appbar_main_search_cancel.setOnClickListener {
            searchMode = false
            closeSearchMode()
        }
        appbar_main_search_clear.setOnClickListener {
            appbar_main_search_edit.setText("")
        }
        appbar_main_search_edit.setOnEditorActionListener { textView, i, keyEvent ->
            if(i == EditorInfo.IME_ACTION_SEARCH) {
                search(textView.text.toString())
                true
            }
            false
        }
        main_pager.adapter = MainPagerAdapter(supportFragmentManager)
        main_pager.offscreenPageLimit = 5
        main_tablayout.setupWithViewPager(main_pager)
        main_pager.addOnPageChangeListener(object : TabLayout.TabLayoutOnPageChangeListener(main_tablayout) {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if(isfabOn) closeFab{}
                pagePosition = position
                appbar_main_title.text =
                        if(position == 0) "지인"
                        else if(position == 1) "채팅"
                        else if(position == 2) "지도"
                        else if(position == 3) "설정"
                        else ""
                if(position > 1) {
                    appbar_main_search.visibility = View.GONE
                    main_fab.visibility = View.GONE
                    if(searchMode) closeSearchMode()
                } else {
                    main_fab.visibility = View.VISIBLE
                    appbar_main_search.visibility = View.VISIBLE
                    if(searchMode && !isSearchVisible) {
                        isSearchVisible = true
                        appbar_main_title.visibility = View.GONE
                        appbar_main_search_mode.visibility = View.VISIBLE
                    }
                }
            }
        })
        fabOpen = AnimationUtils.loadAnimation(this,R.anim.fab_open)
        fabClose = AnimationUtils.loadAnimation(this,R.anim.fab_close)
        fab_backgroud.setOnClickListener(fabClick)
        main_fab.setOnClickListener(fabClick)
        fab_btn_1.setOnClickListener(fabClick)
        fab_btn_2.setOnClickListener(fabClick)


        try{
            val chat = intent.extras.getString("chatid")
            if(chat != null && chat != "") startActivity(Intent(this, ChattingActivity::class.java).putExtra("id",chat))
        } catch ( e : NullPointerException) {
        }
    }

    private val fabClick =
            View.OnClickListener {
                val id = it.id
                when(id) {
                    R.id.fab_backgroud -> if(isfabOn) closeFab { }
                    R.id.main_fab -> {
                        if(pagePosition == 0) {
                            if(!isfabOn) {
                                isfabOn = true
                                fab_backgroud.visibility = View.VISIBLE
                                fab_backgroud.bringToFront()
                                fab_btn_1.visibility = View.VISIBLE
                                fab_btn_2.visibility = View.VISIBLE
                                fab_text_1.visibility = View.VISIBLE
                                fab_text_2.visibility = View.VISIBLE
                                fab_text_1.text = "연락처로 검색"
                                fab_text_2.text = "아이디로 검색"
                                fab_btn_1.startAnimation(fabOpen)
                                fab_btn_2.startAnimation(fabOpen)
                                main_fab.animate().rotationBy(45.toFloat()).setDuration(100).start()
                            } else closeFab{}
                        } else if(pagePosition == 1) {
                            startActivity(Intent(this@MainActivity, MakeRoomActivity::class.java))
                        }
                    }
                    R.id.fab_btn_1 -> {
                        closeFab {
                            startActivity(Intent(this@MainActivity, PhoneSearchActivity::class.java))
                        }
                    }
                    R.id.fab_btn_2 -> {
                        closeFab {
                            startActivity(Intent(this@MainActivity, IdSearchActivity::class.java))
                        }
                    }
                }
            }


    private fun search(text : String) {
        imm.hideSoftInputFromWindow(appbar_main_search_edit.windowToken,0)
        if(pagePosition == 0) {

        }
    }

    private fun closeSearchMode() {
        isSearchVisible = false
        appbar_main_title.visibility = View.VISIBLE
        appbar_main_search_mode.visibility = View.GONE
        imm.hideSoftInputFromWindow(appbar_main_search_edit.windowToken,0)
    }

    private fun closeFab(callback: () -> Unit) {
        isfabOn = false
        fab_btn_1.startAnimation(fabClose)
        fab_btn_2.startAnimation(fabClose)
        fab_backgroud.visibility = View.GONE
        fab_btn_1.visibility = View.GONE
        fab_btn_2.visibility = View.GONE
        fab_text_1.visibility = View.GONE
        fab_text_2.visibility = View.GONE
        main_fab.animate().rotationBy(45.toFloat()).setDuration(100).withEndAction { callback() }.start()
    }


    override fun onBackPressed() {
        if(!isfabOn && !searchMode){
            val time = Calendar.getInstance().timeInMillis
            if(time > backPressTime + 1000) {
                Toast.makeText(this,"한번 더 누르시면 종료됩니다.",Toast.LENGTH_SHORT).show()
                backPressTime = time
            } else {
                super.onBackPressed()
            }
        } else {
            if (isfabOn) closeFab{}
            if (searchMode) {
                searchMode = false
                closeSearchMode()
            }
        }
    }
}
