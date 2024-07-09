package com.example.practice_android_4

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.practice_android_4.databinding.FragmentProcessBinding
import com.example.practice_android_4.network.RetrofitInstance
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException


/**
 * An example full-screen fragment that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class ProcessFragment : Fragment() {

    private var _binding: FragmentProcessBinding? = null
    private val binding get() = _binding!!
    private val apiService by lazy { RetrofitInstance.apiService }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentProcessBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val selfie = arguments?.getByteArray("selfie")
        val frontId = arguments?.getByteArray("front_id")
        val backId = arguments?.getByteArray("back_id")
        val docType = "EID"

        processRequest(frontId, backId, selfie, docType)

    }

    private fun navigate() {
        // Check if the fragment is still attached to its context
        if (isAdded && !isDetached) {

            findNavController().navigate(R.id.action_processFragment_to_HomeFragment)

        } else {
            // Handle the case where the fragment is not attached (optional)
            Log.w(TAG, "Fragment is not attached to context. Cannot proceed with faceDetectSuccessfully.")
        }
    }

   private fun processRequest(
       frontIdByteArray: ByteArray? = null,
       backIdByteArray: ByteArray? = null,
       selfieByteArray: ByteArray? = null,
        docTypeValue: String
    ) {
        // Prepare file parts (optional)
        val frontIdPart = frontIdByteArray?.let {
            val frontIdRequestBody = it.toRequestBody("image/jpeg".toMediaTypeOrNull())
            Log.d("FRONT_ID_REQUEST_BODY", frontIdRequestBody.toString())
            MultipartBody.Part.createFormData("front_id", "front_id.jpeg", frontIdRequestBody)
        }

        val backIdPart = backIdByteArray?.let {
            val backIdRequestBody = it.toRequestBody("image/jpeg".toMediaTypeOrNull())
            Log.d("BACK_ID_REQUEST_BODY", backIdRequestBody.toString())
            MultipartBody.Part.createFormData("back_id", "back_id.jpeg", backIdRequestBody)
        }

        val selfiePart = selfieByteArray?.let {
            val selfieRequestBody = it.toRequestBody("image/jpeg".toMediaTypeOrNull())
            Log.d("SELFIE_REQUEST_BODY", selfieRequestBody.toString())
            MultipartBody.Part.createFormData("selfie", "selfie.jpeg", selfieRequestBody)
        }

        // Prepare doc_type part
        val docType = docTypeValue.toRequestBody("text/plain".toMediaTypeOrNull())

        Log.d("DOC_TYPE", docType.toString())

        // Log the parts to verify their creation
        Log.d("MULTIPART_FRONT_ID", frontIdPart.toString())
        Log.d("MULTIPART_BACK_ID", backIdPart.toString())
        Log.d("MULTIPART_SELFIE", selfiePart.toString())

        apiService.process(
            frontIdPart, backIdPart, selfiePart, docType
        ).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    response.body()?.string()?.let { jsonData ->
                        try {
                            val resObject = JSONObject(jsonData)
                            if (resObject.getBoolean("error")) {
                                val errMessage = resObject.getJSONObject("data").getString("message")
                                Log.d("RESPONSE_ERROR", "$resObject")
                                showMessage(errMessage)
                            } else {
                                Log.d("RESPONSE_SUCCESS", "$resObject")
                            }
                            navigate()
                        } catch (e: JSONException) {
                            Log.e("JSON_PARSE_ERROR", "Error parsing response JSON", e)
                        }
                    } ?: run {
                        Log.e("RESPONSE_BODY_NULL", "Response body is null")
                    }
                } else {
                    Log.e("RESPONSE_ERROR", "Response error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                // Handle failure
                navigate()
                showMessage("Upload error: ${t.message}")
                Log.e("PROCESS_ON_FAILURE", "Upload error: ${t.message}", t)
            }
        })
    }

    private fun showMessage(message: String) {
        // Implement this method to show error messages to the user
        // For example, using a Toast or a Snackbar
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }
    override fun onPause() {
        super.onPause()
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        // Clear the systemUiVisibility flag
        activity?.window?.decorView?.systemUiVisibility = 0
    }

    companion object {
        private val TAG = ProcessFragment::class.simpleName
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}