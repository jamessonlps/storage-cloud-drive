package com.clouddrive.util

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.clouddrive.util.AppLockAuth.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppLockAuthTest {

    private val ok = BiometricManager.BIOMETRIC_SUCCESS
    private val none = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    private val noHw = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
    private val hwBusy = BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
    private val r = Build.VERSION_CODES.R
    private val p = Build.VERSION_CODES.P

    @Test
    fun `android 11 accepts biometrics or the device credential in one prompt`() {
        assertEquals(
            Plan.Use(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, needsNegativeButton = false),
            AppLockAuth.plan(r, strong = ok, credential = ok, combined = ok),
        )
    }

    @Test
    fun `android 11 falls back to the credential when strong biometrics are unusable`() {
        assertEquals(
            Plan.Use(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, needsNegativeButton = false),
            AppLockAuth.plan(r, strong = hwBusy, credential = ok, combined = ok),
        )
    }

    @Test
    fun `older android uses strong biometrics with a cancel button`() {
        assertEquals(
            Plan.Use(BIOMETRIC_STRONG, needsNegativeButton = true),
            AppLockAuth.plan(p, strong = ok, credential = ok, combined = noHw),
        )
    }

    @Test
    fun `older android falls back to the credential alone instead of opening the app`() {
        assertEquals(
            Plan.Use(DEVICE_CREDENTIAL, needsNegativeButton = false),
            AppLockAuth.plan(p, strong = none, credential = ok, combined = noHw),
        )
        assertEquals(
            Plan.Use(DEVICE_CREDENTIAL, needsNegativeButton = false),
            AppLockAuth.plan(p, strong = hwBusy, credential = ok, combined = noHw),
        )
    }

    @Test
    fun `device without any screen lock is reported as such`() {
        assertSame(Plan.NoScreenLock, AppLockAuth.plan(r, strong = noHw, credential = none, combined = none))
        assertSame(Plan.NoScreenLock, AppLockAuth.plan(p, strong = none, credential = none, combined = noHw))
    }

    @Test
    fun `temporary failures never count as no lock`() {
        assertSame(Plan.Unavailable, AppLockAuth.plan(r, strong = hwBusy, credential = hwBusy, combined = hwBusy))
        assertSame(Plan.Unavailable, AppLockAuth.plan(p, strong = hwBusy, credential = none, combined = noHw))
    }
}
