package com.kyun.hpass.util.retrofit

import com.google.gson.JsonElement
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Created by kyun on 2018. 3. 19..
 */
interface RetroService {

    @FormUrlEncoded
    @POST("kakaoLogin")
    fun kakaoLogin(@Field("access_token") token : String) : Call<JsonElement>

    @FormUrlEncoded
    @POST("simpleLogin")
    fun simpleLogin(@Field("user_token") token : String) : Call<JsonElement>

    @FormUrlEncoded
    @POST("newUser")
    fun newUser(@Field("email") email : String,
                @Field("name") name : String,
                @Field("type") type : Int,
                @Field("kakaoId") kakaoId : Long) : Call<JsonElement>

}