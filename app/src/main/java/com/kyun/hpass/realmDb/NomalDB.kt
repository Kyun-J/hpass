package com.kyun.hpass.realmDb

import com.kyun.hpass.realmDb.Nomal.*
import io.realm.annotations.RealmModule

@RealmModule(classes = arrayOf(ChatList::class,ChatMember::class, ChatRoom::class, MyInfo::class, Peoples::class))
class NomalDB