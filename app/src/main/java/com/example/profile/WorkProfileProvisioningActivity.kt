package com.example.profile

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Implements the Android 12+ managed-profile provisioning handshake.
 * The OS still owns and displays all consent/setup screens.
 */
class WorkProfileProvisioningActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            DevicePolicyManager.ACTION_GET_PROVISIONING_MODE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(
                            DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                            DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE
                        )
                    )
                } else {
                    setResult(RESULT_CANCELED)
                }
                finish()
            }

            DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE -> {
                // The profile is configured by WorkProfileAdminReceiver after provisioning.
                setResult(RESULT_OK)
                finish()
            }

            else -> {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }
}
