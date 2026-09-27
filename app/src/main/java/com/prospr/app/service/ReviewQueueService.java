package com.prospr.app.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.prospr.app.config.ClassificationProperties;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberTransactionLabel;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.MemberTransactionLabelRepository;
import com.prospr.app.repository.TransactionRepository;

/**
 * Builds the "N transactions may need your help" queue: groups a member's still-unresolved P2P/UNKNOWN
 * transactions by counterparty (never per-transaction - answering one card resolves every transaction
 * to that counterparty at once) and ranks groups by how much they actually matter, so a member with
 * hundreds of transactions is asked about a handful, not hundreds. "Unresolved" means categorization
 * couldn't assign a category AND the member hasn't already answered a purpose for this counterparty.
 */
@Service
public class ReviewQueueService {

    public record ReviewQueueEntry(String counterpartyKey, String displayName, int transactionCount,
                                    BigDecimal totalAmount, LocalDate earliestDate, LocalDate latestDate,
                                    PayeePatternService.Evidence evidence) {
    }

    private final TransactionRepository transactionRepository;
    private final MemberTransactionLabelRepository labelRepository;
    private final CounterpartyKeyResolver counterpartyKeyResolver;
    private final PayeePatternService patternService;
    private final ClassificationProperties properties;

    public ReviewQueueService(TransactionRepository transactionRepository,
                               MemberTransactionLabelRepository labelRepository,
                               CounterpartyKeyResolver counterpartyKeyResolver,
                               PayeePatternService patternService,
                               ClassificationProperties properties) {
        this.transactionRepository = transactionRepository;
        this.labelRepository = labelRepository;
        this.counterpartyKeyResolver = counterpartyKeyResolver;
        this.patternService = patternService;
        this.properties = properties;
    }

    public List<ReviewQueueEntry> buildQueue(Member member) {
        Set<String> resolvedCounterpartyKeys = labelRepository.findByMemberId(member.getId()).stream()
                .filter(label -> label.getPurpose() != null)
                .map(MemberTransactionLabel::getCounterpartyKey)
                .collect(Collectors.toSet());

        List<Transaction> unresolved = transactionRepository
                .findByMemberIdOrderByTransactionTimestampDesc(member.getId()).stream()
                .filter(txn -> txn.getAmount() != null)
                .filter(txn -> txn.getEffectiveCategory() == null)
                .filter(txn -> CounterpartyTypeClassifier.P2P.equals(txn.getCounterpartyType())
                        || CounterpartyTypeClassifier.UNKNOWN.equals(txn.getCounterpartyType()))
                .toList();

        Map<String, List<Transaction>> grouped = new HashMap<>();
        for (Transaction txn : unresolved) {
            counterpartyKeyResolver.resolveKey(txn)
                    .filter(key -> !resolvedCounterpartyKeys.contains(key))
                    .ifPresent(key -> grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(txn));
        }

        List<ReviewQueueEntry> entries = new ArrayList<>();
        BigDecimal minTotal = BigDecimal.valueOf(properties.getReviewQueueMinTotalAmountInr());
        for (Map.Entry<String, List<Transaction>> group : grouped.entrySet()) {
            List<Transaction> transactions = group.getValue();
            BigDecimal total = transactions.stream()
                    .map(txn -> txn.getAmount().abs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(minTotal) < 0) {
                continue;
            }
            List<LocalDate> dates = transactions.stream().map(this::resolveDate).filter(java.util.Objects::nonNull).toList();
            entries.add(new ReviewQueueEntry(
                    group.getKey(),
                    counterpartyKeyResolver.toDisplayName(group.getKey()),
                    transactions.size(),
                    total,
                    dates.stream().min(LocalDate::compareTo).orElse(null),
                    dates.stream().max(LocalDate::compareTo).orElse(null),
                    patternService.evaluate(transactions)));
        }

        entries.sort(Comparator.comparing(ReviewQueueEntry::totalAmount).reversed());
        int maxResults = properties.getReviewQueueMaxResults();
        return entries.size() > maxResults ? entries.subList(0, maxResults) : entries;
    }

    private LocalDate resolveDate(Transaction txn) {
        if (txn.getValueDate() != null) {
            return txn.getValueDate();
        }
        return txn.getTransactionTimestamp() != null ? txn.getTransactionTimestamp().toLocalDate() : null;
    }
}
