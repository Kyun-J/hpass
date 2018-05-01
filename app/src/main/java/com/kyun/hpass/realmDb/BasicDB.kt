package com.kyun.hpass.realmDb

import com.kyun.hpass.realmDb.Basic.User
import io.realm.annotations.RealmModule

@RealmModule(classes = arrayOf(User::class))
class BasicDB