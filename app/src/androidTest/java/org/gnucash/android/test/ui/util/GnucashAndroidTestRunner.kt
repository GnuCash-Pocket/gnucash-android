/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.test.ui.util

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.test.runner.AndroidJUnitRunner
import timber.log.Timber
import java.lang.reflect.InvocationTargetException

/**
 * Custom test runner
 */
@Suppress("unused")
class GnucashAndroidTestRunner : AndroidJUnitRunner() {
    override fun onCreate(args: Bundle) {
        super.onCreate(args)
        // as time goes on we may actually need to process our arguments.
        disableAnimation()
    }

    override fun onDestroy() {
        enableAnimation()
        super.onDestroy()
    }

    private fun disableAnimation() {
        val permStatus = context.checkCallingOrSelfPermission(ANIMATION_PERMISSION)
        if (permStatus == PackageManager.PERMISSION_GRANTED) {
            if (reflectivelyDisableAnimation(DISABLED)) {
                Timber.i("All animations disabled.")
            } else {
                Timber.i("Could not disable animations.")
            }
        } else {
            Timber.i("Cannot disable animations due to lack of permission.")
        }
    }

    private fun enableAnimation() {
        val permStatus = context.checkCallingOrSelfPermission(ANIMATION_PERMISSION)
        if (permStatus == PackageManager.PERMISSION_GRANTED) {
            if (reflectivelyDisableAnimation(DEFAULT)) {
                Timber.i("All animations enabled.")
            } else {
                Timber.i("Could not enable animations.")
            }
        } else {
            Timber.i("Cannot disable animations due to lack of permission.")
        }
    }

    private fun reflectivelyDisableAnimation(animationScale: Float): Boolean {
        try {
            val windowManagerStubClazz = Class.forName("android.view.IWindowManager\$Stub")
            val asInterface = windowManagerStubClazz.getDeclaredMethod(
                "asInterface",
                IBinder::class.java
            )
            val serviceManagerClazz = Class.forName("android.os.ServiceManager")
            val getService = serviceManagerClazz.getDeclaredMethod(
                "getService",
                String::class.java
            )
            val windowManagerClazz = Class.forName("android.view.IWindowManager")
            val setAnimationScales = windowManagerClazz.getDeclaredMethod(
                "setAnimationScales",
                FloatArray::class.java
            )
            val getAnimationScales = windowManagerClazz.getDeclaredMethod("getAnimationScales")

            val windowManagerBinder = getService.invoke(null, "window") as IBinder
            val windowManagerObj = asInterface.invoke(null, windowManagerBinder)
            val currentScales = getAnimationScales.invoke(windowManagerObj) as Array<Float>
            for (i in currentScales.indices) {
                currentScales[i] = animationScale
            }
            setAnimationScales.invoke(windowManagerObj, *currentScales)
            return true
        } catch (e: NoSuchMethodException) {
            Timber.w(e, "Cannot disable animations reflectively.")
        } catch (e: InvocationTargetException) {
            Timber.w(e, "Cannot disable animations reflectively.")
        } catch (e: IllegalAccessException) {
            Timber.w(e, "Cannot disable animations reflectively.")
        } catch (e: ClassNotFoundException) {
            Timber.w(e, "Cannot disable animations reflectively.")
        } catch (e: RuntimeException) {
            Timber.w(e, "Cannot disable animations reflectively.")
        }
        return false
    }

    companion object {
        private const val ANIMATION_PERMISSION = "android.permission.SET_ANIMATION_SCALE"
        private const val DISABLED = 0.0f
        private const val DEFAULT = 1.0f
    }
}
