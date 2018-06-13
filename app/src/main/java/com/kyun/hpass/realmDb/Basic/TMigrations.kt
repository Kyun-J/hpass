package com.kyun.hpass.realmDb.Basic

import io.realm.DynamicRealm
import io.realm.FieldAttribute
import io.realm.RealmMigration

class TMigrations : RealmMigration {
    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        var ov = oldVersion

        val schema = realm.schema
        if(ov == 0.toLong()) {
            schema.create("LocationMap")
                    .addField("UserId",String::class.java,FieldAttribute.REQUIRED)
                    .addField("latitude",Double::class.java,FieldAttribute.REQUIRED)
                    .addField("longtitude",Double::class.java,FieldAttribute.REQUIRED)
                    .addField("time",Long::class.java,FieldAttribute.REQUIRED)
            ov++
        }
        if(ov == 1.toLong()) {

        }
    }
}