package com.kyun.hpass.util.retrofit

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.ResponseBody
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
    @POST("googleLogin")
    fun googleLogin(@Field("access_token") token : String) : Call<JsonElement>

    @FormUrlEncoded
    @POST("simpleLogin")
    fun simpleLogin(@Field("user_token") token : String) : Call<JsonElement>

    @FormUrlEncoded
    @POST("newUser")
    fun newUser(@Field("id") email : String,
                @Field("name") name : String,
                @Field("phone") phone : String) : Call<JsonElement>

    @FormUrlEncoded
    @POST("findFriends")
    fun findFriends(@Field("user_token") token: String) : Call<JsonElement>

    @FormUrlEncoded
    @POST("addFriend")
    fun addFriend(@Field("user_token") token: String,
                  @Field("friend_id") fid : String,
                  @Field("phone") phone: String) : Call<ResponseBody>

    @FormUrlEncoded
    @POST("addFrinedsByPhone")
    fun addFriendsByPH(@Field("data") data : JsonObject) : Call<JsonElement>

    @FormUrlEncoded
    @POST("searchFriendByPH")
    fun searchFriendByPH(@Field("user_token") token: String,
                         @Field("phone") phone: String) : Call<JsonElement>

    @FormUrlEncoded
    @POST("addFriendsById")
    fun addFriendsById(@Field("data") data : JsonObject) : Call<JsonElement>

    @FormUrlEncoded
    @POST("blockFriend")
    fun blockFriend(@Field("user_token") token : String,
                    @Field("friend_id") friendId : String) : Call<ResponseBody>

}