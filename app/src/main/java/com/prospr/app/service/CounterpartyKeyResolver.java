package com.prospr.app.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.prospr.app.entity.Transaction;

/**
 * Derives a stable "who was this money exchanged with" key for a transaction, used to group personal
 * labels ({@link MemberTransactionLabel}) so one label ("Oil") applies to every past and future
 * transaction to the same counterparty, not just the one the user happened to label.
 *
 * <p>Preference order:
 * <ol>
 *   <li>A UPI VPA found anywhere in the narration - the most reliable identifier a recurring UPI
 *       payee has, when the bank prints one.
 *   <li>The first "real" (non-reference-number, non-bank-code) segment right after {@code UPI/} in
 *       narrations with no VPA - real narration layout varies: {@code UPI/<payee>/<ref>/...} (e.g.
 *       {@code UPI/RAMESH THARUN/YESB/15009}, {@code UPI/Zepto/539411833928/Payment from Ph}) puts
 *       the payee first, unlike the VPA-bearing {@code UPI/<ref>/<payee>/<vpa>/<bank>/...} layout.
 *       Deliberately independent of {@link TransactionCategorizationService}'s own (positionally
 *       fixed, index-2) merchant extraction, which mis-derives a 4-5 letter bank code like "YESB" as
 *       the merchant name for exactly this narration shape - re-deriving here directly from the raw
 *       narration means a bad merchantName can never poison every future label lookup.
 *   <li>The already-extracted {@link Transaction#getMerchantName()}, for non-UPI narrations.
 *   <li>The raw narration verbatim, as a last resort (exact-match only, so at least the one
 *       transaction can still be labeled).
 * </ol>
 * Never throws; empty only when the transaction has neither a narration nor a merchant name at all.
 */
@Component
public class CounterpartyKeyResolver {

    private static final Pattern VPA_PATTERN = Pattern.compile("[A-Za-z0-9.\\-_]{2,}@[A-Za-z]{2,}");
    private static final int MAX_BANK_CODE_LENGTH = 5;

    public Optional<String> resolveKey(Transaction txn) {
        String narration = txn.getNarration();
        if (narration != null) {
            Matcher matcher = VPA_PATTERN.matcher(narration);
            if (matcher.find()) {
                return Optional.of("VPA:" + matcher.group().toLowerCase(Locale.ROOT));
            }
        }
        Optional<String> payeeName = extractUpiPayeeName(narration);
        if (payeeName.isPresent()) {
            return Optional.of("PAYEE:" + payeeName.get());
        }
        if (txn.getMerchantName() != null && !txn.getMerchantName().isBlank()) {
            return Optional.of("MERCHANT:" + txn.getMerchantName().trim().toUpperCase(Locale.ROOT));
        }
        if (narration != null && !narration.isBlank()) {
            return Optional.of("NARRATION:" + narration.trim().toUpperCase(Locale.ROOT));
        }
        return Optional.empty();
    }

    private Optional<String> extractUpiPayeeName(String narration) {
        if (narration == null) {
            return Optional.empty();
        }
        String trimmed = narration.trim();
        if (!trimmed.toUpperCase(Locale.ROOT).startsWith("UPI")) {
            return Optional.empty();
        }
        String[] parts = trimmed.split("/");
        for (int i = 1; i < parts.length; i++) {
            String segment = parts[i].trim();
            if (isUsablePayeeSegment(segment)) {
                return Optional.of(segment.toUpperCase(Locale.ROOT));
            }
        }
        return Optional.empty();
    }

    /** Rejects a pure reference number and a short all-caps alphabetic token (a bank code guess, e.g.
     *  "YESB"/"ICIC"/"HDFC") - everything else is treated as a plausible payee name/merchant. */
    private boolean isUsablePayeeSegment(String segment) {
        if (segment.isEmpty()) {
            return false;
        }
        if (segment.chars().allMatch(Character::isDigit)) {
            return false;
        }
        boolean looksLikeBankCode = segment.length() <= MAX_BANK_CODE_LENGTH
                && segment.chars().allMatch(Character::isLetter)
                && segment.equals(segment.toUpperCase(Locale.ROOT));
        return !looksLikeBankCode;
    }
}
