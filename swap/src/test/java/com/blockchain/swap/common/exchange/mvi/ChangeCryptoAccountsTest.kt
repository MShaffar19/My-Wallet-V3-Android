package com.blockchain.swap.common.exchange.mvi

import info.blockchain.balance.AccountReference
import info.blockchain.balance.CryptoCurrency
import org.amshove.kluent.`should be`
import org.junit.Test

class ChangeCryptoAccountsTest {

    @Test
    fun `can change the "from" account`() {
        given(
            initial("CAD", CryptoCurrency.BTC to CryptoCurrency.ETHER)
        ).on(
            ChangeCryptoFromAccount(
                from = AccountReference.BitcoinLike(CryptoCurrency.BCH, "", "")
            )
        ) {
            assertValue {
                it.from.cryptoValue.currency `should be` CryptoCurrency.BCH
                it.to.cryptoValue.currency `should be` CryptoCurrency.ETHER
                it.to.fiatValue.currencyCode `should be` "CAD"
                true
            }
        }
    }

    @Test
    fun `can change the "to" account`() {
        given(
            initial("GBP", CryptoCurrency.BTC to CryptoCurrency.ETHER)
        ).on(
            ChangeCryptoToAccount(
                to = AccountReference.BitcoinLike(CryptoCurrency.BCH, "", "")
            )
        ) {
            assertValue {
                it.from.cryptoValue.currency `should be` CryptoCurrency.BTC
                it.to.cryptoValue.currency `should be` CryptoCurrency.BCH
                it.to.fiatValue.currencyCode `should be` "GBP"
                true
            }
        }
    }

    @Test
    fun `if the "from" currency matches the "to", they swap`() {
        given(
            com.blockchain.swap.common.exchange.mvi.initial(
                "GBP",
                AccountReference.BitcoinLike(CryptoCurrency.BTC, "", ""),
                AccountReference.Ethereum("1", "0x123")
            )
        ).on(
            ChangeCryptoFromAccount(
                from = AccountReference.Ethereum("1", "0x456")
            )
        ) {
            assertValue {
                it.from.cryptoValue.currency `should be` CryptoCurrency.ETHER
                it.to.cryptoValue.currency `should be` CryptoCurrency.BTC
                it.to.fiatValue.currencyCode `should be` "GBP"
                true
            }
        }
    }

    @Test
    fun `if the "to" currency matches the "from", they swap`() {
        given(
            com.blockchain.swap.common.exchange.mvi.initial(
                "GBP",
                AccountReference.BitcoinLike(CryptoCurrency.BTC, "1", "xpub123"),
                AccountReference.Ethereum("", "")
            )
        ).on(
            ChangeCryptoToAccount(
                to = AccountReference.BitcoinLike(CryptoCurrency.BTC, "2", "xpub456")
            )
        ) {
            assertValue {
                it.from.cryptoValue.currency `should be` CryptoCurrency.ETHER
                it.to.cryptoValue.currency `should be` CryptoCurrency.BTC
                it.to.fiatValue.currencyCode `should be` "GBP"
                true
            }
        }
    }
}
