package org.gnucash.android.ui.account

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView

/**
 * [ViewHolder] implementation for handling [Fragment]s. Used in
 * [FragmentStateAdapter].
 */
class FragmentViewHolder private constructor(container: FrameLayout) :
    RecyclerView.ViewHolder(container) {

    val container: FrameLayout
        get() = itemView as FrameLayout

    var fragment: Fragment? = null
        private set

    fun bind(fragment: Fragment, fragmentManager: FragmentManager) {
        this.fragment = fragment
        val container = this.container
        if (container.childCount > 0) return // some fragment already bound

        fragmentManager.registerFragmentLifecycleCallbacks(object :
            FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(
                fm: FragmentManager,
                f: Fragment,
                v: View,
                savedInstanceState: Bundle?
            ) {
                super.onFragmentViewCreated(fm, f, v, savedInstanceState)
                if (f == fragment) {
                    fm.unregisterFragmentLifecycleCallbacks(this)
                    container.removeAllViews()
                    container.addView(v)
                }
            }
        }, false)

        fragment.setMenuVisibility(false)
        fragmentManager.beginTransaction()
            .add(fragment, "f$itemId")
            .commitNow()
    }

    companion object {
        @JvmStatic
        fun create(parent: ViewGroup): FragmentViewHolder {
            val container = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                id = ViewCompat.generateViewId()
                isSaveEnabled = false
            }
            return FragmentViewHolder(container)
        }
    }
}