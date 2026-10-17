package com.clouddrive.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Decides how the app lock can authenticate right now.
 *
 * Strong biometrics are preferred, but they can disappear (fingerprints removed, sensor error,
 * too many attempts). The lock must never open the app just because of that, so the device
 * PIN/pattern/password is accepted as well:
 * - Android 11+: one prompt accepting biometrics or the device credential.
 * - Android 8-10 do not support that combination: strong biometrics with a "cancel" button, or,
 *   when they are not usable, the device credential alone.
 */
object AppLockAuth {

    sealed interface Plan {
        /** Show a prompt with [authenticators]; [needsNegativeButton] is only valid without DEVICE_CREDENTIAL. */
        data class Use(val authenticators: Int, val needsNegativeButton: Boolean) : Plan

        /** No biometrics and no screen lock on the device: there is nothing to authenticate with. */
        object NoScreenLock : Plan

        /** Authentication exists but cannot be used at the moment (sensor error, update pending...). */
        object Unavailable : Plan
    }

    /**
     * [strong], [credential] and [combined] are `BiometricManager.canAuthenticate` results for
     * BIOMETRIC_STRONG, DEVICE_CREDENTIAL and BIOMETRIC_STRONG or DEVICE_CREDENTIAL.
     */
    fun plan(sdkInt: Int, strong: Int, credential: Int, combined: Int): Plan {
        val ok = BiometricManager.BIOMETRIC_SUCCESS
        if (sdkInt >= Build.VERSION_CODES.R) {
            if (combined == ok) return Plan.Use(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, needsNegativeButton = false)
        } else {
            if (strong == ok) return Plan.Use(BIOMETRIC_STRONG, needsNegativeButton = true)
            if (credential == ok) return Plan.Use(DEVICE_CREDENTIAL, needsNegativeButton = false)
        }
        val nothingSet = setOf(
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
        )
        val noScreenLock = if (sdkInt >= Build.VERSION_CODES.R) {
            combined in nothingSet
        } else {
            strong in nothingSet && credential in nothingSet
        }
        return if (noScreenLock) Plan.NoScreenLock else Plan.Unavailable
    }

    fun plan(context: Context): Plan {
        val manager = BiometricManager.from(context)
        return plan(
            sdkInt = Build.VERSION.SDK_INT,
            strong = manager.canAuthenticate(BIOMETRIC_STRONG),
            credential = manager.canAuthenticate(DEVICE_CREDENTIAL),
            combined = manager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL),
        )
    }
}
