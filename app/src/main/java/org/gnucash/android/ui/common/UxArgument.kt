/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.common

/**
 * Collection of constants which are passed across multiple pieces of the UI (fragments, activities, dialogs)
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
object UxArgument {
    /**
     * Key for passing the transaction GUID as parameter in a bundle
     */
    const val SELECTED_TRANSACTION_UID: String = "selected_transaction_uid"

    /**
     * Key for passing list of UIDs selected transactions as an argument in a bundle or intent
     */
    const val SELECTED_TRANSACTION_UIDS: String = "selected_transactions"

    /**
     * Key for the origin account as argument when moving accounts
     */
    const val ORIGIN_ACCOUNT_UID: String = "origin_account_uid"

    /**
     * Key for skipping the passcode screen. Use this only when there is no other choice.
     */
    const val SKIP_PASSCODE_SCREEN: String = "skip_passcode_screen"

    /**
     * Amount passed as a string
     */
    const val AMOUNT_STRING: String = "starting_amount"

    /**
     * Key for passing the account unique ID as argument to UI
     */
    const val SELECTED_ACCOUNT_UID: String = "account_uid"

    /**
     * Key for passing whether a widget should hide the account balance or not
     */
    const val HIDE_ACCOUNT_BALANCE_IN_WIDGET: String = "hide_account_balance"

    /**
     * Key for passing argument for the parent account GUID.
     */
    const val PARENT_ACCOUNT_UID: String = "parent_account_uid"

    /**
     * Key for passing the scheduled action UID to the transactions editor
     */
    const val SCHEDULED_ACTION_UID: String = "scheduled_action_uid"

    /**
     * Type of form displayed in the [FormActivity]
     */
    const val FORM_TYPE: String = "form_type"

    /**
     * List of splits which have been created using the split editor
     */
    const val SPLIT_LIST: String = "split_list"

    /**
     * GUID of a budget
     */
    const val BUDGET_UID: String = "budget_uid"

    /**
     * List of budget amounts (as csv)
     */
    const val BUDGET_AMOUNT_LIST: String = "budget_amount_list"

    /**
     * GUID of a book which is relevant for a specific action
     */
    const val BOOK_UID: String = "book_uid"

    /**
     * Show hidden items?
     */
    const val SHOW_HIDDEN: String = "show_hidden"
}
