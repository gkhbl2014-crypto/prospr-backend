package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.config.ClassificationProperties;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberTransactionLabel;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.MemberTransactionLabelRepository;
import com.prospr.app.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class ReviewQueueServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MemberTransactionLabelRepository labelRepository;

    private final CounterpartyKeyResolver counterpartyKeyResolver = new CounterpartyKeyResolver();
    private final ClassificationProperties properties = new ClassificationProperties();
    private final PayeePatternService patternService = new PayeePatternService(properties);

    private ReviewQueueService service() {
        return new ReviewQueueService(transactionRepository, labelRepository, counterpartyKeyResolver,
                patternService, properties);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    private Transaction p2pTxn(LocalDate date, String amount, String narration) {
        return p2pTxn(date, amount, narration, "DEBIT");
    }

    private Transaction p2pTxn(LocalDate date, String amount, String narration, String type) {
        return Transaction.builder().narration(narration).amount(new BigDecimal(amount)).valueDate(date)
                .type(type).category(null).counterpartyType(CounterpartyTypeClassifier.P2P).build();
    }

    @Test
    void groupsMultipleTransactionsToTheSameCounterpartyIntoOneEntry() {
        Member member = member();
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of());
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(List.of(
                p2pTxn(LocalDate.of(2026, 4, 1), "500", "UPI/RAHULKUMAR@okaxis"),
                p2pTxn(LocalDate.of(2026, 5, 1), "700", "UPI/RAHULKUMAR@okaxis"),
                p2pTxn(LocalDate.of(2026, 6, 1), "1200", "UPI/RAHULKUMAR@okaxis")));

        List<ReviewQueueService.ReviewQueueEntry> queue = service().buildQueue(member);

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).transactionCount()).isEqualTo(3);
        assertThat(queue.get(0).totalAmount()).isEqualByComparingTo("2400");
        assertThat(queue.get(0).displayName()).isEqualTo("rahulkumar@okaxis");
        assertThat(queue.get(0).direction()).isEqualTo(ReviewQueueService.DIRECTION_DEBIT);
    }

    @Test
    void creditsAndDebitsToTheSameCounterpartyAreSeparateCards() {
        Member member = member();
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of());
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(List.of(
                p2pTxn(LocalDate.of(2026, 4, 1), "5000", "UPI/RAHULKUMAR@okaxis", "DEBIT"),
                p2pTxn(LocalDate.of(2026, 5, 1), "106000", "UPI/RAHULKUMAR@okaxis", "CREDIT")));

        List<ReviewQueueService.ReviewQueueEntry> queue = service().buildQueue(member);

        assertThat(queue).hasSize(2);
        assertThat(queue).extracting(ReviewQueueService.ReviewQueueEntry::direction)
                .containsExactlyInAnyOrder(ReviewQueueService.DIRECTION_CREDIT, ReviewQueueService.DIRECTION_DEBIT);
        assertThat(queue).allMatch(entry -> entry.counterpartyKey().equals("VPA:rahulkumar@okaxis"));
    }

    @Test
    void excludesACounterpartyTheMemberAlreadyAnsweredAPurposeFor() {
        Member member = member();
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of(
                MemberTransactionLabel.builder().counterpartyKey("VPA:rahulkumar@okaxis")
                        .purpose(PayeePurpose.SHARED_EXPENSE).evidenceCount(1).build()));
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(List.of(
                p2pTxn(LocalDate.of(2026, 4, 1), "5000", "UPI/RAHULKUMAR@okaxis")));

        List<ReviewQueueService.ReviewQueueEntry> queue = service().buildQueue(member);

        assertThat(queue).isEmpty();
    }

    @Test
    void excludesGroupsBelowTheMaterialityThreshold() {
        Member member = member();
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of());
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(List.of(
                p2pTxn(LocalDate.of(2026, 4, 1), "10", "UPI/SMALLPAYEE@okaxis")));

        List<ReviewQueueService.ReviewQueueEntry> queue = service().buildQueue(member);

        assertThat(queue).isEmpty();
    }

    @Test
    void rendersHighestTotalAmountFirst() {
        Member member = member();
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of());
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(List.of(
                p2pTxn(LocalDate.of(2026, 4, 1), "500", "UPI/SMALLERPAYEE@okaxis"),
                p2pTxn(LocalDate.of(2026, 4, 1), "15000", "UPI/BIGGERPAYEE@okaxis")));

        List<ReviewQueueService.ReviewQueueEntry> queue = service().buildQueue(member);

        assertThat(queue).hasSize(2);
        assertThat(queue.get(0).totalAmount()).isEqualByComparingTo("15000");
        assertThat(queue.get(1).totalAmount()).isEqualByComparingTo("500");
    }

    @Test
    void merchantTransactionsAreNeverIncludedInTheReviewQueue() {
        Member member = member();
        when(labelRepository.findByMemberId(member.getId())).thenReturn(List.of());
        Transaction merchantTxn = Transaction.builder().narration("UPI/SWIGGY/x/swiggy@icici").category("FOOD_DELIVERY")
                .amount(new BigDecimal("5000")).valueDate(LocalDate.of(2026, 4, 1))
                .counterpartyType(CounterpartyTypeClassifier.MERCHANT).build();
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId()))
                .thenReturn(List.of(merchantTxn));

        List<ReviewQueueService.ReviewQueueEntry> queue = service().buildQueue(member);

        assertThat(queue).isEmpty();
    }
}
