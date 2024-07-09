package com.example.practice_android_4.network

import android.content.Context
import com.example.practice_android_4.R
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {
    private lateinit var retrofit: Retrofit
    lateinit var apiService: ApiService
        private set

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun init(context: Context) {
        val baseUrl = context.getString(R.string.base_url)
        val client = OkHttpClient.Builder().build()

        retrofit =  Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }
}


