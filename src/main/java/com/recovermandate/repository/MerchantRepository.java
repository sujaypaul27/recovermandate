package com.recovermandate.repository;

import com.recovermandate.entity.Merchant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByRazorpayAccountRef(String razorpayAccountRef);
}
