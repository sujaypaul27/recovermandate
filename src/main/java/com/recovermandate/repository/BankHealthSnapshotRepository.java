package com.recovermandate.repository;

import com.recovermandate.entity.BankHealthSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankHealthSnapshotRepository extends JpaRepository<BankHealthSnapshot, Long> {

    Optional<BankHealthSnapshot> findTopByBankCodeOrderByCreatedAtDesc(String bankCode);

    List<BankHealthSnapshot> findAllByOrderByCreatedAtDesc();

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT b.bankCode FROM BankHealthSnapshot b")
    List<String> findDistinctBankCodes();
}
