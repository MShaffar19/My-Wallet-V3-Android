package piuk.blockchain.android.simplebuy

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.SimpleBuyAnalytics
import com.blockchain.notifications.analytics.eventWithPaymentMethod
import com.blockchain.swap.nabu.datamanagers.OrderState
import com.blockchain.swap.nabu.datamanagers.Quote
import com.blockchain.swap.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.ui.urllinks.URL_SUPPORT_BALANCE_LOCKED
import info.blockchain.balance.FiatValue
import kotlinx.android.synthetic.main.fragment_checkout.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.ErrorSlidingBottomDialog
import piuk.blockchain.android.ui.base.mvi.MviFragment
import piuk.blockchain.android.ui.base.setupToolbar
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.android.util.extensions.secondsToDays
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.utils.extensions.gone
import piuk.blockchain.androidcoreui.utils.extensions.goneIf
import piuk.blockchain.androidcoreui.utils.extensions.inflate
import piuk.blockchain.androidcoreui.utils.extensions.setOnClickListenerDebounced
import piuk.blockchain.androidcoreui.utils.extensions.visible
import piuk.blockchain.androidcoreui.utils.extensions.visibleIf

class SimpleBuyCheckoutFragment : MviFragment<SimpleBuyModel, SimpleBuyIntent, SimpleBuyState>(), SimpleBuyScreen,
    SimpleBuyCancelOrderBottomSheet.Host {

    override val model: SimpleBuyModel by scopedInject()
    private val stringUtils: StringUtils by inject()
    private var lastState: SimpleBuyState? = null
    private val checkoutAdapter = CheckoutAdapter()

    private val isForPendingPayment: Boolean by unsafeLazy {
        arguments?.getBoolean(PENDING_PAYMENT_ORDER_KEY, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_checkout)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = checkoutAdapter
            addItemDecoration(BlockchainListDividerDecor(requireContext()))
        }

        model.process(SimpleBuyIntent.FlowCurrentScreen(FlowScreen.CHECKOUT))
        activity.setupToolbar(
            if (isForPendingPayment) {
                R.string.order_details
            } else {
                R.string.checkout
            },
            !isForPendingPayment
        )
        model.process(SimpleBuyIntent.FetchQuote)
        model.process(SimpleBuyIntent.FetchBankAccount)
        model.process(SimpleBuyIntent.FetchWithdrawLockTime)
    }

    override fun backPressedHandled(): Boolean =
        isForPendingPayment

    override fun navigator(): SimpleBuyNavigator =
        (activity as? SimpleBuyNavigator) ?: throw IllegalStateException(
            "Parent must implement SimpleBuyNavigator")

    override fun onBackPressed(): Boolean = true

    override fun render(newState: SimpleBuyState) {
        // Event should be triggered only the first time a state is rendered
        if (lastState == null) {
            analytics.logEvent(eventWithPaymentMethod(SimpleBuyAnalytics.CHECKOUT_SUMMARY_SHOWN,
                newState.selectedPaymentMethod?.paymentMethodType?.toAnalyticsString() ?: ""))
            lastState = newState
        }

        progress.visibleIf { newState.isLoading }
        purchase_note.text = when {
            newState.selectedPaymentMethod?.isBank() == true -> {
                getString(R.string.purchase_bank_note, newState.selectedCryptoCurrency?.displayTicker)
            }
            newState.selectedPaymentMethod?.isCard() == true -> {
                getString(R.string.purchase_card_note_1)
            }
            newState.selectedPaymentMethod?.isFunds() == true -> {
                getString(R.string.purchase_funds_note)
            }
            else -> ""
        }

        showLockedFunds(newState.withdrawalLockPeriod.secondsToDays())

        if (newState.errorState != null) {
            showErrorState(newState.errorState)
            return
        }

        showAmountForMethod(newState)

        updateStatusPill(newState)

        checkoutAdapter.items = getListFields(newState)

        configureButtons(newState)

        when (newState.order.orderState) {
            OrderState.FINISHED, // Funds orders are getting finished right after confirmation
            OrderState.AWAITING_FUNDS -> {
                if (newState.confirmationActionRequested) {
                    if (newState.selectedPaymentMethod?.isBank() == true) {
                        navigator().goToBankDetailsScreen()
                    } else {
                        navigator().goToCardPaymentScreen()
                    }
                }
            }
            OrderState.CANCELED -> {
                model.process(SimpleBuyIntent.ClearState)
                navigator().exitSimpleBuyFlow()
            }
            else -> {
                // do nothing
            }
        }
    }

    private fun showLockedFunds(days: Long) {
        val intro = getString(R.string.purchase_card_note_2)
        val bold =
            resources.getQuantityString(R.plurals.lock_days, days.toInt(), days.toInt())
        val map = mapOf(
            "learn_more_link" to Uri.parse(URL_SUPPORT_BALANCE_LOCKED)
        )
        val boldAndLinked = stringUtils.getStringWithMappedLinks(
            R.string.common_linked_learn_more,
            map,
            requireActivity()
        )

        val sb = SpannableStringBuilder()
        sb.append(intro)
            .append(bold)
            .append(boldAndLinked)
            .setSpan(StyleSpan(Typeface.BOLD), intro.length,
                intro.length + bold.length + boldAndLinked.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_600)),
            intro.length + bold.length, intro.length + bold.length + boldAndLinked.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        purchase_note_1.apply {
            if (days > 0) {
                visible()
                movementMethod = LinkMovementMethod.getInstance()
                setText(sb, TextView.BufferType.SPANNABLE)
            } else gone()
        }
    }

    private fun showAmountForMethod(newState: SimpleBuyState) {
        amount.text = if (newState.selectedPaymentMethod?.isBank() == true) {
            if (newState.orderState == OrderState.PENDING_CONFIRMATION) {
                newState.quote?.estimatedAmount()
            } else {
                newState.orderValue?.toStringWithSymbol()
            }
        } else {
            newState.orderValue?.toStringWithSymbol()
        }
    }

    private fun updateStatusPill(newState: SimpleBuyState) {
        when {
            isPendingOrAwaitingFunds(newState.orderState) -> {
                status.text = getString(R.string.order_pending)
                status.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.bkgd_status_unconfirmed)
                status.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.grey_800))
            }
            newState.orderState == OrderState.FINISHED -> {
                status.text = getString(R.string.order_complete)
                status.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.bkgd_status_received)
                status.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.green_600))
            }
            else -> {
                status.gone()
            }
        }
    }

    private fun getListFields(state: SimpleBuyState) =
        listOf(
            if (state.selectedPaymentMethod?.isBank() == true) {
                CheckoutItem(getString(R.string.morph_exchange_rate),
                    "${state.quote?.rate?.toStringWithSymbol()} / " +
                            "${state.selectedCryptoCurrency?.displayTicker}")
            } else {
                CheckoutItem(getString(R.string.morph_exchange_rate),
                    "${state.orderExchangePrice?.toStringWithSymbol()} / " +
                            "${state.selectedCryptoCurrency?.displayTicker}")
            },

            CheckoutItem(getString(R.string.fees),
                state.fee?.toStringWithSymbol() ?: FiatValue.zero(state.fiatCurrency)
                    .toStringWithSymbol()),

            CheckoutItem(getString(R.string.common_total),
                state.order.amount?.toStringWithSymbol() ?: ""),

            CheckoutItem(getString(R.string.payment_method),
                state.selectedPaymentMethod?.let {
                    paymentMethodLabel(it, state.fiatCurrency)
                } ?: ""
            )
        )

    private fun Quote.estimatedAmount(): String =
        getString(R.string.approximately_symbol, estimatedAmount.toStringWithSymbol())

    private fun isPendingOrAwaitingFunds(orderState: OrderState) =
        isForPendingPayment || orderState == OrderState.AWAITING_FUNDS

    private fun configureButtons(state: SimpleBuyState) {
        val isOrderAwaitingFunds = state.orderState == OrderState.AWAITING_FUNDS

        button_action.apply {
            if (!isForPendingPayment && !isOrderAwaitingFunds) {
                text = getString(R.string.buy_now_1, state.orderValue?.toStringWithSymbol())
                setOnClickListener {
                    model.process(SimpleBuyIntent.ConfirmOrder)
                    analytics.logEvent(
                        eventWithPaymentMethod(SimpleBuyAnalytics.CHECKOUT_SUMMARY_CONFIRMED,
                            state.selectedPaymentMethod?.paymentMethodType?.toAnalyticsString() ?: ""))
                }
            } else {
                text = if (isOrderAwaitingFunds && !isForPendingPayment) {
                    getString(R.string.complete_payment)
                } else {
                    getString(R.string.ok_cap)
                }
                setOnClickListener {
                    if (isForPendingPayment) {
                        navigator().exitSimpleBuyFlow()
                    } else {
                        navigator().goToCardPaymentScreen()
                    }
                }
            }
        }

        button_action.isEnabled = !state.isLoading
        button_cancel.goneIf { isOrderAwaitingFunds || isForPendingPayment }
        button_cancel.setOnClickListenerDebounced {
            analytics.logEvent(SimpleBuyAnalytics.CHECKOUT_SUMMARY_PRESS_CANCEL)
            showBottomSheet(SimpleBuyCancelOrderBottomSheet.newInstance())
        }
    }

    private fun paymentMethodLabel(
        selectedPaymentMethod: SelectedPaymentMethod,
        fiatCurrency: String
    ): String =
        when (selectedPaymentMethod.paymentMethodType) {
            PaymentMethodType.BANK_ACCOUNT -> getString(R.string.checkout_bank_transfer_label)
            PaymentMethodType.FUNDS -> getString(R.string.currency_funds_wallet, fiatCurrency)
            else -> selectedPaymentMethod.label ?: ""
        }

    private fun showErrorState(errorState: ErrorState) {
        showBottomSheet(ErrorSlidingBottomDialog.newInstance(activity))
    }

    override fun cancelOrderConfirmAction(cancelOrder: Boolean, orderId: String?) {
        if (cancelOrder) {
            model.process(SimpleBuyIntent.CancelOrder)
            analytics.logEvent(
                eventWithPaymentMethod(SimpleBuyAnalytics.CHECKOUT_SUMMARY_CANCELLATION_CONFIRMED,
                    lastState?.selectedPaymentMethod?.paymentMethodType?.toAnalyticsString()
                        ?: ""))
        } else {
            analytics.logEvent(SimpleBuyAnalytics.CHECKOUT_SUMMARY_CANCELLATION_GO_BACK)
        }
    }

    override fun onPause() {
        super.onPause()
        model.process(SimpleBuyIntent.NavigationHandled)
    }

    override fun onSheetClosed() {
        if (lastState?.errorState != null) {
            model.process(SimpleBuyIntent.ClearError)
            model.process(SimpleBuyIntent.ClearState)
            navigator().exitSimpleBuyFlow()
        }
    }

    companion object {
        private const val PENDING_PAYMENT_ORDER_KEY = "PENDING_PAYMENT_KEY"

        fun newInstance(isForPending: Boolean = false): SimpleBuyCheckoutFragment {
            val fragment = SimpleBuyCheckoutFragment()
            fragment.arguments = Bundle().apply {
                putBoolean(PENDING_PAYMENT_ORDER_KEY, isForPending)
            }
            return fragment
        }
    }
}