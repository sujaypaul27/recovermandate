package com.recovermandate.repository;

import com.recovermandate.entity.PaymentEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    Optional<PaymentEvent> findByRazorpayPaymentId(String razorpayPaymentId);
}
