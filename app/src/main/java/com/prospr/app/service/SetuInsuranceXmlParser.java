package com.prospr.app.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.prospr.app.entity.Insurance;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.SetuIntegrationException;

@Component
public class SetuInsuranceXmlParser {

    private static final Logger log = LoggerFactory.getLogger(SetuInsuranceXmlParser.class);

    public List<Insurance> parse(String xml, Member member, String maskedAccNumber, String sessionId, String consentId) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }

        try {
            Document document = SetuXmlUtils.parse(xml);

            Element accountElement = document.getDocumentElement();
            String accountRef = accountElement.getAttribute("linkedAccRef");
            String maskedPolicyNumber = (maskedAccNumber != null && !maskedAccNumber.isBlank())
                    ? maskedAccNumber
                    : accountElement.getAttribute("maskedPolicyNumber");
            String nomineeName = extractNomineeName(document);

            NodeList summaryNodes = document.getElementsByTagName("Summary");
            List<Insurance> policies = new ArrayList<>(summaryNodes.getLength());
            for (int i = 0; i < summaryNodes.getLength(); i++) {
                Element summary = (Element) summaryNodes.item(i);
                String policyNumber = summary.getAttribute("policyNumber");
                if (policyNumber == null || policyNumber.isBlank()) {
                    continue;
                }
                policies.add(Insurance.builder()
                        .member(member)
                        .sessionId(sessionId)
                        .consentId(consentId)
                        .accountRef(accountRef)
                        .maskedPolicyNumber(maskedPolicyNumber)
                        .insuranceType(blankToNull(summary.getAttribute("insuranceType")))
                        .policyNumber(policyNumber)
                        .insurerName(blankToNull(accountElement.getAttribute("insurerName")))
                        .policyName(blankToNull(summary.getAttribute("policyName")))
                        .sumAssured(parseDecimal(summary.getAttribute("sumAssured")))
                        .premiumAmount(parseDecimal(summary.getAttribute("premiumAmount")))
                        .premiumFrequency(blankToNull(summary.getAttribute("premiumFrequency")))
                        .policyStartDate(parseDate(summary.getAttribute("policyStartDate")))
                        .policyEndDate(parseDate(summary.getAttribute("policyExpiryDate")))
                        .maturityDate(parseDate(summary.getAttribute("maturityDate")))
                        .nextPremiumDueDate(parseDate(summary.getAttribute("nextPremiumDueDate")))
                        .policyStatus(blankToNull(summary.getAttribute("policyStatus")))
                        .nomineeName(nomineeName)
                        .lastUpdated(LocalDateTime.now())
                        .build());
            }
            return policies;
        } catch (Exception ex) {
            log.error("Failed to parse Setu insurance XML: {}", ex.getMessage(), ex);
            throw new SetuIntegrationException("Unable to parse insurance data", ex);
        }
    }

    private String extractNomineeName(Document document) {
        NodeList nomineeNodes = document.getElementsByTagName("Nominee");
        if (nomineeNodes.getLength() == 0) {
            return null;
        }
        return blankToNull(((Element) nomineeNodes.item(0)).getAttribute("name"));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
