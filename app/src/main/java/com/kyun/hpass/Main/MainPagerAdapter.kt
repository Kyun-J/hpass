package com.kyun.hpass.Main

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import com.kyun.hpass.Chatting.ChattingListFragment
import com.kyun.hpass.test

/**
 * Created by kyun on 2018. 3. 13..
 */
class MainPagerAdapter(fm : FragmentManager) : FragmentPagerAdapter(fm) {

    override fun getItem(position: Int): Fragment {
        when(position) {
            0 -> return ChattingListFragment()
            1 -> return test()
            else -> return test()
        }
    }

    override fun getPageTitle(position: Int): CharSequence {
        when(position) {
            0 -> return "대화"
            1 -> return "테스트"
            else -> return ""
        }
    }

    override fun getCount(): Int {
        return 2
    }
}