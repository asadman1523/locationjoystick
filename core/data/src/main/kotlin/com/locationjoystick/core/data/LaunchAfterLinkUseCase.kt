package com.locationjoystick.core.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LaunchAfterLink"

/** Opens the app the user picked in Settings once a map link was handled. No-op when none is set. */
@Singleton
class LaunchAfterLinkUseCase
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val captureRepository: CaptureCoordinatesRepository,
    ) {
        suspend fun launch() {
            val pkg = captureRepository.launchAfterLinkPackage.first()
            if (pkg == null || pkg == context.packageName) return
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent == null) {
                Log.w(TAG, "$pkg is not launchable")
                return
            }
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Cannot launch $pkg", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Not allowed to launch $pkg", e)
            }
        }
    }
