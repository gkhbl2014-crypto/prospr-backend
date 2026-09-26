package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.prospr.app.entity.Transaction;

class CounterpartyKeyResolverTest {

    private final CounterpartyKeyResolver resolver = new CounterpartyKeyResolver();

    @Test
    void extractsVpaFromAnywhereInTheNarrationRegardlessOfSplitPosition() {
        Transaction withVpaAtIndex3 = Transaction.builder()
                .narration("UPI/402312345678/SWIGGY INSTAMART/swiggy@icici/ICICI/Payment").build();
        Transaction withVpaAtIndex1 = Transaction.builder()
                .narration("UPI/oilstore@ybl/402312345678/Payment").build();

        assertThat(resolver.resolveKey(withVpaAtIndex3)).contains("VPA:swiggy@icici");
        assertThat(resolver.resolveKey(withVpaAtIndex1)).contains("VPA:oilstore@ybl");
    }

    @Test
    void isCaseInsensitiveSoTheSameVpaAlwaysResolvesToTheSameKey() {
        Transaction lower = Transaction.builder().narration("UPI/x/y/oilstore@icici/ICICI").build();
        Transaction upper = Transaction.builder().narration("UPI/x/y/OilStore@ICICI/ICICI").build();

        assertThat(resolver.resolveKey(lower)).isEqualTo(resolver.resolveKey(upper));
    }

    @Test
    void extractsThePayeeNameFromTheFirstSegmentAfterUpiWhenThereIsNoVpa() {
        // Real reported case: "UPI/<payee>/<bank-code>/<ref>" has no @ anywhere, and the categorizer's
        // own (positionally-fixed, index-2) merchant extraction mis-derives the 4-letter bank code
        // "YESB" as the merchant name for this exact shape - the resolver must not inherit that bug.
        Transaction txn = Transaction.builder().narration("UPI/RAMESH THARUN/YESB/15009")
                .merchantName("Yesb").build();

        assertThat(resolver.resolveKey(txn)).contains("PAYEE:RAMESH THARUN");
    }

    @Test
    void twoTransactionsToTheSamePayeeInDifferentMonthsResolveToTheIdenticalKey() {
        Transaction july = Transaction.builder().narration("UPI/RAMESH THARUN/YESB/15009").build();
        Transaction august = Transaction.builder().narration("UPI/RAMESH THARUN/YESB/88231").build();

        assertThat(resolver.resolveKey(july)).isEqualTo(resolver.resolveKey(august));
    }

    @Test
    void extractsPayeeNameEvenWhenTheMerchantNameFieldHappensToAgree() {
        Transaction txn = Transaction.builder()
                .narration("UPI/Zepto/539411833928/Payment from Ph").merchantName("Zepto").build();

        assertThat(resolver.resolveKey(txn)).contains("PAYEE:ZEPTO");
    }

    @Test
    void rejectsAPureReferenceNumberAndFallsThroughToTheNextUsableSegment() {
        Transaction txn = Transaction.builder()
                .narration("UPI/402312345678/SWIGGY INSTAMART/Payment").build();

        // Index 1 ("402312345678") is a pure reference number, so it's skipped in favor of index 2.
        assertThat(resolver.resolveKey(txn)).contains("PAYEE:SWIGGY INSTAMART");
    }

    @Test
    void fallsBackToMerchantNameForNonUpiNarrations() {
        Transaction txn = Transaction.builder()
                .narration("NEFT-N123-ACME CORP-SALARY-AUG2026").merchantName("Acme Corp").build();

        assertThat(resolver.resolveKey(txn)).contains("MERCHANT:ACME CORP");
    }

    @Test
    void fallsBackToRawNarrationWhenNeitherVpaNorMerchantNameIsAvailable() {
        Transaction txn = Transaction.builder().narration("NEFT-ACME CORP-SAL-AUG2026").build();

        assertThat(resolver.resolveKey(txn)).contains("NARRATION:NEFT-ACME CORP-SAL-AUG2026");
    }

    @Test
    void isEmptyOnlyWhenThereIsNothingAtAllToIdentifyTheCounterpartyBy() {
        Transaction blank = Transaction.builder().build();

        assertThat(resolver.resolveKey(blank)).isEqualTo(Optional.empty());
    }
}
