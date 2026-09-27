package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.prospr.app.entity.Transaction;

class CounterpartyTypeClassifierTest {

    private final CounterpartyTypeClassifier classifier =
            new CounterpartyTypeClassifier(new CounterpartyKeyResolver(), new CategoryFallbackRules());

    private Transaction txn(String narration, String category) {
        return Transaction.builder().narration(narration).category(category).build();
    }

    @Test
    void aTransactionThatAlreadyHasACategoryIsAlwaysMerchant() {
        Transaction txn = txn("UPI/402312345678/SWIGGY INSTAMART/swiggy@icici/ICICI/Payment", "FOOD_DELIVERY");

        assertThat(classifier.classify(txn)).isEqualTo(CounterpartyTypeClassifier.MERCHANT);
    }

    @Test
    void aVpaContainingAKnownBrandKeywordIsMerchantEvenWithoutACategoryYet() {
        Transaction txn = txn("UPI/x/y/swiggy@icici/ICICI", null);

        assertThat(classifier.classify(txn)).isEqualTo(CounterpartyTypeClassifier.MERCHANT);
    }

    @Test
    void aPersonalLookingVpaHandleWithNoBrandMatchIsP2p() {
        // The exact example from the spec: a person's name as a UPI handle, no category resolvable.
        Transaction txn = txn("UPI/RAHULKUMAR@okaxis", null);

        assertThat(classifier.classify(txn)).isEqualTo(CounterpartyTypeClassifier.P2P);
    }

    @Test
    void aTwoWordPayeeNameWithNoVpaAndNoCategoryIsP2p() {
        Transaction txn = txn("UPI/RAMESH THARUN/YESB/15009", null);

        assertThat(classifier.classify(txn)).isEqualTo(CounterpartyTypeClassifier.P2P);
    }

    @Test
    void doesNotAutomaticallyTreatEveryP2pLookingUpiPaymentAsFoodOrShopping() {
        // Regression guard for the spec's explicit "do not infer Rahul -> Food" requirement: a P2P
        // result must never carry a category alongside it.
        Transaction txn = txn("UPI/RAHULKUMAR@okaxis", null);

        assertThat(classifier.classify(txn)).isEqualTo(CounterpartyTypeClassifier.P2P);
        assertThat(txn.getCategory()).isNull();
    }

    @Test
    void aSingleAmbiguousWordWithNoCategoryIsUnknownNotP2p() {
        Transaction txn = txn("UPI/Zepto/539411833928/Payment from Ph", null);

        assertThat(classifier.classify(txn)).isEqualTo(CounterpartyTypeClassifier.UNKNOWN);
    }

    @Test
    void aTransactionWithNoNarrationAtAllIsUnknown() {
        Transaction txn = Transaction.builder().build();

        assertThat(classifier.classify(txn)).isEqualTo(CounterpartyTypeClassifier.UNKNOWN);
    }
}
