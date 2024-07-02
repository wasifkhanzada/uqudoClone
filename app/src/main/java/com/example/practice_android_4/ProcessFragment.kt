package com.example.practice_android_4

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.practice_android_4.databinding.FragmentProcessBinding
import com.example.practice_android_4.network.RetrofitInstance
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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

            Log.d("SELFIE", "${selfie}")
            Log.d("FRONT_ID", "${frontId}")
            Log.d("BACK_ID", "${backId}")
        processRequest(frontId, backId, selfie)

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

    fun processRequest(frontId: ByteArray?, backId: ByteArray?, selfie: ByteArray?) {

        val frontRequestFile = frontId?.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val frontId = frontRequestFile?.let { MultipartBody.Part.createFormData("front_id", "front_id.jpeg", it) }

        val backRequestFile = backId?.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val backId = backRequestFile?.let { MultipartBody.Part.createFormData("back_id", "back_id.jpeg", it) }

        val selfieRequestFile = selfie?.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val selfie = selfieRequestFile?.let { MultipartBody.Part.createFormData("selfie", "selfie.jpeg", it) }

        apiService.process(frontId, backId, selfie).enqueue(object : Callback<Any> {
            override fun onResponse(call: Call<Any>, response: Response<Any>) {
                if (response.isSuccessful) {
                    // Handle successful response
                    navigate()
                    Log.d("PROCESS_ON_RESPONSE", "Upload successful: ${response.body()}")

                } else {
                    // Handle non-successful response
                    navigate()
                    Log.d("PROCESS_ON_RESPONSE", "Upload failed: ${response.errorBody()?.string()}")
                }
            }
            override fun onFailure(call: Call<Any>, t: Throwable) {
                // Handle failure
                navigate()
                Log.e("PROCESS_ON_FAILURE", "Upload error: ${t.message}", t)
            }
        })
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

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private val TAG = ProcessFragment::class.simpleName
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}