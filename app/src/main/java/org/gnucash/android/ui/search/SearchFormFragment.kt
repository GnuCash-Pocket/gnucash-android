package org.gnucash.android.ui.search

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.gnucash.android.R
import org.gnucash.android.app.actionBar
import org.gnucash.android.databinding.FragmentSearchFormBinding
import org.gnucash.android.databinding.ItemSearchDateBinding
import org.gnucash.android.databinding.ItemSearchDescriptionBinding
import org.gnucash.android.databinding.ItemSearchMemoBinding
import org.gnucash.android.databinding.ItemSearchNotesBinding
import org.gnucash.android.databinding.ItemSearchNumericBinding
import org.gnucash.android.ui.adapter.DefaultItemSelectedListener
import org.gnucash.android.ui.adapter.SpinnerArrayAdapter
import org.gnucash.android.ui.adapter.SpinnerItem
import org.gnucash.android.ui.search.SearchResultsFragment.Companion.EXTRA_FORM
import org.gnucash.android.ui.text.DefaultTextWatcher
import org.gnucash.android.ui.util.dialog.DatePickerDialogFragment
import org.gnucash.android.util.toMillis
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

class SearchFormFragment : Fragment() {
    private val viewModel by viewModels<SearchFormViewModel>()
    private var binding: FragmentSearchFormBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            viewModel.query.collect { sql ->
                if (!sql.isNullOrEmpty()) {
                    showResults(sql)
                    viewModel.onSearchShowed()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentSearchFormBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actionBar: ActionBar? = this.actionBar
        actionBar?.setTitle(R.string.title_search_form)

        val binding = binding!!
        val form = viewModel.form

        bind(binding, form)
    }

    private fun showResults(sqlWhere: String) {
        val args = Bundle()
        args.putString(EXTRA_FORM, sqlWhere)

        val fragment = SearchResultsFragment()
        fragment.arguments = args

        val fm = parentFragmentManager
        fm.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("search")
            .commit()
    }

    private fun bind(binding: FragmentSearchFormBinding, form: SearchForm) {
        bindComparison(binding)
        binding.menuAdd.setOnClickListener { showAddMenu(binding.menuAdd) }
        binding.btnSearch.setOnClickListener { viewModel.onSearchClicked() }

        binding.list.removeAllViews()
        for (criterion in form.criteria) {
            when (criterion) {
                is SearchCriteria.Date -> addDatePosted(binding.list, criterion)
                is SearchCriteria.Description -> addDescription(binding.list, criterion)
                is SearchCriteria.Memo -> addMemo(binding.list, criterion)
                is SearchCriteria.Note -> addNote(binding.list, criterion)
                is SearchCriteria.Numeric -> addNumeric(binding.list, criterion)
            }
        }
    }

    private fun bindComparison(binding: FragmentSearchFormBinding) {
        val context: Context = binding.root.context
        val comparisons = listOf(
            SpinnerItem(ComparisonType.All, context.getString(R.string.search_criteria_all)),
            SpinnerItem(ComparisonType.Any, context.getString(R.string.search_criteria_any))
        )
        binding.groupingCombo.adapter = SpinnerArrayAdapter(context, comparisons)
        binding.groupingCombo.onItemSelectedListener =
            DefaultItemSelectedListener(false) { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                viewModel.setComparison(comparisons[position].value)
            }
    }

    private fun bind(binding: ItemSearchDescriptionBinding, criterion: SearchCriteria.Description) {
        val context: Context = binding.root.context
        val adapter = createStringComparisonAdapter(context)
        binding.comparison.adapter = adapter
        binding.comparison.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                val item = adapter.getItem(position)!!
                criterion.compare = item.value
            }
        binding.comparison.post {
            binding.comparison.setSelection(adapter.getValuePosition(criterion.compare))
        }
        binding.inputTransactionName.setText(criterion.value)
        binding.inputTransactionName.addTextChangedListener(DefaultTextWatcher { s ->
            criterion.value = s.toString()
        })
        binding.deleteBtn.setOnClickListener {
            viewModel.remove(criterion)
            removeItem(binding.root)
        }
    }

