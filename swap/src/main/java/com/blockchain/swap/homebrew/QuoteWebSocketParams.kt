package com.blockchain.swap.homebrew

import com.blockchain.swap.common.quote.ExchangeQuoteRequest
import com.blockchain.serialization.JsonSerializable
import io.reactivex.Observable
import java.util.Locale

data class QuoteWebSocketParams(
    val pair: String,
    val volume: String,
    val fiatCurrency: String,
    val fix: String,
    val type: String = "conversionSpecification"
) : JsonSerializable

data class QuoteWebSocketUnsubscribeParams(
    val pair: String,
    val type: String
) : JsonSerializable

fun Observable<ExchangeQuoteRequest>.mapToSocketParameters(): Observable<QuoteWebSocketParams> =
    map(ExchangeQuoteRequest::mapToSocketParameters)

internal fun ExchangeQuoteRequest.mapToSocketParameters() =
    when (this) {
        is ExchangeQuoteRequest.Selling ->
            QuoteWebSocketParams(
                pair = pair.pairCodeUpper,
                volume = offering.toStringWithoutSymbol(Locale.US).removeComma(),
                fiatCurrency = indicativeFiatSymbol,
                fix = "base"
            )
        is ExchangeQuoteRequest.SellingFiatLinked ->
            QuoteWebSocketParams(
                pair = pair.pairCodeUpper,
                volume = offeringFiatValue.toStringWithoutSymbol(Locale.US).removeComma(),
                fiatCurrency = offeringFiatValue.currencyCode,
                fix = "baseInFiat"
            )
        is ExchangeQuoteRequest.Buying ->
            QuoteWebSocketParams(
                pair = pair.pairCodeUpper,
                volume = wanted.toStringWithoutSymbol(Locale.US).removeComma(),
                fiatCurrency = indicativeFiatSymbol,
                fix = "counter"
            )
        is ExchangeQuoteRequest.BuyingFiatLinked ->
            QuoteWebSocketParams(
                pair = pair.pairCodeUpper,
                volume = wantedFiatValue.toStringWithoutSymbol(Locale.US).removeComma(),
                fiatCurrency = wantedFiatValue.currencyCode,
                fix = "counterInFiat"
            )
    }

private fun String.removeComma(): String {
    return replace(",", "")
}
