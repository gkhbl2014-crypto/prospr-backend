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

import com.prospr.app.dto.request.UpdateTransactionDirectionRequest;
import com.prospr.app.dto.request.UpdateTransactionHiddenRequest;
import com.prospr.app.dto.request.UpdateTransactionLabelRequest;
import com.prospr.app.dto.request.UpdateTransactionTagRequest;
import com.prospr.app.dto.response.TransactionResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberTransactionLabel;
import com.prospr.app.entity.Transaction;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.exception.TransactionTagException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.MemberTransactionLabelRepository;
import com.prospr.app.repository.TransactionRepository;
import com.prospr.app.service.CategoryTaxonomy;
import com.prospr.app.service.CounterpartyKeyResolver;
import com.prospr.app.service.EssentialCategoryCatalog;
import com.prospr.app.service.FinancialAnalysisOrchestratorService;
import com.prospr.app.service.LifestyleCategoryCatalog;
import com.prospr.app.service.TransactionCategorizationService;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private MemberTransactionLabelRepository labelRepository;
    @Mock
    private FinancialAnalysisOrchestratorService financialAnalysisOrchestratorService;
    @Mock
    private Authentication authentication;

    private final CategoryTaxonomy categoryTaxonomy = new CategoryTaxonomy();
    private final CounterpartyKeyResolver counterpartyKeyResolver = new CounterpartyKeyResolver();

    private TransactionController controller() {
        return new TransactionController(transactionRepository, memberRepository, labelRepository,
                categoryTaxonomy, counterpartyKeyResolver, financialAnalysisOrchestratorService);
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

    private UpdateTransactionTagRequest tagRequest(String tag) {
        UpdateTransactionTagRequest request = new UpdateTransactionTagRequest();
        request.setTag(tag);
        return request;
    }

    private UpdateTransactionDirectionRequest directionRequest(String type) {
        UpdateTransactionDirectionRequest request = new UpdateTransactionDirectionRequest();
        request.setType(type);
        return request;
    }

    private UpdateTransactionLabelRequest labelRequest(String label) {
        UpdateTransactionLabelRequest request = new UpdateTransactionLabelRequest();
        request.setLabel(label);
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
    void taggingAsInvestmentSetsUserCategoryOverrideAndTriggersFullRecompute() {
        Member caller = member("caller@example.com", null);
        Transaction owned = txn(caller, false);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByIdAndMemberId(owned.getId(), caller.getId())).thenReturn(Optional.of(owned));

        TransactionResponse result = controller().updateTag(owned.getId(), tagRequest("INVESTMENT"), authentication).getBody();

        assertThat(owned.getUserCategoryOverride()).isEqualTo("INVESTMENT");
        assertThat(owned.getCategorySource()).isEqualTo(TransactionCategorizationService.SOURCE_USER_OVERRIDE);
        assertThat(owned.getUserOverrideAt()).isNotNull();
        assertThat(result.isManuallyTagged()).isTrue();
        org.mockito.Mockito.verify(financialAnalysisOrchestratorService).recomputeForMember(caller);
    }

    @Test
    void taggingAsEssentialAndLifestyleCreepUseTheirDedicatedSyntheticCategories() {
        Member caller = member("caller@example.com", null);
        Transaction essential = txn(caller, false);
        Transaction lifestyle = txn(caller, false);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByIdAndMemberId(essential.getId(), caller.getId())).thenReturn(Optional.of(essential));
        when(transactionRepository.findByIdAndMemberId(lifestyle.getId(), caller.getId())).thenReturn(Optional.of(lifestyle));

        controller().updateTag(essential.getId(), tagRequest("ESSENTIAL"), authentication);
        controller().updateTag(lifestyle.getId(), tagRequest("LIFESTYLE_CREEP"), authentication);

        assertThat(essential.getUserCategoryOverride()).isEqualTo(EssentialCategoryCatalog.MARKED_ESSENTIAL);
        assertThat(lifestyle.getUserCategoryOverride()).isEqualTo(LifestyleCategoryCatalog.MARKED_LIFESTYLE_CREEP);
    }

    @Test
    void taggingAsAutoClearsTheOverride() {
        Member caller = member("caller@example.com", null);
        Transaction owned = txn(caller, false);
        owned.setUserCategoryOverride("INVESTMENT");
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByIdAndMemberId(owned.getId(), caller.getId())).thenReturn(Optional.of(owned));

        TransactionResponse result = controller().updateTag(owned.getId(), tagRequest("AUTO"), authentication).getBody();

        assertThat(owned.getUserCategoryOverride()).isNull();
        assertThat(result.isManuallyTagged()).isFalse();
    }

    @Test
    void unrecognizedTagIsRejected() {
        Member caller = member("caller@example.com", null);
        Transaction owned = txn(caller, false);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByIdAndMemberId(owned.getId(), caller.getId())).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> controller().updateTag(owned.getId(), tagRequest("NOT_A_REAL_TAG"), authentication))
                .isInstanceOf(TransactionTagException.class);
    }

    @Test
    void cannotTagAnotherMembersTransaction() {
        Member caller = member("caller@example.com", null);
        Member other = member("other@example.com", null);
        Transaction othersTxn = txn(other, false);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByIdAndMemberId(othersTxn.getId(), caller.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().updateTag(othersTxn.getId(), tagRequest("INVESTMENT"), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void correctingDirectionFlipsTypeAndResetsCategorySoItCanBeRecategorizedFresh() {
        Member caller = member("caller@example.com", null);
        Transaction owned = txn(caller, false);
        owned.setCategory("SHOPPING");
        owned.setCategoryConfidence(TransactionCategorizationService.CONFIDENCE_MEDIUM);
        owned.setCategorySource(TransactionCategorizationService.SOURCE_FALLBACK_RULE);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByIdAndMemberId(owned.getId(), caller.getId())).thenReturn(Optional.of(owned));

        TransactionResponse result = controller().updateDirection(owned.getId(), directionRequest("CREDIT"), authentication).getBody();

        assertThat(owned.getType()).isEqualTo("CREDIT");
        assertThat(owned.getCategory()).isNull();
        assertThat(owned.getCategoryConfidence()).isNull();
        assertThat(owned.getCategorySource()).isNull();
        assertThat(result.getType()).isEqualTo("CREDIT");
        org.mockito.Mockito.verify(financialAnalysisOrchestratorService).recomputeForMember(caller);
    }

    @Test
    void cannotCorrectDirectionOnAnotherMembersTransaction() {
        Member caller = member("caller@example.com", null);
        Member other = member("other@example.com", null);
        Transaction othersTxn = txn(other, false);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByIdAndMemberId(othersTxn.getId(), caller.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().updateDirection(othersTxn.getId(), directionRequest("CREDIT"), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void settingALabelUpsertsByCounterpartyKeyAndReflectsItInTheResponse() {
        Member caller = member("caller@example.com", null);
        Transaction owned = txn(caller, false);
        owned.setNarration("UPI/402312345678/Local Oil Store/oilstore@icici/ICICI/Payment");
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByIdAndMemberId(owned.getId(), caller.getId())).thenReturn(Optional.of(owned));
        when(labelRepository.findByMemberIdAndCounterpartyKey(caller.getId(), "VPA:oilstore@icici"))
                .thenReturn(Optional.empty());
        when(labelRepository.findByMemberId(caller.getId())).thenReturn(List.of(
                MemberTransactionLabel.builder().member(caller).counterpartyKey("VPA:oilstore@icici").label("Oil").build()));

        TransactionResponse result = controller().updateLabel(owned.getId(), labelRequest("Oil"), authentication).getBody();

        org.mockito.Mockito.verify(labelRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                "VPA:oilstore@icici".equals(saved.getCounterpartyKey()) && "Oil".equals(saved.getLabel())));
        assertThat(result.getPersonalLabel()).isEqualTo("Oil");
    }

    @Test
    void everyTransactionSharingTheSameVpaPicksUpTheSameLabelOnRead() {
        Member caller = member("caller@example.com", null);
        Transaction first = txn(caller, false);
        first.setNarration("UPI/402312345678/Local Oil Store/oilstore@icici/ICICI/Payment");
        Transaction second = txn(caller, false);
        second.setNarration("UPI/509912345678/Local Oil Store/oilstore@icici/ICICI/Payment");
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(caller.getId()))
                .thenReturn(List.of(first, second));
        when(labelRepository.findByMemberId(caller.getId())).thenReturn(List.of(
                MemberTransactionLabel.builder().member(caller).counterpartyKey("VPA:oilstore@icici").label("Oil").build()));

        List<TransactionResponse> result = controller().listTransactions(authentication).getBody();

        assertThat(result).extracting(TransactionResponse::getPersonalLabel).containsExactly("Oil", "Oil");
    }

    @Test
    void blankLabelClearsTheExistingOverride() {
        Member caller = member("caller@example.com", null);
        Transaction owned = txn(caller, false);
        owned.setNarration("UPI/402312345678/Local Oil Store/oilstore@icici/ICICI/Payment");
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByIdAndMemberId(owned.getId(), caller.getId())).thenReturn(Optional.of(owned));

        controller().updateLabel(owned.getId(), labelRequest("  "), authentication);

        org.mockito.Mockito.verify(labelRepository).deleteByMemberIdAndCounterpartyKey(caller.getId(), "VPA:oilstore@icici");
    }

    @Test
    void cannotLabelAnotherMembersTransaction() {
        Member caller = member("caller@example.com", null);
        Member other = member("other@example.com", null);
        Transaction othersTxn = txn(other, false);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(transactionRepository.findByIdAndMemberId(othersTxn.getId(), caller.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().updateLabel(othersTxn.getId(), labelRequest("Oil"), authentication))
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
