package com.kyun.hpass.Chatting

import android.content.Intent
import android.view.View
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.kyun.hpass.Chatting.Activity.ChattingActivity
import com.kyun.hpass.R
import com.kyun.hpass.util.objects.Singleton

/**
 * Created by kyun on 2018. 3. 19..
 */
class ChattingListRecyclerAdapter : BaseQuickAdapter<ChattingListRecyclerItem,BaseViewHolder>, BaseQuickAdapter.OnItemClickListener{


    constructor(items : MutableList<ChattingListRecyclerItem>) : super(R.layout.item_chatting_list,items) {
        this.setOnItemClickListener(this)
    }

    override fun convert(helper: BaseViewHolder, item: ChattingListRecyclerItem) {
        //title
        helper.setText(R.id.chat_list_item_title,item.title)
        //nomber of users
        if(item.users > 1) {
            helper.setVisible(R.id.chat_list_item_users,true)
            helper.setText(R.id.chat_list_item_users,Integer.toString(item.users))
        }
        //alarm icon
        if(item.alarm) {
            helper.setVisible(R.id.chat_list_item_no_alarm,true)
        }
        //time
        helper.setText(R.id.chat_list_item_time,Singleton.longToDateString(item.time))
        //recent content
        helper.setText(R.id.chat_list_item_recent,item.recent)
        //stack of not read
        if(item.stack > 0) {
            if(item.stack <= 300) helper.setText(R.id.chat_list_item_stack,Integer.toString(item.stack))
            else helper.setText(R.id.chat_list_item_stack,"300+")
        } else
            helper.setText(R.id.chat_list_item_stack,"")
    }

    override fun onItemClick(adapter: BaseQuickAdapter<*, *>?, v: View, position: Int) {
        v.context.startActivity(Intent(v.context, ChattingActivity::class.java).putExtra("id",this.data[position].id).putExtra("count",this.data[position].stack))
    }
}