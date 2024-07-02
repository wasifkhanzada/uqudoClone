package com.example.practice_android_4

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply insets listener
//        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.header)) { view, insets ->
//            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            // Check if padding needs to be updated
//            if (systemBarsInsets.top != 0) {
//                // Apply top padding to accommodate the status bar
//                view.setPadding(
//                    view.paddingLeft,
////                    systemBarsInsets.top + (systemBarsInsets.top / 2),
//                    systemBarsInsets.top,
//                    view.paddingRight,
//                    systemBarsInsets.bottom
//                )
//            }
//            // Preserve the insets without consuming them
//            insets
//        }

        // Find the button by its ID and set a click listener
        val exampleButton: Button = view.findViewById(R.id.start_verification)
        exampleButton.setOnClickListener {
            // Handle button click to navigate to another fragment or activity
            // Example navigation to FaceDetectionFragment using Navigation Component

            findNavController().navigate(R.id.action_HomeFragment_to_frontEidDetection)
//            findNavController().navigate(R.id.action_HomeFragment_to_FaceDetectionFragment)

            // Or, if you want to start an activity
            // val intent = Intent(requireContext(), FaceDetectionActivity::class.java)
            // startActivity(intent)
        }
    }


    override fun onResume() {
        super.onResume()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }
}
