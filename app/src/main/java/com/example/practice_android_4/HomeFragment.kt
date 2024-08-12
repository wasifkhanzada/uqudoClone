package com.example.practice_android_4

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import com.example.practice_android_4.network.RetrofitInstance
import com.example.practice_android_4.common.CommonHelper
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.practice_android_4.models.initializeBody
import androidx.navigation.fragment.findNavController

class HomeFragment : BaseFragment() {

    private lateinit var commonHelper: CommonHelper

    private val auth by lazy { RetrofitInstance.auth }
    private val appPackage by lazy { RetrofitInstance.appPackage }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupOtherServices()

        // Find the button by its ID and set a click listener
        val exampleButton: Button = view.findViewById(R.id.start_verification)
        exampleButton.setOnClickListener {
            // Handle button click to navigate to another fragment or activity
            // Example navigation to FaceDetectionFragment using Navigation Component
            getToken()
//            findNavController().navigate(R.id.action_HomeFragment_to_frontEidDetection)
//            findNavController().navigate(R.id.action_HomeFragment_to_FaceDetectionFragment)

            // Or, if you want to start an activity
            // val intent = Intent(requireContext(), FaceDetectionActivity::class.java)
            // startActivity(intent)
        }
    }

    private fun setupOtherServices() {
        // Initialize Helper service
        commonHelper = CommonHelper(requireContext())
    }

    private fun getToken() {
        auth.getToken().enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    response.body()?.string()?.let { jsonData ->
                        try {
                            val resObject = JSONObject(jsonData)
                            Log.d("resObject", "$resObject")

                            if (resObject.getBoolean("error")) {
                                val errMessage = resObject.getJSONObject("data").getString("message")
                                Log.d("RESPONSE_ERROR", "$resObject")
                                commonHelper.showMessage(errMessage)
                            } else {
                                val token = resObject.getJSONObject("data").getString("access_toke")
                                initialize(token)
                                Log.d("getToken RESPONSE_SUCCESS", "$resObject $token")
                            }
//                            navigate()
                        } catch (e: JSONException) {
                            Log.e("JSON_PARSE_ERROR", "Error parsing response JSON", e)
                        }
                    } ?: run {
                        Log.e("RESPONSE_BODY_NULL", "Response body is null")
                    }
                } else {
                    Log.e("RESPONSE_ERROR", "Response error: $response ${response.code()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                // Handle failure
//                navigate()
                commonHelper.showMessage("Upload error: ${t.message}")
                Log.e("PROCESS_ON_FAILURE", "Upload error: ${t.message}", t)
            }
        })
    }

    private fun initialize(initToken: String) {

        val request = initializeBody(
            scope = commonHelper.DOCUMENT_OR_FACE_TYPE,
            doc_type = "EMIRATES_ID",
            type = commonHelper.DETECTION
        )

        val call = appPackage.initialize(
            authorization = "Bearer $initToken",
            request = request
        )

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    response.body()?.string()?.let { jsonData ->
                        try {
                            val resObject = JSONObject(jsonData)
                            Log.d("resObject", "$resObject")

                            if (resObject.getBoolean("error")) {
                                val errMessage = resObject.getJSONObject("data").getString("message")
                                Log.d("RESPONSE_ERROR", "$resObject")
                                commonHelper.showMessage(errMessage)
                            } else {
                                val token = resObject.getJSONObject("data").getString("access_token")
                                val bundle = Bundle().apply {
                                    putString("token", token)
                                    putString("doc_type", request.doc_type)
                                    putString("scope", request.scope)
                                    putString("type", request.type)
                                }
                                navigate(bundle)
                                Log.d("initialize  RESPONSE_SUCCESS", "$resObject $token")
                            }
                        } catch (e: JSONException) {
                            Log.e("JSON_PARSE_ERROR", "Error parsing response JSON", e)
                        }
                    } ?: run {
                        Log.e("RESPONSE_BODY_NULL", "Response body is null")
                    }
                } else {
                    Log.e("RESPONSE_ERROR", "Response error: $response ${response.code()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                // Handle failure
//                navigate()
                commonHelper.showMessage("Upload error: ${t.message}")
                Log.e("PROCESS_ON_FAILURE", "Upload error: ${t.message}", t)
            }
        })
    }

    private fun navigate(bundle: Bundle) {
        // Check if the fragment is still attached to its context
        if (isAdded && !isDetached) {

            Log.d("navigate bundle", "$bundle")

            if(bundle.getString("scope") === commonHelper.DOCUMENT_OR_FACE_TYPE || bundle.getString("scope") === commonHelper.DOCUMENT_TYPE){
                findNavController().navigate(R.id.action_HomeFragment_to_eidDetection, bundle)
            } else if(bundle.getString("scope") === commonHelper.FACE_MATCH_TYPE) {
                findNavController().navigate(R.id.action_HomeFragment_to_FaceDetectionFragment, bundle)
            }
            // Use the bundle as needed (e.g., pass to a fragment)

        } else {
            // Handle the case where the fragment is not attached (optional)
            Log.w(TAG, "Fragment is not attached to context. Cannot proceed with frontEidDetectSuccessfully.")
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }

    companion object {
        private val TAG = HomeFragment::class.simpleName
    }
}
