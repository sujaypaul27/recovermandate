package com.recovermandate.repository;

import com.recovermandate.entity.RetrySchedule;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RetryScheduleRepository extends JpaRepository<RetrySchedule, Long> {

    List<RetrySchedule> findByResultAndScheduledAtLessThanEqual(String result, Instant scheduledAt, Pageable pageable);

    List<RetrySchedule> findByPaymentEventId(Long paymentEventId);

    long countByResult(String result);
}
