package piuk.blockchain.android.withdraw

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.analytics.SimpleBuyAnalytics
import com.blockchain.notifications.analytics.withdrawEventWithCurrency
import info.blockchain.balance.FiatValue
import kotlinx.android.synthetic.main.fragment_withdraw_complete.icon
import kotlinx.android.synthetic.main.fragment_withdraw_complete.ok_btn
import kotlinx.android.synthetic.main.fragment_withdraw_complete.progress
import kotlinx.android.synthetic.main.fragment_withdraw_complete.state_indicator
import kotlinx.android.synthetic.main.fragment_withdraw_complete.subtitle
import kotlinx.android.synthetic.main.fragment_withdraw_complete.title
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.mvi.MviFragment
import piuk.blockchain.android.withdraw.mvi.WithdrawIntent
import piuk.blockchain.android.withdraw.mvi.WithdrawModel
import piuk.blockchain.android.withdraw.mvi.WithdrawState
import piuk.blockchain.androidcoreui.utils.extensions.inflate
import piuk.blockchain.androidcoreui.utils.extensions.visibleIf

class WithdrawCompleteFragment : MviFragment<WithdrawModel, WithdrawIntent, WithdrawState>(), WithdrawScreen {
    override val model: WithdrawModel by scopedInject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_withdraw_complete)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ok_btn.setOnClickListener {
            navigator().exitFlow()
        }
    }

    override fun render(newState: WithdrawState) {
        newState.currency?.let {
            icon.setImageResource(it.resource())
        }
        newState.amount?.let {
            renderTitleAndSubtitle(it, newState.isLoading, newState.withdrawSucceeded, newState.errorState != null)
        }
        newState.amount?.let { amount ->
            newState.selectedBank?.let { bank ->
                model.process(WithdrawIntent.CreateWithdrawOrder(
                    bankId = bank.id,
                    amount = amount
                ))
            }
        }

        if (newState.withdrawSucceeded) {
            analytics.logEventOnceForSession(withdrawEventWithCurrency(
                analytics = SimpleBuyAnalytics.WITHDRAWAL_SUCCESS,
                currency = newState.currency ?: ""
            ))
        }

        if (newState.errorState != null) {
            analytics.logEventOnceForSession(withdrawEventWithCurrency(
                analytics = SimpleBuyAnalytics.WITHDRAWAL_ERROR,
                currency = newState.currency ?: ""
            ))
        }
    }

    private fun renderTitleAndSubtitle(
        value: FiatValue,
        loading: Boolean,
        withdrawSucceeded: Boolean,
        hasError: Boolean
    ) {
        when {
            withdrawSucceeded -> {
                title.text = getString(R.string.withdrawal_amount, value.formatOrSymbolForZero())
                subtitle.text = getString(R.string.withdrawal_success_message, value.currencyCode)
            }
            loading -> {
                title.text = getString(R.string.withdrawing_amount, value.formatOrSymbolForZero())
                subtitle.text = getString(R.string.withdrawing_amount_message)
            }
            hasError -> {
                state_indicator.setImageResource(R.drawable.ic_alert)
                title.text = getString(R.string.card_error_title)
                subtitle.text = getString(R.string.order_error_subtitle)
            }
        }

        state_indicator.visibleIf { withdrawSucceeded || hasError }
        progress.visibleIf { loading }
        ok_btn.visibleIf { withdrawSucceeded || hasError }
        state_indicator.setImageResource(if (hasError) R.drawable.ic_alert else R.drawable.ic_check_circle)
    }

    override fun navigator(): WithdrawNavigator =
        (activity as? WithdrawNavigator)
            ?: throw IllegalStateException("Parent must implement WithdrawNavigator")

    override fun onBackPressed(): Boolean = true

    override fun backPressedHandled(): Boolean {
        return navigator().hasMoreThanOneFragmentInTheStack()
    }
}

private fun String.resource(): Int =
    when (this) {
        "EUR" -> R.drawable.ic_funds_euro_masked
        "GBP" -> R.drawable.ic_funds_gbp_masked
        "USD" -> R.drawable.ic_funds_usd_masked
        else -> R.drawable.ic_funds_usd_masked
    }
