package com.mylifeplanner.repository;
import com.mylifeplanner.model.FinanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FinanceTransactionRepository extends JpaRepository<FinanceTransaction, Long> {}
