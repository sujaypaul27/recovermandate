package com.recovermandate.repository;

import com.recovermandate.entity.FailureClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailureClassificationRepository extends JpaRepository<FailureClassification, Long> {
}
