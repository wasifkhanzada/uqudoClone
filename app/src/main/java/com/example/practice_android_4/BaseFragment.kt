package com.example.practice_android_4

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

open class BaseFragment : Fragment() {

    private lateinit var loadingContainer: View
    private lateinit var holdOnLadingContainer: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_base, container, false)
        loadingContainer = view.findViewById(R.id.loadingContainer)
        holdOnLadingContainer = view.findViewById(R.id.holdOnLoadingContainer)
        return view
    }

    protected fun showLoading() {
        loadingContainer.visibility = View.VISIBLE
    }

    protected fun hideLoading() {
        loadingContainer.visibility = View.GONE
    }

    protected fun showHoldOnLoading() {
        holdOnLadingContainer.visibility = View.VISIBLE
    }

    protected fun hideHoldOnLoading() {
        holdOnLadingContainer.visibility = View.GONE
    }
}