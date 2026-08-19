package com.prospr.app.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.prospr.app.config.SetuProperties;
import com.prospr.app.dto.setu.SetuSessionRequest;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.Transaction;
import com.prospr.app.exception.SetuIntegrationException;
import com.prospr.app.repository.TransactionRepository;

@Service
public class SetuSessionService {

    private static final Logger log = LoggerFactory.getLogger(SetuSessionService.class);
    private static final String COMPLETED_STATUS = "COMPLETED";

    private final WebClient webClient;
    private final SetuProperties properties;
    private final SetuAuthenticationService authenticationService;
    private final SetuTransactionXmlParser xmlParser;
    private final TransactionRepository transactionRepository;

    public SetuSessionService(WebClient webClient, SetuProperties properties,
                               SetuAuthenticationService authenticationService,
                               SetuTransactionXmlParser xmlParser,
                               TransactionRepository transactionRepository) {
        this.webClient = webClient;
        this.properties = properties;
        this.authenticationService = authenticationService;
        this.xmlParser = xmlParser;
        this.transactionRepository = transactionRepository;
    }

    public com.prospr.app.dto.response.SetuSessionResponse createSession(String consentId) {
        if (consentId == null || consentId.isBlank()) {
            throw new SetuIntegrationException("consentId is required to create a Setu session");
        }

        SetuSessionRequest request = SetuSessionRequest.builder()
                .dataRange(SetuSessionRequest.DataRange.builder()
                        .from(properties.getDataRange().getFrom())
                        .to(properties.getDataRange().getTo())
                        .build())
                .consentId(consentId)
                .format(properties.getFormat())
                .build();

        log.info("Creating Setu session for consent '{}'", consentId);
        try {
            com.prospr.app.dto.setu.SetuSessionResponse response = webClient.post()
                    .uri(properties.getSessionUrl())
                    .headers(headers -> {
                        headers.setBearerAuth(authenticationService.obtainBearerToken());
                        headers.set("x-product-instance-id", properties.getProductInstanceId());
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(com.prospr.app.dto.setu.SetuSessionResponse.class)
                    .block();

            if (response == null || response.getId() == null || response.getId().isBlank()) {
                throw new SetuIntegrationException("Setu session response did not contain a session id");
            }
            log.info("Setu session created with id '{}' and status '{}'", response.getId(), response.getStatus());
            return com.prospr.app.dto.response.SetuSessionResponse.builder()
                    .id(response.getId())
                    .consentId(response.getConsentId() == null ? consentId : response.getConsentId())
                    .status(response.getStatus())
                    .build();
        } catch (SetuIntegrationException ex) {
            log.error("Setu session creation failed: {}", ex.getMessage());
            throw ex;
        } catch (WebClientResponseException ex) {
            log.error("Setu session creation failed with status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new SetuIntegrationException("Setu session creation failed: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("Setu session creation request failed: {}", ex.getMessage(), ex);
            throw new SetuIntegrationException("Unable to create Setu session", ex);
        }
    }

    public com.prospr.app.dto.setu.SetuSessionResponse fetchSession(String sessionId, Member member) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new SetuIntegrationException("sessionId is required to fetch a Setu session");
        }

        log.info("Fetching Setu session '{}'", sessionId);
        try {
            com.prospr.app.dto.setu.SetuSessionResponse response = webClient.get()
                    .uri(properties.getSessionUrl() + "/" + sessionId)
                    .headers(headers -> {
                        headers.setBearerAuth(authenticationService.obtainBearerToken());
                        headers.set("x-product-instance-id", properties.getProductInstanceId());
                    })
                    .retrieve()
                    .bodyToMono(com.prospr.app.dto.setu.SetuSessionResponse.class)
                    .block();

            if (response == null) {
                throw new SetuIntegrationException("Setu session fetch returned an empty response");
            }
            log.info("Setu session '{}' has status '{}'", sessionId, response.getStatus());
            logAccountDataPresence(sessionId, response);

            if (COMPLETED_STATUS.equalsIgnoreCase(response.getStatus())) {
                persistTransactions(response, member);
            }
            return response;
        } catch (SetuIntegrationException ex) {
            log.error("Setu session fetch failed: {}", ex.getMessage());
            throw ex;
        } catch (WebClientResponseException ex) {
            log.error("Setu session fetch failed with status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new SetuIntegrationException("Setu session fetch failed: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("Setu session fetch request failed: {}", ex.getMessage(), ex);
            throw new SetuIntegrationException("Unable to fetch Setu session", ex);
        }
    }

    private void logAccountDataPresence(String sessionId, com.prospr.app.dto.setu.SetuSessionResponse session) {
        if (session.getFips() == null) {
            log.debug("Setu session '{}' fips: NULL", sessionId);
            return;
        }
        for (com.prospr.app.dto.setu.SetuSessionResponse.Fip fip : session.getFips()) {
            if (fip.getAccounts() == null) {
                continue;
            }
            for (com.prospr.app.dto.setu.SetuSessionResponse.Account account : fip.getAccounts()) {
                boolean dataPresent = account.getData() != null && account.getData().getXml() != null;
                int xmlLength = dataPresent ? account.getData().getXml().length() : 0;
                log.debug("Setu session '{}' account '{}' data present: {} (xmlLength={})",
                        sessionId, account.getMaskedAccNumber(), dataPresent, xmlLength);
            }
        }
    }

    private void persistTransactions(com.prospr.app.dto.setu.SetuSessionResponse session, Member member) {
        if (transactionRepository.existsBySessionId(session.getId())) {
            log.info("Transactions for session '{}' already persisted; skipping", session.getId());
            return;
        }
        if (session.getFips() == null) {
            return;
        }

        List<Transaction> transactions = new ArrayList<>();
        for (com.prospr.app.dto.setu.SetuSessionResponse.Fip fip : session.getFips()) {
            if (fip.getAccounts() == null) {
                continue;
            }
            for (com.prospr.app.dto.setu.SetuSessionResponse.Account account : fip.getAccounts()) {
                if (account.getData() == null || account.getData().getXml() == null) {
                    continue;
                }
                try {
                    transactions.addAll(xmlParser.parse(account.getData().getXml(), member,
                            account.getMaskedAccNumber(), session.getId(), session.getConsentId()));
                } catch (SetuIntegrationException ex) {
                    log.error("Skipping account '{}' for session '{}': {}",
                            account.getMaskedAccNumber(), session.getId(), ex.getMessage());
                }
            }
        }

        if (!transactions.isEmpty()) {
            transactionRepository.saveAll(transactions);
            log.info("Persisted {} transactions for member '{}' from session '{}'",
                    transactions.size(), member.getId(), session.getId());
        }
    }
}
