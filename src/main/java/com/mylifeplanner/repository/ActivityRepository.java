package com.mylifeplanner.repository;
import com.mylifeplanner.model.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ActivityRepository extends JpaRepository<Activity, Long> {}
