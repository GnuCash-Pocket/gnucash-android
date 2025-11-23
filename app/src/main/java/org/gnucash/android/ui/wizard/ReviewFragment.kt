package org.gnucash.android.ui.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tech.freak.wizardpager.model.AbstractWizardModel
import com.tech.freak.wizardpager.model.ModelCallbacks
import com.tech.freak.wizardpager.model.Page
import com.tech.freak.wizardpager.model.ReviewItem
import org.gnucash.android.R

class ReviewFragment : Fragment(), ModelCallbacks, AdapterView.OnItemClickListener {

    private var callbacks: Callbacks? = null
    private var wizardModel: AbstractWizardModel? = null
    private val reviewAdapter: ReviewAdapter = ReviewAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView: View =
            inflater.inflate(com.tech.freak.wizardpager.R.layout.fragment_page, container, false)
        val context = rootView.context

        val titleView = rootView.findViewById<View>(android.R.id.title) as TextView
        titleView.setText(com.tech.freak.wizardpager.R.string.review)
        titleView.setTextColor(
            ContextCompat.getColor(
                context,
                com.tech.freak.wizardpager.R.color.review_green
            )
        )

        val listView = rootView.findViewById<View>(android.R.id.list) as ListView
        listView.adapter = reviewAdapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.onItemClickListener = this

        return rootView
    }

    override fun onStart() {
        super.onStart()

        val context = requireContext()
        if (context !is Callbacks) {
            throw ClassCastException("Activity must implement fragment's callbacks")
        }

        wizardModel = null
        callbacks = (context as Callbacks).apply {
            wizardModel = onGetModel().apply {
                registerListener(this@ReviewFragment)
            }
        }

        onPageTreeChanged()
    }

    override fun onStop() {
        super.onStop()
        callbacks = null
        wizardModel?.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        reviewAdapter.notifyDataSetChanged()
        // Workaround for `notifyDataSetChanged` not working properly.
        view?.let { rootView ->
            val listView = rootView.findViewById<View>(android.R.id.list) as ListView
            listView.adapter = reviewAdapter
        }
    }

    override fun onPageDataChanged(page: Page?) {
        val model = wizardModel ?: return
        val reviewItems = ArrayList<ReviewItem>()
        for (p in model.getCurrentPageSequence()) {
            p.getReviewItems(reviewItems)
        }
        reviewItems.sortWith { a, b -> a.weight.compareTo(b.weight) }

        reviewAdapter.reviewItems = reviewItems
    }

    override fun onPageTreeChanged() {
        onPageDataChanged(null)
    }

    interface Callbacks {
        fun onGetModel(): AbstractWizardModel
        fun onEditScreenAfterReview(pageKey: String)
    }

    private class ReviewAdapter : BaseAdapter() {

        var reviewItems: List<ReviewItem> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun getCount(): Int {
            return reviewItems.size
        }

        override fun getItem(position: Int): Any {
            return reviewItems[position]
        }

        override fun getItemId(position: Int): Long {
            return reviewItems[position].hashCode().toLong()
        }

        override fun hasStableIds(): Boolean {
            return true
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val inflater = LayoutInflater.from(parent.context)
            val rootView: View = convertView ?: inflater.inflate(
                com.tech.freak.wizardpager.R.layout.list_item_review,
                parent,
                false
            )

            val reviewItem: ReviewItem = reviewItems[position]
            var value = reviewItem.displayValue
            if (value.isNullOrEmpty()) {
                value = rootView.context.getString(R.string.none)
            }
            (rootView.findViewById<TextView>(android.R.id.text1)!!).text = reviewItem.title
            (rootView.findViewById<TextView>(android.R.id.text2)!!).text = value
            return rootView
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val item = reviewAdapter.reviewItems[position]
        callbacks?.onEditScreenAfterReview(item.pageKey)
    }
}