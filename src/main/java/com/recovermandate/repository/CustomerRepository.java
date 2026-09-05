package com.recovermandate.repository;

import com.recovermandate.entity.Customer;
import com.recovermandate.entity.Merchant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByRazorpayCustomerId(String razorpayCustomerId);

    Optional<Customer> findFirstByRazorpayCustomerIdOrderByIdDesc(String razorpayCustomerId);

    List<Customer> findByEmail(String email);

    List<Customer> findByEmailOrderByIdDesc(String email);

    Optional<Customer> findFirstByEmailOrderByIdDesc(String email);

    List<Customer> findByMerchantAndEmail(Merchant merchant, String email);

    List<Customer> findByMerchantAndEmailOrderByIdDesc(Merchant merchant, String email);

    Optional<Customer> findFirstByMerchantAndEmailOrderByIdDesc(Merchant merchant, String email);
}


