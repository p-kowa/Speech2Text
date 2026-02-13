package com.example.speech2text

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionManager(
    private val registry: ActivityResultRegistry,
    private val context: Context
) {
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    // Two "parking spots" for our callbacks
    private var onGrantedCallback: (() -> Unit)? = null
    private var onDeniedCallback: (() -> Unit)? = null

    fun register(ownerKey: String) {
        requestPermissionLauncher = registry.register(
            ownerKey,
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                onGrantedCallback?.invoke()
            } else {
                // NOW the tool knows what to do in case of error!
                onDeniedCallback?.invoke()
            }
        }
    }

    fun checkAudioPermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit // Newly added
    ) {
        this.onGrantedCallback = onGranted
        this.onDeniedCallback = onDenied

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}