package com.recovermandate.repository;

import com.recovermandate.entity.WebhookDlq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookDlqRepository extends JpaRepository<WebhookDlq, Long> {

    List<WebhookDlq> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);
}
