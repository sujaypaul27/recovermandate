package com.recovermandate.repository;

import com.recovermandate.entity.DispatchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DispatchLogRepository extends JpaRepository<DispatchLog, Long> {

    List<DispatchLog> findByRecoveryActionId(Long recoveryActionId);
}
