package com.recovermandate.repository;

import com.recovermandate.entity.Plan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByRazorpayPlanId(String razorpayPlanId);
}
