package com.example.profile

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast

class WorkProfileAdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)

        val admin = ComponentName(context, WorkProfileAdminReceiver::class.java)
        val policyManager = context.getSystemService(DevicePolicyManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { policyManager.setProfileName(admin, "Live Media Assistant Space") }
                .onFailure { Log.w(TAG, "Could not set managed profile name", it) }
        }

        val targetPackage = intent
            .getBundleExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
            ?.getString(PENDING_PACKAGE)
            ?.takeIf { it.isNotBlank() }

        if (targetPackage == null) {
            Log.i(TAG, "Profile provisioning completed without a target package")
            Toast.makeText(context, "Work profile created. No app was selected to install.", Toast.LENGTH_LONG).show()
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.w(TAG, "Installing an existing package into a profile requires Android 9 or newer")
            Toast.makeText(context, "Work profile created, but automatic app install needs Android 9 or newer.", Toast.LENGTH_LONG).show()
            return
        }

        runCatching { policyManager.installExistingPackage(admin, targetPackage) }
            .onSuccess { installed ->
                if (installed) {
                    Log.i(TAG, "Requested app installed in work profile: $targetPackage")
                    Toast.makeText(context, "Work profile ready. App added: $targetPackage", Toast.LENGTH_LONG).show()
                } else {
                    Log.w(TAG, "Android did not install the existing package in work profile: $targetPackage")
                    Toast.makeText(context, "Work profile created, but Android could not add $targetPackage. Install it in the work profile manually.", Toast.LENGTH_LONG).show()
                }
            }
            .onFailure { error ->
                Log.e(TAG, "Could not install existing package in work profile: $targetPackage", error)
                Toast.makeText(context, "Work profile created, but app install failed: ${error.localizedMessage ?: error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
    }

    companion object {
        private const val TAG = "WorkProfileAdmin"
        const val PREFERENCES = "work_profile"
        const val PENDING_PACKAGE = "pending_package"
    }
}
