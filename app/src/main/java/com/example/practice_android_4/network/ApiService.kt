package com.example.practice_android_4.network

import com.example.practice_android_4.models.initializeBody
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
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

interface Auth {
    @Headers(
        "x-client-id: 668d489c556c07a4b73a8167",
        "x-client-secret: wH04SkPyGylh2Syrs8GeoerrVBtM_JeljcnmCI4njp4"
    )
    @POST("oauth/token")
    fun getToken(): Call<ResponseBody>
}

interface AppPackage {

    @POST("app-package/initialize")
    fun initialize(
        @Header("Content-Type") contentType: String = "application/json",
        @Header("Authorization") authorization: String,
        @Body request: initializeBody
    ): Call<ResponseBody>

    @Multipart
    @POST("app-package/process-id-card")
    fun processIdCard(
        @Header("Authorization") authorization: String,
        @Part front_id: MultipartBody.Part,
        @Part back_id: MultipartBody.Part,
    ): Call<ResponseBody>

    @Multipart
    @POST("app-package/register-face")
    fun registerFace(
        @Header("Authorization") authorization: String,
        @Part selfie: MultipartBody.Part,
    ): Call<ResponseBody>

    @Multipart
    @POST("app-package/compare-faces")
    fun compareFaces(
        @Header("Authorization") authorization: String,
        @Part selfie: MultipartBody.Part,
    ): Call<ResponseBody>

}
