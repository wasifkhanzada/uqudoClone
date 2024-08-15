package com.example.practice_android_4.common

import android.content.Context
import android.widget.Toast

class CommonHelper(private val context: Context) {

    val DOCUMENT_TYPE = "document-scan"
    val DOCUMENT_OR_FACE_TYPE = "document-face-scan"
    val FACE_MATCH_TYPE = "face-match"
    val DETECTION = "DETECTION"
    val REGISTRATION = "REGISTRATION"


    fun showMessage(message: String) {
        // Implement this method to show error messages to the user
        // For example, using a Toast or a Snackbar
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

}