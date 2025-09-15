package org.gnucash.android.app

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.core.view.children
import androidx.fragment.app.Fragment

open class MenuFragment : Fragment() {

    private var menuThisFragment: MenuDiff? = null
    private var isMenuVisible = true
    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            val itemsBefore = menu.children.toList()
            onCreateOptionsMenu(menu, menuInflater)
            onPrepareOptionsMenu(menu)
            val itemsAfter = menu.children.toList()
            menuThisFragment = MenuDiff(itemsBefore, itemsAfter).apply {
                setMenuVisibility(isMenuVisible)
            }
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (context == null) return false
            return onOptionsItemSelected(menuItem)
        }
    }

    override fun onStart() {
        super.onStart()
        val activity = requireActivity()
        activity.addMenuProvider(menuProvider, this)
    }

    override fun onStop() {
        super.onStop()
        activity?.removeMenuProvider(menuProvider)
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        this.isMenuVisible = menuVisible
        // hide menu items that this fragment created.
        menuThisFragment?.setMenuVisibility(menuVisible)
    }
}