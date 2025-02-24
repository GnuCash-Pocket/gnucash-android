package org.gnucash.android.ui.util.widget

import android.util.SparseArray
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

abstract class FragmentStateAdapter(activity: FragmentActivity) :
    RecyclerView.Adapter<FragmentViewHolder>() {

    private val fragmentManager: FragmentManager = activity.supportFragmentManager
    private val fragments = SparseArray<Fragment?>()

    init {
        setHasStableIds(true)
    }

    /**
     * Provide a new Fragment associated with the specified position.
     *
     *
     * The adapter will be responsible for the Fragment lifecycle:
     *
     *  * The Fragment will be used to display an item.
     *  * The Fragment will be destroyed when it gets too far from the viewport, and its state
     * will be saved. When the item is close to the viewport again, a new Fragment will be
     * requested, and a previously saved state will be used to initialize it.
     *
     * @see ViewPager2.setOffscreenPageLimit
     */
    abstract fun createFragment(position: Int): Fragment

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FragmentViewHolder {
        return FragmentViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: FragmentViewHolder, position: Int) {
        var fragment = fragments[position]
        if (fragment == null) {
            fragment = createFragment(position)
            fragments[position] = fragment
        }
        holder.bind(fragment, fragmentManager)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        removeFragments()
    }

    private fun removeFragments(now: Boolean = false) {
        val tx = fragmentManager.beginTransaction()
        val count = itemCount
        for (i in 0 until count) {
            val fragment = fragments[i]
            if (fragment != null) {
                tx.remove(fragment)
            }
        }
        if (now) {
            tx.commitNowAllowingStateLoss()
        } else {
            tx.commitAllowingStateLoss()
        }
        fragments.clear()
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    fun getFragment(position: Int): Fragment? {
        return fragments[position]
    }
}