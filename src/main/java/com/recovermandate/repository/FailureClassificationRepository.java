package com.recovermandate.repository;

import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailureClassificationRepository extends JpaRepository<FailureClassification, Long> {

    Optional<FailureClassification> findByPaymentEvent(PaymentEvent paymentEvent);

    @org.springframework.data.jpa.repository.Query("SELECT fc.category, COUNT(fc) FROM FailureClassification fc GROUP BY fc.category")
    java.util.List<Object[]> countByCategory();
}
