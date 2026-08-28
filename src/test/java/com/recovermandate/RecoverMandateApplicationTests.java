package com.recovermandate;

import com.recovermandate.repository.AuditLogRepository;
import com.recovermandate.repository.CustomerRepository;
import com.recovermandate.repository.FailureClassificationRepository;
import com.recovermandate.repository.MerchantRepository;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.repository.PlanRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import com.recovermandate.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/recovermandate_test",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "razorpay.webhook.secret=test_secret"
})
class RecoverMandateApplicationTests {

    @MockBean
    private MerchantRepository merchantRepository;

    @MockBean
    private CustomerRepository customerRepository;

    @MockBean
    private PlanRepository planRepository;

    @MockBean
    private SubscriptionRepository subscriptionRepository;

    @MockBean
    private PaymentEventRepository paymentEventRepository;

    @MockBean
    private FailureClassificationRepository failureClassificationRepository;

    @MockBean
    private RecoveryActionRepository recoveryActionRepository;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private com.recovermandate.repository.PaymentLinkRepository paymentLinkRepository;

    @MockBean
    private com.recovermandate.repository.DispatchLogRepository dispatchLogRepository;

    @Test
    void contextLoads() {
    }
}
