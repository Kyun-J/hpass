package com.kyun.hpass.Main

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import com.kyun.hpass.Chatting.ChattingListFragment
import com.kyun.hpass.FriendList.FriendListFragment
import com.kyun.hpass.Setting.SettingFragment

/**
 * Created by kyun on 2018. 3. 13..
 */
class MainPagerAdapter(fm : FragmentManager) : FragmentPagerAdapter(fm) {

    override fun getItem(position: Int): Fragment {
        when(position) {
            0 -> return FriendListFragment()
            1 -> return ChattingListFragment()
            2 -> return SettingFragment()
            else -> return Fragment()
        }
    }

    override fun getPageTitle(position: Int): CharSequence {
        when(position) {
            0 -> return "지인"
            1 -> return "대화"
            2 -> return "설정"
            else -> return ""
        }
    }

    override fun getCount(): Int {
        return 3
    }
}