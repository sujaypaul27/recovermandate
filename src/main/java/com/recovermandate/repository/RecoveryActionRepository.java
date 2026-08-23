package com.recovermandate.repository;

import com.recovermandate.entity.RecoveryAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecoveryActionRepository extends JpaRepository<RecoveryAction, Long> {
}
