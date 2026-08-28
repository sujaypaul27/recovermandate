package com.recovermandate.repository;

import com.recovermandate.entity.PaymentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentLinkRepository extends JpaRepository<PaymentLink, Long> {

    Optional<PaymentLink> findByRecoveryActionId(Long recoveryActionId);

    Optional<PaymentLink> findByRazorpayLinkId(String razorpayLinkId);
}
