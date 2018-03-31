package com.kyun.hpass.Main

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.view.ViewPager
import com.kyun.hpass.R
import com.kyun.hpass.Service.HService
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        main_pager.adapter = MainPagerAdapter(supportFragmentManager)
        main_pager.offscreenPageLimit = 3
        main_tablayout.setupWithViewPager(main_pager)
        main_pager.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(main_tablayout))
    }
}
