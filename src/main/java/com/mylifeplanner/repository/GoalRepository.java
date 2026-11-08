package com.mylifeplanner.repository;
import com.mylifeplanner.model.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
public interface GoalRepository extends JpaRepository<Goal, Long> {}
