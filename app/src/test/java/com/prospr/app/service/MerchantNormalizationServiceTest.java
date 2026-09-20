package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.entity.MerchantAlias;
import com.prospr.app.repository.MerchantAliasRepository;

@ExtendWith(MockitoExtension.class)
class MerchantNormalizationServiceTest {

    @Mock
    private MerchantAliasRepository merchantAliasRepository;

    private final MerchantNormalizationRules fallbackRules = new MerchantNormalizationRules();
    private static final List<MerchantAlias> NO_ALIASES = List.of();

    private MerchantNormalizationService service() {
        return new MerchantNormalizationService(merchantAliasRepository, fallbackRules);
    }

    @Test
    void groupsSwiggyNarrationVariantsUnderOneCanonicalName() {
        MerchantNormalizationService service = service();

        assertThat(service.normalize("UPI/402312345678/SWIGGY/swiggy@icici/ICICI", NO_ALIASES)).contains("Swiggy");
        assertThat(service.normalize("SWIGGY INSTAMART PAYMENT", NO_ALIASES)).contains("Swiggy");
        assertThat(service.normalize("UPI-SWIGGY UPI-REF12345", NO_ALIASES)).contains("Swiggy");
    }

    @Test
    void groupsZeptoAndUberVariants() {
        MerchantNormalizationService service = service();

        assertThat(service.normalize("UPI/Zepto/539411833928/Payment from Ph", NO_ALIASES)).contains("Zepto");
        assertThat(service.normalize("UBER *TRIP HELP.UBER.COM", NO_ALIASES)).contains("Uber");
    }

    @Test
    void dbBackedAliasTakesPrecedenceOverFallbackList() {
        MerchantAlias alias = MerchantAlias.builder().rawKeyword("swiggy instamart").canonicalName("Swiggy Instamart Groceries").build();

        Optional<String> result = service().normalize("SWIGGY INSTAMART ORDER", List.of(alias));

        assertThat(result).contains("Swiggy Instamart Groceries");
    }

    @Test
    void unrecognizedNarrationReturnsEmpty() {
        assertThat(service().normalize("NEFT-N123-RAJESH KUMAR-HDFC0001234", NO_ALIASES)).isEmpty();
    }

    @Test
    void blankNarrationReturnsEmpty() {
        assertThat(service().normalize(null, NO_ALIASES)).isEmpty();
        assertThat(service().normalize("  ", NO_ALIASES)).isEmpty();
    }

    @Test
    void loadKnownAliasesReadsFromRepositoryExactlyOnce() {
        MerchantAlias alias = MerchantAlias.builder().rawKeyword("airtel").canonicalName("Airtel").build();
        when(merchantAliasRepository.findAll()).thenReturn(List.of(alias));

        List<MerchantAlias> loaded = service().loadKnownAliases();

        assertThat(loaded).containsExactly(alias);
    }
}
