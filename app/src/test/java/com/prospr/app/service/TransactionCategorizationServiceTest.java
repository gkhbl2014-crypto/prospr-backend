package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.entity.MerchantCategory;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.MerchantAliasRepository;
import com.prospr.app.repository.MerchantCategoryRepository;

@ExtendWith(MockitoExtension.class)
class TransactionCategorizationServiceTest {

    @Mock
    private MerchantCategoryRepository merchantCategoryRepository;
    @Mock
    private MerchantAliasRepository merchantAliasRepository;

    private final CategoryFallbackRules fallbackRules = new CategoryFallbackRules();
    private final MerchantNormalizationRules normalizationRules = new MerchantNormalizationRules();

    private TransactionCategorizationService service() {
        MerchantNormalizationService normalizationService =
                new MerchantNormalizationService(merchantAliasRepository, normalizationRules);
        return new TransactionCategorizationService(merchantCategoryRepository, fallbackRules, normalizationService);
    }

    private Transaction debit(String narration) {
        return Transaction.builder().type("DEBIT").narration(narration).amount(new BigDecimal("500")).build();
    }

    private Transaction credit(String narration) {
        return Transaction.builder().type("CREDIT").narration(narration).amount(new BigDecimal("50000")).build();
    }

    @Test
    void categorizesRealisticUpiNarrationAsFoodDeliveryWithHighConfidenceViaMerchantDb() {
        when(merchantAliasRepository.findAll()).thenReturn(List.of());
        MerchantCategory known = MerchantCategory.builder()
                .merchantKeyword("swiggy").category("FOOD_DELIVERY").subcategory("Delivery").build();
        when(merchantCategoryRepository.findAll()).thenReturn(List.of(known));

        Transaction txn = debit("UPI/402312345678/SWIGGY INSTAMART/swiggy@icici/ICICI/Payment");
        service().categorize(List.of(txn));

        assertThat(txn.getCategory()).isEqualTo("FOOD_DELIVERY");
        assertThat(txn.getCategoryConfidence()).isEqualTo(TransactionCategorizationService.CONFIDENCE_HIGH);
        assertThat(txn.getCategorySource()).isEqualTo(TransactionCategorizationService.SOURCE_MERCHANT_DB);
        assertThat(txn.getMerchantName()).isEqualTo("Swiggy");
    }

    @Test
    void fallbackBrandKeywordIsMediumConfidenceAndCategorized() {
        when(merchantAliasRepository.findAll()).thenReturn(List.of());
        when(merchantCategoryRepository.findAll()).thenReturn(List.of());

        Transaction txn = debit("IMPS/P2A/302012345678/AJIO/HDFC");
        service().categorize(List.of(txn));

        assertThat(txn.getCategory()).isEqualTo("SHOPPING");
        assertThat(txn.getCategoryConfidence()).isEqualTo(TransactionCategorizationService.CONFIDENCE_MEDIUM);
        assertThat(txn.getCategorySource()).isEqualTo(TransactionCategorizationService.SOURCE_FALLBACK_RULE);
    }

    @Test
    void genericWordKeywordIsLowConfidenceAndLeftUnclassified() {
        when(merchantAliasRepository.findAll()).thenReturn(List.of());
        when(merchantCategoryRepository.findAll()).thenReturn(List.of());

        Transaction txn = debit("POS PURCHASE AT LOCAL CAFE COUNTER");
        service().categorize(List.of(txn));

        assertThat(txn.getCategory()).isNull();
        assertThat(txn.getCategoryConfidence()).isEqualTo(TransactionCategorizationService.CONFIDENCE_LOW);
        assertThat(txn.getCategorySource()).isEqualTo(TransactionCategorizationService.SOURCE_NONE);
    }

    @Test
    void essentialKeywordsStayMediumConfidenceNotLow() {
        when(merchantAliasRepository.findAll()).thenReturn(List.of());
        when(merchantCategoryRepository.findAll()).thenReturn(List.of());

        Transaction rent = debit("NEFT-RENT-PAYMENT-LANDLORD");
        Transaction emi = debit("HDFC-EMI-DEDUCTION-REF1234");
        service().categorize(List.of(rent, emi));

        assertThat(rent.getCategory()).isEqualTo("RENT");
        assertThat(rent.getCategoryConfidence()).isEqualTo(TransactionCategorizationService.CONFIDENCE_MEDIUM);
        assertThat(emi.getCategory()).isEqualTo("EMI");
        assertThat(emi.getCategoryConfidence()).isEqualTo(TransactionCategorizationService.CONFIDENCE_MEDIUM);
    }

    @Test
    void salaryCreditIsCategorizedAsIncomeButOtherCreditsAreNot() {
        when(merchantAliasRepository.findAll()).thenReturn(List.of());
        when(merchantCategoryRepository.findAll()).thenReturn(List.of());

        Transaction salary = credit("NEFT-N123-ACME CORP-SALARY-AUG2026");
        Transaction refund = credit("UPI/REFUND/AMAZON/123456");
        service().categorize(List.of(salary, refund));

        assertThat(salary.getCategory()).isEqualTo("SALARY_INCOME");
        assertThat(refund.getCategory()).isNull();
    }

    @Test
    void transportationAndFuelAndEducationCategoriesAreRecognized() {
        when(merchantAliasRepository.findAll()).thenReturn(List.of());
        when(merchantCategoryRepository.findAll()).thenReturn(List.of());

        Transaction cab = debit("UBER *TRIP HELP.UBER.COM");
        Transaction fuel = debit("POS/INDIANOIL PETROL PUMP");
        Transaction edu = debit("NEFT-BYJUS-TUITION-FEE");
        service().categorize(List.of(cab, fuel, edu));

        assertThat(cab.getCategory()).isEqualTo("TRANSPORTATION");
        assertThat(fuel.getCategory()).isEqualTo("FUEL");
        assertThat(edu.getCategory()).isEqualTo("EDUCATION");
    }

    @Test
    void alreadyCategorizedTransactionIsSkipped() {
        Transaction txn = debit("Whatever");
        txn.setCategory("SHOPPING");

        int categorized = service().categorize(List.of(txn));

        assertThat(categorized).isZero();
        assertThat(txn.getCategory()).isEqualTo("SHOPPING");
    }
}
