package com.example.practice_android_4.network

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("test-module/testing")
    fun process(
        @Part front_id: MultipartBody.Part?,
        @Part back_id: MultipartBody.Part?,
        @Part selfie: MultipartBody.Part?,
    ): Call<Any>
}
