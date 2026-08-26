package com.recovermandate.repository;

import com.recovermandate.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a " +
           "WHERE (:entityType IS NULL OR a.entityType = :entityType) " +
           "AND (:actor IS NULL OR a.actor = :actor) " +
           "AND (cast(:startDate as timestamp) IS NULL OR a.createdAt >= :startDate) " +
           "AND (cast(:endDate as timestamp) IS NULL OR a.createdAt <= :endDate)")
    Page<AuditLog> findByFilters(
            @Param("entityType") String entityType,
            @Param("actor") String actor,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );
}
