package com.mylifeplanner.repository;

import com.mylifeplanner.model.FinanceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceCategoryRepository extends JpaRepository<FinanceCategory, Long> {
}
