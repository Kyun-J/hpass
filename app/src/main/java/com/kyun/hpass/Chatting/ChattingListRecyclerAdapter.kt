package com.kyun.hpass.Chatting

import android.content.Intent
import android.view.View
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.kyun.hpass.R

/**
 * Created by kyun on 2018. 3. 19..
 */
class ChattingListRecyclerAdapter : BaseQuickAdapter<ChattingRecyclerItem,BaseViewHolder>{

    var items : List<ChattingRecyclerItem>? = null

    constructor(items : List<ChattingRecyclerItem>) : super(R.layout.fragment_chatting_list_item,items) {this.items = items}

    override fun convert(helper: BaseViewHolder, item: ChattingRecyclerItem) {
        //title
        helper.setText(R.id.chat_list_item_title,item.title)
        //nomber of users
        if(item.users > 2) {
            helper.setVisible(R.id.chat_list_item_users,true)
            helper.setText(R.id.chat_list_item_users,Integer.toString(item.users))
        }
        //alarm icon
        if(item.alarm) {
            helper.setVisible(R.id.chat_list_item_no_alarm,true)
        }
        //time
        helper.setText(R.id.chat_list_item_time,item.time)
        //recent content
        helper.setText(R.id.chat_list_item_recent,item.recent)
        //stack of not read
        if(item.stack > 0) {
            helper.setText(R.id.chat_list_item_stack,Integer.toString(item.stack))
        }
    }

    override fun setOnItemClick(v: View, position: Int) {
        super.setOnItemClick(v, position)
        v.context.startActivity(Intent(v.context,ChattingActivity::class.java).putExtra("id",items!![position].id))
    }

}