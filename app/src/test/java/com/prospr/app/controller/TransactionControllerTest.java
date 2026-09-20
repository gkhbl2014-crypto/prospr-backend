package com.prospr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.prospr.app.dto.request.UpdateTransactionHiddenRequest;
import com.prospr.app.dto.response.TransactionResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.Transaction;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.TransactionRepository;
import com.prospr.app.service.CategoryTaxonomy;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private Authentication authentication;

    private final CategoryTaxonomy categoryTaxonomy = new CategoryTaxonomy();

    private TransactionController controller() {
        return new TransactionController(transactionRepository, memberRepository, categoryTaxonomy);
    }

    private Member member(String email, Family family) {
        return Member.builder().id(UUID.randomUUID()).email(email).family(family).build();
    }

    private Transaction txn(Member owner, boolean hidden) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .member(owner)
                .maskedAccountNumber("XXXX1234")
                .txnId("TXN-1")
                .mode("UPI")
                .type("DEBIT")
                .amount(new BigDecimal("500"))
                .transactionalBalance(new BigDecimal("10000"))
                .narration("Swiggy order")
                .reference("REF-1")
                .transactionTimestamp(OffsetDateTime.now())
                .isHidden(hidden)
                .build();
    }

    private UpdateTransactionHiddenRequest request(boolean hidden) {
        UpdateTransactionHiddenRequest request = new UpdateTransactionHiddenRequest();
        request.setHidden(hidden);
        return request;
    }

    @Test
    void listReturnsHiddenAndVisibleTransactionsBothFlaggedCorrectly() {
        Family family = Family.builder().id(UUID.randomUUID()).build();
        Member caller = member("caller@example.com", family);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByMemberFamilyIdOrderByTransactionTimestampDesc(family.getId()))
                .thenReturn(List.of(txn(caller, false), txn(caller, true)));

        List<TransactionResponse> result = controller().listTransactions(authentication).getBody();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isHidden()).isFalse();
        assertThat(result.get(1).isHidden()).isTrue();
        // Full detail is still returned even for the hidden one - it's a display filter, not a
        // security/privacy redaction - so aggregates computed from this list stay accurate.
        assertThat(result.get(1).getAmount()).isEqualByComparingTo("500");
        assertThat(result.get(1).getNarration()).isEqualTo("Swiggy order");
    }

    @Test
    void newTransactionDefaultsToNotHidden() {
        Family family = Family.builder().id(UUID.randomUUID()).build();
        Member caller = member("caller@example.com", family);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByMemberFamilyIdOrderByTransactionTimestampDesc(family.getId()))
                .thenReturn(List.of(txn(caller, false)));

        TransactionResponse result = controller().listTransactions(authentication).getBody().get(0);

        assertThat(result.isHidden()).isFalse();
    }

    @Test
    void ownerCanHideTheirOwnTransaction() {
        Member caller = member("caller@example.com", null);
        Transaction owned = txn(caller, false);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByIdAndMemberId(owned.getId(), caller.getId())).thenReturn(Optional.of(owned));

        TransactionResponse result = controller().updateHidden(owned.getId(), request(true), authentication).getBody();

        assertThat(result.isHidden()).isTrue();
        assertThat(owned.getIsHidden()).isTrue();
    }

    @Test
    void ownerCanUnhideAPreviouslyHiddenTransaction() {
        Member caller = member("caller@example.com", null);
        Transaction owned = txn(caller, true);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByIdAndMemberId(owned.getId(), caller.getId())).thenReturn(Optional.of(owned));

        TransactionResponse result = controller().updateHidden(owned.getId(), request(false), authentication).getBody();

        assertThat(result.isHidden()).isFalse();
    }

    @Test
    void cannotHideAnotherMembersTransaction() {
        Member caller = member("caller@example.com", null);
        Member other = member("other@example.com", null);
        Transaction othersTxn = txn(other, false);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        // findByIdAndMemberId is scoped to the caller's own id, so another member's transaction id
        // simply never resolves for this caller - this is the actual IDOR protection.
        when(transactionRepository.findByIdAndMemberId(othersTxn.getId(), caller.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().updateHidden(othersTxn.getId(), request(true), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void emptyTransactionResultReturnsEmptyList() {
        Family family = Family.builder().id(UUID.randomUUID()).build();
        Member caller = member("caller@example.com", family);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByMemberFamilyIdOrderByTransactionTimestampDesc(family.getId()))
                .thenReturn(List.of());

        List<TransactionResponse> result = controller().listTransactions(authentication).getBody();

        assertThat(result).isEmpty();
    }
}
