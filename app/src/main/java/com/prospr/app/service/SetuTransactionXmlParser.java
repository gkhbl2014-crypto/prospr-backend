package com.prospr.app.service;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.prospr.app.entity.Member;
import com.prospr.app.entity.Transaction;
import com.prospr.app.exception.SetuIntegrationException;

@Component
public class SetuTransactionXmlParser {

    private static final Logger log = LoggerFactory.getLogger(SetuTransactionXmlParser.class);

    public List<Transaction> parse(String xml, Member member, String maskedAccNumber, String sessionId, String consentId) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }

        try {
            DocumentBuilder builder = newSecureBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            document.getDocumentElement().normalize();

            Element accountElement = document.getDocumentElement();
            String accountRef = accountElement.getAttribute("linkedAccRef");
            String accountNumber = (maskedAccNumber != null && !maskedAccNumber.isBlank())
                    ? maskedAccNumber
                    : accountElement.getAttribute("maskedAccNumber");

            NodeList transactionNodes = document.getElementsByTagName("Transaction");
            List<Transaction> transactions = new ArrayList<>(transactionNodes.getLength());
            for (int i = 0; i < transactionNodes.getLength(); i++) {
                Element txnElement = (Element) transactionNodes.item(i);
                String txnId = txnElement.getAttribute("txnId");
                if (txnId == null || txnId.isBlank()) {
                    continue;
                }
                transactions.add(Transaction.builder()
                        .member(member)
                        .sessionId(sessionId)
                        .consentId(consentId)
                        .maskedAccountNumber(accountNumber)
                        .accountRef(accountRef)
                        .txnId(txnId)
                        .mode(txnElement.getAttribute("mode"))
                        .type(txnElement.getAttribute("type"))
                        .amount(parseDecimal(txnElement.getAttribute("amount")))
                        .transactionalBalance(parseDecimal(txnElement.getAttribute("transactionalBalance")))
                        .narration(txnElement.getAttribute("narration"))
                        .reference(txnElement.getAttribute("reference"))
                        .valueDate(parseDate(txnElement.getAttribute("valueDate")))
                        .transactionTimestamp(parseTimestamp(txnElement.getAttribute("transactionTimestamp")))
                        .build());
            }
            return transactions;
        } catch (Exception ex) {
            log.error("Failed to parse Setu transaction XML: {}", ex.getMessage(), ex);
            throw new SetuIntegrationException("Unable to parse transaction data", ex);
        }
    }

    private DocumentBuilder newSecureBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
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

    private OffsetDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
