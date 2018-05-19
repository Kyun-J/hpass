package com.kyun.hpass.util.objects

object FriendEvent {

    private val EventList = ArrayList<()->Unit>()

    fun addList(item : () -> Unit) {
        EventList.add(item)
    }

    fun removeList(item : () -> Unit) {
        EventList.remove(item)
    }

    fun Event() {
        for(e in EventList) e()
    }
}