package com.example.practice_android_4.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Headers
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
        @Part("doc_type") docType: RequestBody
    ): Call<ResponseBody>
}
