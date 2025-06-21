package org.gnucash.android.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat

abstract class GnuPreferenceFragment : PreferenceFragmentCompat() {

    @get:StringRes
    protected abstract val titleId: Int

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val titleId = this.titleId
        if (titleId == 0) return
        val actionBar = checkNotNull((requireActivity() as AppCompatActivity).supportActionBar)
        actionBar.setTitle(titleId)
    }
}