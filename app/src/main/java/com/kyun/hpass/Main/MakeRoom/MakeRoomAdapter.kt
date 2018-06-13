package com.kyun.hpass.Main.MakeRoom

import android.view.View
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.kyun.hpass.R

class MakeRoomAdapter : BaseQuickAdapter<MakeRoomItem,BaseViewHolder>, BaseQuickAdapter.OnItemClickListener {

    constructor(items : ArrayList<MakeRoomItem>) : super(R.layout.item_makeroom,items) {
        this.setOnItemClickListener(this)
    }


    override fun convert(helper: BaseViewHolder, item: MakeRoomItem) {
        helper.setChecked(R.id.makeroom_item_check,item.checked)
        helper.setText(R.id.makeroom_item_name,item.name)
    }

    override fun onItemClick(adapter: BaseQuickAdapter<*, *>?, view: View?, position: Int) {
        this.data[position].checked = !this.data[position].checked
        this.notifyItemChanged(position)
    }
}