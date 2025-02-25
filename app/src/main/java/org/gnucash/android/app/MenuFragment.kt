package org.gnucash.android.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.core.view.children
import androidx.fragment.app.Fragment

open class MenuFragment : Fragment() {

    private var menuThisFragment: MenuDiff? = null
    private var isMenuVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = requireActivity()
        activity.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                val itemsBefore = menu.children.toList()
                onCreateOptionsMenu(menu, menuInflater)
                val itemsAfter = menu.children.toList()
                menuThisFragment = MenuDiff(itemsBefore, itemsAfter).apply {
                    setMenuVisibility(isMenuVisible)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (context == null) return false
                return onOptionsItemSelected(menuItem)
            }
        }, this)
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        this.isMenuVisible = menuVisible
        // hide menu items that this fragment created.
        menuThisFragment?.setMenuVisibility(menuVisible)
    }
}