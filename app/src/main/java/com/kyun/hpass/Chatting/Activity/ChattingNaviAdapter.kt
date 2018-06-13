package com.kyun.hpass.Chatting.Activity

import android.util.Log
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.kyun.hpass.R

class ChattingNaviAdapter : BaseQuickAdapter<ChattingNaviItem,BaseViewHolder> {

    constructor(items : ArrayList<ChattingNaviItem>) : super(R.layout.item_chat_navi,items)

    override fun convert(helper: BaseViewHolder, item: ChattingNaviItem) {
        helper.setText(R.id.item_chat_navi_txt,item.name)
    }

}