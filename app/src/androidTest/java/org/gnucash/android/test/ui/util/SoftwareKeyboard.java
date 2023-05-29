package org.gnucash.android.test.ui.util;

import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;

public class SoftwareKeyboard {
    public static boolean isKeyboardOpen() {
        String command = "dumpsys input_method | grep mInputShown";
        try {
            return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand(command).contains("mInputShown=true");
        } catch (IOException e) {
            Log.e(SoftwareKeyboard.class.getSimpleName(),  Log.getStackTraceString(e), e);
            throw new RuntimeException("Keyboard state cannot be read", e);

        }
    }
}
