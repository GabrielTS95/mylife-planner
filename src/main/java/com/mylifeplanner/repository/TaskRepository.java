package com.mylifeplanner.repository;
import com.mylifeplanner.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TaskRepository extends JpaRepository<Task, Long> {}
