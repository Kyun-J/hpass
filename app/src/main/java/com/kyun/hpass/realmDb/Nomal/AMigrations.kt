package com.kyun.hpass.realmDb.Nomal

import io.realm.DynamicRealm
import io.realm.RealmMigration

/**
 * Created by kyun on 2018. 3. 13..
 */
class AMigrations : RealmMigration {

    override fun migrate(realm: DynamicRealm?, oldVersion: Long, newVersion: Long) {
        var oldv = oldVersion

        if(oldv == 0 as Long) {

            oldv++
        }


    }
}