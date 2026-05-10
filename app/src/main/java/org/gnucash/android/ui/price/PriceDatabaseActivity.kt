package org.gnucash.android.ui.price

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.gnucash.android.R
import org.gnucash.android.databinding.ActivityPricesBinding
import org.gnucash.android.ui.common.BaseDrawerActivity

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
        fun show(context: Context) {
            val intent = Intent(context, PriceDatabaseActivity::class.java)
            context.startActivity(intent)
        }
    }
}