    private fun bind(binding: ItemSearchNotesBinding, criterion: SearchCriteria.Note) {
        val context: Context = binding.root.context
        val adapter = createStringComparisonAdapter(context)
        binding.comparison.adapter = adapter
        binding.comparison.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                val item = adapter.getItem(position)!!
                criterion.compare = item.value
            }
        binding.comparison.post {
            binding.comparison.setSelection(adapter.getValuePosition(criterion.compare))
        }
        binding.notes.setText(criterion.value)
        binding.notes.addTextChangedListener(DefaultTextWatcher { s ->
            criterion.value = s.toString()
        })
        binding.deleteBtn.setOnClickListener {
            viewModel.remove(criterion)
            removeItem(binding.root)
        }
    }

    private fun bind(binding: ItemSearchMemoBinding, criterion: SearchCriteria.Memo) {
        val context: Context = binding.root.context
        val adapter = createStringComparisonAdapter(context)
        binding.comparison.adapter = adapter
        binding.comparison.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                val item = adapter.getItem(position)!!
                criterion.compare = item.value
            }
        binding.comparison.post {
            binding.comparison.setSelection(adapter.getValuePosition(criterion.compare))
        }
        binding.inputSplitMemo.setText(criterion.value)
        binding.inputSplitMemo.addTextChangedListener(DefaultTextWatcher { s ->
            criterion.value = s.toString()
        })
        binding.deleteBtn.setOnClickListener {
            viewModel.remove(criterion)
            removeItem(binding.root)
        }
    }

    private fun bind(binding: ItemSearchDateBinding, criterion: SearchCriteria.Date) {
        val context: Context = binding.root.context
        val adapter = createDateComparisonAdapter(context)
        binding.comparison.adapter = adapter
        binding.comparison.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                val item = adapter.getItem(position)!!
                criterion.compare = item.value
            }
        binding.comparison.post {
            binding.comparison.setSelection(adapter.getValuePosition(criterion.compare))
        }
        val date = criterion.value ?: LocalDate.now()
        binding.dateText.text = dateFormatter.print(date)
        binding.dateText.setOnClickListener {
            val listener = DatePickerDialog.OnDateSetListener { view, year, month, dayOfMonth ->
                criterion.set(year, month + 1, dayOfMonth)
                binding.dateText.text = dateFormatter.print(criterion.value!!)
            }
            val dateMillis = criterion.value?.toMillis() ?: System.currentTimeMillis()
            DatePickerDialogFragment.newInstance(listener, dateMillis)
                .show(parentFragmentManager, "date_fragment")
        }
        binding.deleteBtn.setOnClickListener {
            viewModel.remove(criterion)
            removeItem(binding.root)
        }
    }

    private fun bind(binding: ItemSearchNumericBinding, criterion: SearchCriteria.Numeric) {
        val bindingRoot = this@SearchFormFragment.binding ?: return

        val context: Context = binding.root.context
        val adapter = createNumericComparisonAdapter(context)
        binding.comparison.adapter = adapter
        binding.comparison.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                val item = adapter.getItem(position)!!
                criterion.compare = item.value
            }
        binding.comparison.post {
            binding.comparison.setSelection(adapter.getValuePosition(criterion.compare))
        }
        binding.inputSplitAmount.setValue(criterion.value, true)
        binding.inputSplitAmount.bindKeyboard(bindingRoot.calculatorKeyboard)
        binding.inputSplitAmount.addTextChangedListener(DefaultTextWatcher { s ->
            val eval = binding.inputSplitAmount.evaluate()
            if (eval.isEmpty()) {
                criterion.value = null
            } else {
                criterion.value = binding.inputSplitAmount.value
            }
        })
        binding.deleteBtn.setOnClickListener {
            viewModel.remove(criterion)
            removeItem(binding.root)
        }
    }

    private fun showAddMenu(v: View) {
        val popupMenu = PopupMenu(v.context, v)
        val menu = popupMenu.menu
        menu.add(0, MENU_DESCRIPTION, 0, R.string.search_field_description)
        menu.add(0, MENU_NOTE, 0, R.string.search_field_notes)
        menu.add(0, MENU_DATE_POSTED, 0, R.string.search_field_date_posted)
        popupMenu.setOnMenuItemClickListener { item ->
            return@setOnMenuItemClickListener when (item.itemId) {
                MENU_DESCRIPTION -> {
                    addDescription()
                    true
                }

                MENU_NOTE -> {
                    addNote()
                    true
                }

                MENU_MEMO -> {
                    addMemo()
                    true
                }

                MENU_DATE_POSTED -> {
                    addDatePosted()
                    true
                }

                MENU_NUMERIC -> {
                    addNumeric()
                    true
                }

                else -> false
            }
        }
        popupMenu.show()
    }

    private fun addDescription() {
        val binding = binding ?: return
        addDescription(binding.list, viewModel.addDescription())
    }

    private fun addDescription(parent: ViewGroup, criterion: SearchCriteria.Description) {
        val bindingItem = ItemSearchDescriptionBinding.inflate(layoutInflater, parent, true)
        bind(bindingItem, criterion)
    }

    private fun addNote() {
        val binding = binding ?: return
        addNote(binding.list, viewModel.addNote())
    }

    private fun addNote(parent: ViewGroup, criterion: SearchCriteria.Note) {
        val bindingItem = ItemSearchNotesBinding.inflate(layoutInflater, parent, true)
        bind(bindingItem, criterion)
    }

    private fun addMemo() {
        val binding = binding ?: return
        addMemo(binding.list, viewModel.addMemo())
    }

    private fun addMemo(parent: ViewGroup, criterion: SearchCriteria.Memo) {
        val bindingItem = ItemSearchMemoBinding.inflate(layoutInflater, parent, true)
        bind(bindingItem, criterion)
    }

    private fun addDatePosted() {
        val binding = binding ?: return
        addDatePosted(binding.list, viewModel.addDate())
    }

    private fun addDatePosted(parent: ViewGroup, criterion: SearchCriteria.Date) {
        val bindingItem = ItemSearchDateBinding.inflate(layoutInflater, parent, true)
        bind(bindingItem, criterion)
    }

    private fun addNumeric() {
        val binding = binding ?: return
        addNumeric(binding.list, viewModel.addNumeric())
    }

    private fun addNumeric(parent: ViewGroup, criterion: SearchCriteria.Numeric) {
        val bindingItem = ItemSearchNumericBinding.inflate(layoutInflater, parent, true)
        bind(bindingItem, criterion)
    }

    private fun removeItem(view: View) {
        val binding = binding ?: return
        val parent = binding.list
        parent.removeView(view)
    }

    private fun createStringComparisonAdapter(context: Context): SpinnerArrayAdapter<StringCompare> {
        val items = listOf(
            SpinnerItem(
                StringCompare.Contains,
                context.getString(R.string.search_compare_contains)
            ),
            SpinnerItem(
                StringCompare.Equals,
                context.getString(R.string.search_compare_equals)
            )
        )
        return SpinnerArrayAdapter(context, items)
    }

    private fun createDateComparisonAdapter(context: Context): SpinnerArrayAdapter<Compare> {
        val items = listOf(
            SpinnerItem(Compare.LessThan, context.getString(R.string.search_compare_date_lt)),
            SpinnerItem(
                Compare.LessThanOrEqualTo,
                context.getString(R.string.search_compare_date_lte)
            ),
            SpinnerItem(Compare.EqualTo, context.getString(R.string.search_compare_date_eq)),
            SpinnerItem(Compare.NotEqualTo, context.getString(R.string.search_compare_date_neq)),
            SpinnerItem(Compare.GreaterThan, context.getString(R.string.search_compare_date_gt)),
            SpinnerItem(
                Compare.GreaterThanOrEqualTo,
                context.getString(R.string.search_compare_date_gte)
            ),
        )
        return SpinnerArrayAdapter(context, items)
    }

    private fun createNumericComparisonAdapter(context: Context): SpinnerArrayAdapter<Compare> {
        val items = listOf(
            SpinnerItem(Compare.LessThan, context.getString(R.string.search_compare_lt)),
            SpinnerItem(Compare.LessThanOrEqualTo, context.getString(R.string.search_compare_lte)),
            SpinnerItem(Compare.EqualTo, context.getString(R.string.search_compare_eq)),
            SpinnerItem(Compare.NotEqualTo, context.getString(R.string.search_compare_neq)),
            SpinnerItem(Compare.GreaterThan, context.getString(R.string.search_compare_gt)),
            SpinnerItem(
                Compare.GreaterThanOrEqualTo,
                context.getString(R.string.search_compare_gte)
            ),
        )
        return SpinnerArrayAdapter(context, items)
    }

    private fun createNumericDebitCreditComparisonAdapter(context: Context): SpinnerArrayAdapter<Compare> {
        val items = listOf(
            SpinnerItem(Compare.LessThan, context.getString(R.string.search_compare_debcred_lt)),
            SpinnerItem(
                Compare.LessThanOrEqualTo,
                context.getString(R.string.search_compare_date_lte)
            ),
            SpinnerItem(Compare.EqualTo, context.getString(R.string.search_compare_debcred_eq)),
            SpinnerItem(Compare.NotEqualTo, context.getString(R.string.search_compare_debcred_neq)),
            SpinnerItem(Compare.GreaterThan, context.getString(R.string.search_compare_debcred_gt)),
            SpinnerItem(
                Compare.GreaterThanOrEqualTo,
                context.getString(R.string.search_compare_debcred_gte)
            ),
        )
        return SpinnerArrayAdapter(context, items)
    }

    companion object {
        private const val MENU_DESCRIPTION = 0
        private const val MENU_NOTE = 1
        private const val MENU_MEMO = 2
        private const val MENU_DATE_POSTED = 3
        private const val MENU_NUMERIC = 4

        private val dateFormatter: DateTimeFormatter = DateTimeFormat.fullDate()
    }
}