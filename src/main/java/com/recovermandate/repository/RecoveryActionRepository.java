package com.recovermandate.repository;

import com.recovermandate.entity.RecoveryAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.recovermandate.entity.FailureClassification;
import java.util.Optional;

@Repository
public interface RecoveryActionRepository extends JpaRepository<RecoveryAction, Long> {
    long countByStatus(String status);
    Optional<RecoveryAction> findByFailureClassification(FailureClassification classification);
    Optional<RecoveryAction> findByFailureClassificationPaymentEvent(com.recovermandate.entity.PaymentEvent paymentEvent);
    org.springframework.data.domain.Page<RecoveryAction> findByStatus(String status, org.springframework.data.domain.Pageable pageable);
    java.util.List<RecoveryAction> findByStatus(String status);
    org.springframework.data.domain.Page<RecoveryAction> findAll(org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<RecoveryAction> findByApprovedAtIsNotNull(org.springframework.data.domain.Pageable pageable);
    java.util.List<RecoveryAction> findByApprovedAtIsNotNull();
}
