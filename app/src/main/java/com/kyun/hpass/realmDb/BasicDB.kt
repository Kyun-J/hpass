package com.kyun.hpass.realmDb

import com.kyun.hpass.realmDb.Basic.Token
import io.realm.annotations.RealmModule

@RealmModule(classes = arrayOf(Token::class))
class BasicDB