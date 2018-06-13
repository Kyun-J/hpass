package com.kyun.hpass.FriendList

import android.util.Log
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.kyun.hpass.People.PeopleInfoDialog
import com.kyun.hpass.R

class FriendListAdapter : BaseMultiItemQuickAdapter<FriendListItem,BaseViewHolder> {

    constructor(items : ArrayList<FriendListItem>) :super(items) {
        addItemType(FriendListItem.friends, R.layout.item_friend_list)
        addItemType(FriendListItem.item, R.layout.item_friend_item)
        this.setOnItemClickListener { adapter, view, position ->
            val item = getItem(position)
            if(item!!.itemtype == FriendListItem.friends) {
                PeopleInfoDialog(mContext).setData(item.id,item.name).show()
            }
        }
    }

    override fun convert(helper: BaseViewHolder, item: FriendListItem) {
        when(item.itemtype) {
            FriendListItem.friends -> {
                helper.setText(R.id.item_friend,item.name)
            }
            FriendListItem.item -> {
                helper.setText(R.id.item_friend_item,item.name)
            }
        }
    }
}