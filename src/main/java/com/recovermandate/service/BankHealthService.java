package com.recovermandate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.BankHealthSnapshot;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.repository.BankHealthSnapshotRepository;
import com.recovermandate.repository.PaymentEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to monitor issuer bank health and compute real-time failure rate snapshots.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankHealthService {

    private final BankHealthSnapshotRepository bankHealthSnapshotRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private static final List<String> KNOWN_BANKS = List.of("HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "PNB", "YESB");

    /**
     * Computes failure rate snapshots for all active banks across the last 30 minutes.
     *
     * @return the list of created BankHealthSnapshot records
     */
    @Transactional
    public List<BankHealthSnapshot> computeHealthSnapshots() {
        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minus(30, ChronoUnit.MINUTES);

        List<PaymentEvent> recentEvents = paymentEventRepository.findByReceivedAtGreaterThanEqual(windowStart);
        Map<String, List<PaymentEvent>> eventsByBank = recentEvents.stream()
                .collect(Collectors.groupingBy(this::extractBankCode));

        // Ensure active known banks have at least default evaluation if events exist
        for (String bank : KNOWN_BANKS) {
            eventsByBank.putIfAbsent(bank, Collections.emptyList());
        }

        List<BankHealthSnapshot> snapshots = new ArrayList<>();

        for (Map.Entry<String, List<PaymentEvent>> entry : eventsByBank.entrySet()) {
            String bankCode = entry.getKey();
            List<PaymentEvent> bankEvents = entry.getValue();

            int total = bankEvents.size();
            int failed = (int) bankEvents.stream()
                    .filter(e -> "payment.failed".equalsIgnoreCase(e.getEventType()))
                    .count();

            double failureRate = total > 0 ? (double) failed / total : 0.0;
            String status = determineStatus(failureRate, total);

            BankHealthSnapshot snapshot = BankHealthSnapshot.builder()
                    .bankCode(bankCode)
                    .windowStart(windowStart)
                    .windowEnd(windowEnd)
                    .totalAttempts(total)
                    .failedAttempts(failed)
                    .failureRate(failureRate)
                    .status(status)
                    .createdAt(windowEnd)
                    .build();

            snapshots.add(bankHealthSnapshotRepository.save(snapshot));
        }

        log.info("Computed {} bank health snapshots for window {} to {}", snapshots.size(), windowStart, windowEnd);

        auditService.log(
                "SYSTEM",
                0L,
                "BANK_HEALTH_COMPUTED",
                "SYSTEM",
                String.format("Computed %d bank health snapshots over %d recent events.", snapshots.size(), recentEvents.size())
        );

        return snapshots;
    }

    /**
     * Gets the latest operational status for an issuer bank (HEALTHY, DEGRADED, DOWN).
     */
    public String getBankHealth(String bankCode) {
        if (bankCode == null || bankCode.isBlank()) {
            return "HEALTHY";
        }

        return bankHealthSnapshotRepository.findTopByBankCodeOrderByCreatedAtDesc(bankCode)
                .map(BankHealthSnapshot::getStatus)
                .orElse("HEALTHY");
    }

    /**
     * Extracts bank code from PaymentEvent JSON payload or error descriptions.
     */
    public String extractBankCode(PaymentEvent event) {
        if (event == null) {
            return "UNKNOWN";
        }

        if (event.getRawPayload() != null && !event.getRawPayload().isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(event.getRawPayload());
                JsonNode payment = root.path("payload").path("payment").path("entity");
                if (payment.hasNonNull("bank")) {
                    return payment.get("bank").asText().toUpperCase(Locale.ROOT);
                }
                if (root.hasNonNull("bank")) {
                    return root.get("bank").asText().toUpperCase(Locale.ROOT);
                }
            } catch (Exception e) {
                log.debug("Could not parse bank code from raw payload: {}", e.getMessage());
            }
        }

        String reason = event.getFailureReasonCode();
        if (reason != null) {
            String upper = reason.toUpperCase(Locale.ROOT);
            for (String bank : KNOWN_BANKS) {
                if (upper.contains(bank)) {
                    return bank;
                }
            }
        }

        return "UNKNOWN";
    }

    /**
     * Evaluates health status from failure rate.
     */
    public String determineStatus(double failureRate, int totalAttempts) {
        if (totalAttempts == 0) {
            return "HEALTHY";
        }
        if (failureRate >= 0.80) {
            return "DOWN";
        }
        if (failureRate >= 0.40) {
            return "DEGRADED";
        }
        return "HEALTHY";
    }

    /**
     * Returns only the single latest bank health snapshot per distinct bank code.
     */
    public List<BankHealthSnapshot> getLatestSnapshots() {
        List<String> bankCodes = bankHealthSnapshotRepository.findDistinctBankCodes();
        if (bankCodes == null || bankCodes.isEmpty()) {
            bankCodes = KNOWN_BANKS;
        } else {
            Set<String> allBanks = new LinkedHashSet<>(bankCodes);
            allBanks.addAll(KNOWN_BANKS);
            bankCodes = new ArrayList<>(allBanks);
        }

        List<BankHealthSnapshot> latestSnapshots = new ArrayList<>();
        for (String bankCode : bankCodes) {
            bankHealthSnapshotRepository.findTopByBankCodeOrderByCreatedAtDesc(bankCode)
                    .ifPresent(latestSnapshots::add);
        }
        return latestSnapshots;
    }
}
