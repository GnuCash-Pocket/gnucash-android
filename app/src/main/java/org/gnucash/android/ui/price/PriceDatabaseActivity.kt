package org.gnucash.android.ui.price

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.gnucash.android.R
import org.gnucash.android.databinding.ActivityPricesBinding
import org.gnucash.android.ui.common.BaseDrawerActivity
import org.gnucash.android.ui.common.UxArgument

class PriceDatabaseActivity : BaseDrawerActivity() {

    override val titleRes: Int = R.string.price_database

    private lateinit var binding: ActivityPricesBinding

    override fun inflateView() {
        val binding = ActivityPricesBinding.inflate(layoutInflater)
        this.binding = binding
        setContentView(binding.root)
        drawerLayout = binding.drawerLayout
        navigationView = binding.navView
        toolbar = binding.toolbarLayout.toolbar
        toolbarProgress = binding.toolbarLayout.toolbarProgress.progress
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val fragment = PriceListFragment()

            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, fragment)
                .commit()
        }
    }

    companion object {
        fun show(context: Context, bookUID: String) {
            val intent = Intent(context, PriceDatabaseActivity::class.java)
                .putExtra(UxArgument.BOOK_UID, bookUID)
            context.startActivity(intent)
        }
    }
}