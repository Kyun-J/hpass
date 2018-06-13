package com.kyun.hpass.realmDb

import com.kyun.hpass.realmDb.Basic.Token
import com.kyun.hpass.realmDb.Basic.LocationMap
import io.realm.annotations.RealmModule

@RealmModule(classes = arrayOf(Token::class,LocationMap::class))
class BasicDB