package com.example.practice_android_4.network

import okhttp3.MediaType
import okhttp3.RequestBody

// Extension function to convert ByteArray to RequestBody
fun ByteArray.toRequestBody(contentType: MediaType?): RequestBody {
    return RequestBody.create(contentType, this)
}