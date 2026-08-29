package com.recovermandate.repository;

import com.recovermandate.entity.PaymentEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    Optional<PaymentEvent> findByRazorpayPaymentId(String razorpayPaymentId);

    long countByEventType(String eventType);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentEvent p WHERE p.eventType = :eventType")
    long sumAmountByEventType(@org.springframework.data.repository.query.Param("eventType") String eventType);

    @org.springframework.data.jpa.repository.Query(
        "SELECT p FROM PaymentEvent p " +
        "LEFT JOIN FailureClassification fc ON fc.paymentEvent = p " +
        "LEFT JOIN RecoveryAction ra ON ra.failureClassification = fc " +
        "WHERE (:category IS NULL OR fc.category = :category) " +
        "AND (:status IS NULL OR ra.status = :status)"
    )
    org.springframework.data.domain.Page<PaymentEvent> findByFilters(@org.springframework.data.repository.query.Param("category") String category, @org.springframework.data.repository.query.Param("status") String status, org.springframework.data.domain.Pageable pageable);

    java.util.List<PaymentEvent> findByReceivedAtGreaterThanEqual(java.time.Instant since);

    @org.springframework.data.jpa.repository.Query("SELECT fc.category, COUNT(fc) FROM FailureClassification fc GROUP BY fc.category")
    java.util.List<Object[]> countFailuresByCategory();

    @org.springframework.data.jpa.repository.Query("SELECT p FROM PaymentEvent p WHERE LOWER(p.razorpayPaymentId) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.eventType) LIKE LOWER(CONCAT('%', :query, '%'))")
    java.util.List<PaymentEvent> searchEvents(@org.springframework.data.repository.query.Param("query") String query, org.springframework.data.domain.Pageable pageable);
}
