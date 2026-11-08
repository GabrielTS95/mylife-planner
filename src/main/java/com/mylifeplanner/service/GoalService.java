package com.mylifeplanner.service;

import com.mylifeplanner.model.Goal;
import com.mylifeplanner.repository.GoalRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class GoalService {
    private final GoalRepository repository;
    private final AccountService account;
    public GoalService(GoalRepository repository, AccountService account) { this.repository = repository; this.account = account; }
    public List<Goal> findAll() { return repository.findAll(); }
    public Goal save(Goal goal) { goal.setUser(account.getOwner()); return repository.save(goal); }
    public void delete(Long id) { repository.deleteById(id); }
}
