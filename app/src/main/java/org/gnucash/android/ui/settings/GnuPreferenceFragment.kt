package org.gnucash.android.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBar
import androidx.preference.PreferenceFragmentCompat
import org.gnucash.android.app.actionBar

abstract class GnuPreferenceFragment : PreferenceFragmentCompat() {

    @get:StringRes
    protected abstract val titleId: Int

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val titleId = this.titleId
        if (titleId == 0) return
        val actionBar: ActionBar? = this.actionBar
        actionBar?.setTitle(titleId)
    }
}