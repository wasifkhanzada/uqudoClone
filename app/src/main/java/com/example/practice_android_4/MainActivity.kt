package com.example.practice_android_4

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.example.practice_android_4.databinding.ActivityMainBinding
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AlertDialog
import com.example.practice_android_4.network.RetrofitInstance

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var permissionDialog: AlertDialog? = null

    // Registering the permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
            permissionDialog?.dismiss()
            onCameraPermissionGranted()
        } else {
            // Permission denied, handle it accordingly
            handlePermissionDenied()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)

        checkAndRequestCameraPermission()

        // Enable full-screen immersive mode
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )

        RetrofitInstance.init(this)

    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Ensure full-screen immersive mode is maintained
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
        }
    }

    override fun onResume() {
        super.onResume()
        // Check permission status when returning to the app
//        checkAndRequestCameraPermission()
    }

    private fun checkAndRequestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted
                onCameraPermissionGranted()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // Show rationale and request permission again
                showPermissionRationaleDialog()
            }
            else -> {
                // Request the camera permission for the first time
                requestCameraPermission()
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        // Ensure no previous dialog is active
        if (permissionDialog?.isShowing == true) {
            permissionDialog?.dismiss()
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Camera Permission Required")
        builder.setMessage("This app needs access to your camera to take pictures. Please grant the permission.")
        builder.setPositiveButton("Grant Permission") { _, _ ->
            requestCameraPermission()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
            permissionDialog = null // Clear the dialog reference
        }

        permissionDialog = builder.create()
        permissionDialog?.setCancelable(false)
        permissionDialog?.setCanceledOnTouchOutside(false)
        permissionDialog?.show()
    }

    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun handlePermissionDenied() {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            // The user has selected "Don't ask again", show a dialog directing to settings
            showSettingsDialog()
        } else {
            // The user denied the permission but didn't select "Don't ask again", show the rationale dialog
            showPermissionRationaleDialog()
        }
    }

    private fun showSettingsDialog() {
        // Ensure no previous dialog is active
        if (permissionDialog?.isShowing == true) {
            permissionDialog?.dismiss()
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Camera Permission Required")
        builder.setMessage("This app needs camera access to function properly. Please grant the permission in settings.")
        builder.setPositiveButton("Go to Settings") { _, _ ->
            openAppSettings()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
            permissionDialog = null // Clear the dialog reference
        }

        permissionDialog = builder.create()
        permissionDialog?.setCancelable(false)
        permissionDialog?.setCanceledOnTouchOutside(false)
        permissionDialog?.show()
    }

    private fun openAppSettings() {
        // Open the app's settings screen
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri: Uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun onCameraPermissionGranted() {
        // This method is called when the camera permission is granted
//        Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
        // Implement your camera-related functionality here
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

}