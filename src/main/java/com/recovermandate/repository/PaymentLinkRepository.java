package com.recovermandate.repository;

import com.recovermandate.entity.PaymentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentLinkRepository extends JpaRepository<PaymentLink, Long> {

    Optional<PaymentLink> findByRecoveryActionId(Long recoveryActionId);

    Optional<PaymentLink> findByRecoveryAction(com.recovermandate.entity.RecoveryAction recoveryAction);

    Optional<PaymentLink> findByRazorpayLinkId(String razorpayLinkId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(pl.amount), 0) FROM PaymentLink pl WHERE pl.status = :status")
    long sumAmountByStatus(@org.springframework.data.repository.query.Param("status") String status);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(pl.amount), 0) FROM PaymentLink pl WHERE pl.status = :status AND pl.isDemoData = :isDemoData")
    long sumAmountByStatusAndIsDemoData(@org.springframework.data.repository.query.Param("status") String status, @org.springframework.data.repository.query.Param("isDemoData") boolean isDemoData);
}
