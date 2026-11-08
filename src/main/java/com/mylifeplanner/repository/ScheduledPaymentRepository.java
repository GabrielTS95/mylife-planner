package com.mylifeplanner.repository;

import com.mylifeplanner.model.ScheduledPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ScheduledPaymentRepository extends UserOwnedRepository<ScheduledPayment> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ScheduledPayment p where p.id = :id and p.user.id = :userId")
    Optional<ScheduledPayment> lockOwned(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByTransactionIdAndUserId(Long transactionId, Long userId);
}
