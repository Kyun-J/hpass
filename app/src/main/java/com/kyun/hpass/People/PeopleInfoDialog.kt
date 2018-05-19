package com.kyun.hpass.People

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AlertDialog
import com.kyun.hpass.R

class PeopleInfoDialog(val mContext: Context) : AlertDialog(mContext) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_people_info)
        
    }
}