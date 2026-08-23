package com.prospr.app.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.prospr.app.entity.Member;
import com.prospr.app.entity.MutualFundHolding;
import com.prospr.app.exception.SetuIntegrationException;

@Component
public class SetuMutualFundXmlParser {

    private static final Logger log = LoggerFactory.getLogger(SetuMutualFundXmlParser.class);

    public List<MutualFundHolding> parse(String xml, Member member, String maskedAccNumber, String sessionId, String consentId) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }

        try {
            Document document = SetuXmlUtils.parse(xml);

            Element accountElement = document.getDocumentElement();
            String accountRef = accountElement.getAttribute("linkedAccRef");
            String accountNumber = (maskedAccNumber != null && !maskedAccNumber.isBlank())
                    ? maskedAccNumber
                    : accountElement.getAttribute("maskedDematID");

            BigDecimal costValue = null;
            BigDecimal currentValue = null;
            NodeList summaryNodes = document.getElementsByTagName("Summary");
            if (summaryNodes.getLength() > 0) {
                Element summary = (Element) summaryNodes.item(0);
                costValue = parseDecimal(summary.getAttribute("costValue"));
                currentValue = parseDecimal(summary.getAttribute("currentValue"));
            }

            NodeList holdingNodes = document.getElementsByTagName("Holding");
            List<MutualFundHolding> holdings = new ArrayList<>(holdingNodes.getLength());
            for (int i = 0; i < holdingNodes.getLength(); i++) {
                Element holding = (Element) holdingNodes.item(i);
                String isin = holding.getAttribute("isin");
                if (isin == null || isin.isBlank()) {
                    continue;
                }
                holdings.add(MutualFundHolding.builder()
                        .member(member)
                        .sessionId(sessionId)
                        .consentId(consentId)
                        .accountRef(accountRef)
                        .maskedAccountNumber(accountNumber)
                        .costValue(costValue)
                        .currentValue(currentValue)
                        .amc(blankToNull(holding.getAttribute("amc")))
                        .registrar(blankToNull(holding.getAttribute("registrar")))
                        .schemeCode(blankToNull(holding.getAttribute("schemeCode")))
                        .schemeOption(blankToNull(holding.getAttribute("schemeOption")))
                        .isin(isin)
                        .isinDescription(blankToNull(holding.getAttribute("isinDescription")))
                        .ucc(blankToNull(holding.getAttribute("ucc")))
                        .folioNo(blankToNull(holding.getAttribute("folioNo")))
                        .closingUnits(parseDecimal(holding.getAttribute("closingUnits")))
                        .lienUnits(parseDecimal(holding.getAttribute("lienUnits")))
                        .nav(parseDecimal(holding.getAttribute("nav")))
                        .navDate(parseDate(holding.getAttribute("navDate")))
                        .lockinUnits(parseDecimal(holding.getAttribute("lockinUnits")))
                        .build());
            }
            return holdings;
        } catch (Exception ex) {
            log.error("Failed to parse Setu mutual fund XML: {}", ex.getMessage(), ex);
            throw new SetuIntegrationException("Unable to parse mutual fund data", ex);
        }
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
