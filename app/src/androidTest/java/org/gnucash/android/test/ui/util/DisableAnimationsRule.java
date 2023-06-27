package org.gnucash.android.test.ui.util;

import android.app.UiAutomation;
import android.net.TrafficStats;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.gnucash.android.test.ui.TransactionsActivityTest;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Ngewi on 19.04.2016.
 * Credit: <a href="https://product.reverb.com/2015/06/06/disabling-animations-in-espresso-for-android-testing/">reverb.com</a>
 */
public class DisableAnimationsRule implements TestRule {
    private void setAnimationState(AnimationState state) throws IOException {
        List<String> commands = List.of(
                "settings get global animator_duration_scale",
                "settings put global animator_duration_scale " + state.getStatusCode(),
                "settings put global transition_animation_scale " + state.getStatusCode(),
                "settings put global window_animation_scale " + state.getStatusCode()
        );

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uiAutomation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS");
        }

        for (String command : commands) {
            try (
                    ParcelFileDescriptor fd = uiAutomation.executeShellCommand(command);
                    InputStream is = new BufferedInputStream(new FileInputStream(fd.getFileDescriptor()));
                    fd;
                    is
            ) {
                Log.d(DisableAnimationsRule.class.getSimpleName(), new String(is.readAllBytes()));
            }
        }
    }

    @Override
    public Statement apply(final Statement statement, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                setAnimationState(AnimationState.DISABLED);
                try {
                    statement.evaluate();
                } finally {
                    setAnimationState(AnimationState.DEFAULT);
                }
            }
        };
    }

    private enum AnimationState {
        DISABLED(AnimationState.ANIMATIONS_DISABLED),
        DEFAULT(AnimationState.ANIMATIONS_DEFAULT);

        private final float statusCode;

        AnimationState(float statusCode) {
            this.statusCode = statusCode;
        }

        public float getStatusCode() {
            return statusCode;
        }

        private static final float ANIMATIONS_DISABLED = 0.0f;
        private static final float ANIMATIONS_DEFAULT = 1.0f;
    }
}