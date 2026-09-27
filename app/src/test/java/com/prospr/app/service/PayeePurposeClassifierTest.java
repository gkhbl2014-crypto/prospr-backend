package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberTransactionLabel;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.MemberTransactionLabelRepository;

@ExtendWith(MockitoExtension.class)
class PayeePurposeClassifierTest {

    @Mock
    private MemberTransactionLabelRepository labelRepository;

    private final CounterpartyKeyResolver counterpartyKeyResolver = new CounterpartyKeyResolver();

    private PayeePurposeClassifier classifier() {
        return new PayeePurposeClassifier(labelRepository, counterpartyKeyResolver);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    private Transaction txn(Member member, String narration) {
        return Transaction.builder().member(member).narration(narration).type("DEBIT")
                .amount(new BigDecimal("2000")).build();
    }

    @Test
    void sharedExpensePurposeAppliesTheLifestyleCreepSyntheticCategory() {
        Member member = member();
        Transaction txn = txn(member, "UPI/RAHULKUMAR@okaxis");
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of(
                MemberTransactionLabel.builder().counterpartyKey("VPA:rahulkumar@okaxis")
                        .purpose(PayeePurpose.SHARED_EXPENSE).evidenceCount(3).build()));

        classifier().applyLearnedPurposes(List.of(txn));

        assertThat(txn.getCategory()).isEqualTo(LifestyleCategoryCatalog.MARKED_LIFESTYLE_CREEP);
        assertThat(txn.getCategorySource()).isEqualTo(PayeePurposeClassifier.SOURCE_LEARNED_PURPOSE);
        assertThat(txn.getTransactionType()).isEqualTo(TransactionClassificationService.EXPENSE);
    }

    @Test
    void householdPurposeAppliesTheEssentialSyntheticCategory() {
        Member member = member();
        Transaction txn = txn(member, "UPI/RAHULKUMAR@okaxis");
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of(
                MemberTransactionLabel.builder().counterpartyKey("VPA:rahulkumar@okaxis")
                        .purpose(PayeePurpose.HOUSEHOLD).evidenceCount(3).build()));

        classifier().applyLearnedPurposes(List.of(txn));

        assertThat(txn.getCategory()).isEqualTo(EssentialCategoryCatalog.MARKED_ESSENTIAL);
    }

    @Test
    void personalTransferAndRepaymentAndOtherPurposesLeaveCategoryAlone() {
        // These three purposes are context/labels only - forcing a spending category onto a transfer
        // or debt repayment would misrepresent it as consumption, so the classifier must not touch them.
        Member member = member();
        Transaction transferTxn = txn(member, "UPI/A@okaxis");
        Transaction repaymentTxn = txn(member, "UPI/B@okaxis");
        Transaction otherTxn = txn(member, "UPI/C@okaxis");
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of(
                MemberTransactionLabel.builder().counterpartyKey("VPA:a@okaxis").purpose(PayeePurpose.PERSONAL_TRANSFER).evidenceCount(1).build(),
                MemberTransactionLabel.builder().counterpartyKey("VPA:b@okaxis").purpose(PayeePurpose.REPAYMENT).evidenceCount(1).build(),
                MemberTransactionLabel.builder().counterpartyKey("VPA:c@okaxis").purpose(PayeePurpose.OTHER).evidenceCount(1).build()));

        classifier().applyLearnedPurposes(List.of(transferTxn, repaymentTxn, otherTxn));

        assertThat(transferTxn.getCategory()).isNull();
        assertThat(repaymentTxn.getCategory()).isNull();
        assertThat(otherTxn.getCategory()).isNull();
    }

    @Test
    void neverOverwritesATransactionWithItsOwnIndividualUserOverride() {
        // The core "must not silently rewrite a user-confirmed classification" requirement: an
        // individual /tag action on this one transaction must survive even though the payee has a
        // learned purpose that would otherwise apply.
        Member member = member();
        Transaction txn = txn(member, "UPI/RAHULKUMAR@okaxis");
        txn.setUserCategoryOverride("INVESTMENT");
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of(
                MemberTransactionLabel.builder().counterpartyKey("VPA:rahulkumar@okaxis")
                        .purpose(PayeePurpose.SHARED_EXPENSE).evidenceCount(3).build()));

        classifier().applyLearnedPurposes(List.of(txn));

        assertThat(txn.getCategory()).isNull();
        assertThat(txn.getUserCategoryOverride()).isEqualTo("INVESTMENT");
    }

    @Test
    void neverOverwritesARealMerchantDerivedCategory() {
        Member member = member();
        Transaction txn = txn(member, "UPI/RAHULKUMAR@okaxis");
        txn.setCategory("FOOD_DELIVERY");
        txn.setCategorySource(TransactionCategorizationService.SOURCE_MERCHANT_DB);
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of(
                MemberTransactionLabel.builder().counterpartyKey("VPA:rahulkumar@okaxis")
                        .purpose(PayeePurpose.SHARED_EXPENSE).evidenceCount(3).build()));

        classifier().applyLearnedPurposes(List.of(txn));

        assertThat(txn.getCategory()).isEqualTo("FOOD_DELIVERY");
    }

    @Test
    void doesNotThrowOnATransactionWithACategoryButNoCategorySource() {
        // Regression: Set.of(...).contains(null) throws NPE, unlike a regular HashSet - a real
        // transaction can have a non-null category with categorySource left null (e.g. categorized
        // before categorySource was consistently populated on every path). Must be treated as "not
        // safe to overwrite" (same as any other non-learned-purpose category), not crash.
        Member member = member();
        Transaction txn = txn(member, "UPI/RAHULKUMAR@okaxis");
        txn.setCategory("FOOD_DELIVERY");
        txn.setCategorySource(null);
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of(
                MemberTransactionLabel.builder().counterpartyKey("VPA:rahulkumar@okaxis")
                        .purpose(PayeePurpose.SHARED_EXPENSE).evidenceCount(3).build()));

        classifier().applyLearnedPurposes(List.of(txn));

        assertThat(txn.getCategory()).isEqualTo("FOOD_DELIVERY");
        assertThat(txn.getCategorySource()).isNull();
    }

    @Test
    void reapplyingADifferentPurposeSafelyOverwritesThePreviouslyLearnedCategory() {
        Member member = member();
        Transaction txn = txn(member, "UPI/RAHULKUMAR@okaxis");
        txn.setCategory(LifestyleCategoryCatalog.MARKED_LIFESTYLE_CREEP);
        txn.setCategorySource(PayeePurposeClassifier.SOURCE_LEARNED_PURPOSE);
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of(
                MemberTransactionLabel.builder().counterpartyKey("VPA:rahulkumar@okaxis")
                        .purpose(PayeePurpose.HOUSEHOLD).evidenceCount(3).build()));

        classifier().applyLearnedPurposes(List.of(txn));

        assertThat(txn.getCategory()).isEqualTo(EssentialCategoryCatalog.MARKED_ESSENTIAL);
    }

    @Test
    void doesNothingWhenNoPurposeIsLearnedForTheCounterparty() {
        Member member = member();
        Transaction txn = txn(member, "UPI/UNKNOWNPERSON@okaxis");
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of());

        classifier().applyLearnedPurposes(List.of(txn));

        assertThat(txn.getCategory()).isNull();
    }
}
