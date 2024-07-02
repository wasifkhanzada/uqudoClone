package com.example.practice_android_4.network

import android.content.Context
import com.example.practice_android_4.R
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private lateinit var retrofit: Retrofit
    lateinit var apiService: ApiService
        private set

    fun init(context: Context) {
        val baseUrl = context.getString(R.string.base_url)

        retrofit =  Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)
    }
}


