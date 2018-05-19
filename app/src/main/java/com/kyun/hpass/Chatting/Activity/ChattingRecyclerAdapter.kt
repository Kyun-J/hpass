package com.kyun.hpass.Chatting.Activity

import android.widget.TextView
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.kyun.hpass.R
import com.kyun.hpass.util.objects.Singleton

class ChattingRecyclerAdapter : BaseMultiItemQuickAdapter<ChattingRecyclerItem,BaseViewHolder> {

    var width = 0

    constructor(items : ArrayList<ChattingRecyclerItem>) : super(items) {
        addItemType(ChattingRecyclerItem.Istarted,R.layout.item_chat_text_istart)
        addItemType(ChattingRecyclerItem.Icontinued,R.layout.item_chat_text_icontinue)
        addItemType(ChattingRecyclerItem.Ystarted,R.layout.item_chat_text_ystart)
        addItemType(ChattingRecyclerItem.Ycontinued,R.layout.item_chat_text_ycontinue)
        addItemType(ChattingRecyclerItem.noti,R.layout.item_chat_text_noti)
        addItemType(ChattingRecyclerItem.day,R.layout.item_chat_text_day)
    }

    override fun convert(helper: BaseViewHolder, item: ChattingRecyclerItem) {
        if(mContext != null && width == 0) width = mContext.resources.displayMetrics.widthPixels * 7 / 10
        when(helper.itemViewType) {
            ChattingRecyclerItem.Istarted -> {
                helper.getView<TextView>(R.id.chat_istart_contents).maxWidth = width
                helper.setText(R.id.chat_istart_contents, item.contents)
                helper.setText(R.id.chat_istart_left, Singleton.longToTimeString(item.time))
            }
            ChattingRecyclerItem.Icontinued -> {
                helper.getView<TextView>(R.id.chat_icontinue_contents).maxWidth = width
                helper.setText(R.id.chat_icontinue_contents,item.contents)
            }
            ChattingRecyclerItem.Ystarted -> {
                helper.getView<TextView>(R.id.chat_ystart_contents).maxWidth = width
                helper.setText(R.id.chat_ystart_contents, item.contents)
                helper.setText(R.id.chat_ytext_name, item.name)
                helper.setText(R.id.chat_ystart_right, Singleton.longToTimeString(item.time))
            }
            ChattingRecyclerItem.Ycontinued -> {
                helper.getView<TextView>(R.id.chat_ycontinue_contents).maxWidth = width
                helper.setText(R.id.chat_ycontinue_contents, item.contents)
            }
            ChattingRecyclerItem.noti -> helper.setText(R.id.chat_text_noti,item.contents)
            ChattingRecyclerItem.day -> helper.setText(R.id.chat_day,item.contents)
        }
    }



}