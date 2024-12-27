package org.gnucash.android.ui.util.dialog

import android.os.Bundle
import androidx.fragment.app.DialogFragment

abstract class VolatileDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Disable re-creation because the members have been erased.
        if (savedInstanceState != null) dismiss()
    }
}