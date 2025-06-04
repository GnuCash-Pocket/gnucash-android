package org.gnucash.android.test.ui.util

import android.Manifest
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.FileInputStream

/**
 * Created by Ngewi on 19.04.2016.
 * Credit: [reverb.com](https://product.reverb.com/2015/06/06/disabling-animations-in-espresso-for-android-testing/)
 */
class DisableAnimationsRule : TestRule {
    private fun setAnimationState(state: AnimationState) {
        val commands = listOf(
            "settings get global animator_duration_scale",
            "settings put global animator_duration_scale " + state.statusCode,
            "settings put global transition_animation_scale " + state.statusCode,
            "settings put global window_animation_scale " + state.statusCode
        )

        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uiAutomation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
        }

        for (command in commands) {
            uiAutomation.executeShellCommand(command).use { fd ->
                BufferedInputStream(FileInputStream(fd.fileDescriptor)).use { input ->
                    fd.use {
                        input.use {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // https://stackoverflow.com/a/75837274/2249464
                                val commandOutput = String(input.readAllBytes())
                                Timber.d(commandOutput)
                            } else {
                                val commandOutput = StringBuilder()
                                var i: Int
                                while ((input.read().also { i = it }) != -1) {
                                    commandOutput.append(i)
                                }
                                Timber.d(commandOutput.toString())
                            }
                        }
                    }
                }
            }
        }
    }

    override fun apply(statement: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                setAnimationState(AnimationState.DISABLED)
                try {
                    statement.evaluate()
                } finally {
                    setAnimationState(AnimationState.DEFAULT)
                }
            }
        }
    }

    private enum class AnimationState(val statusCode: Float) {
        DISABLED(0.0f),
        DEFAULT(1.0f);
    }
}