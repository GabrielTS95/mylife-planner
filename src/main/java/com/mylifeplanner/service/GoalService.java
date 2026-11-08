package com.mylifeplanner.service;

import com.mylifeplanner.model.Goal;
import com.mylifeplanner.model.AppPage;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.mylifeplanner.repository.GoalRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class GoalService {
    private final GoalRepository repository;
    private final AccountService account;
    public GoalService(GoalRepository repository, AccountService account) { this.repository = repository; this.account = account; }
    public List<Goal> findAll() {
        return account.canAccess(AppPage.GOALS) ? repository.findAllByUserId(account.currentUser().getId()) : List.of();
    }
    public Goal findById(Long id) {
        account.requireAccess(AppPage.GOALS);
        return repository.findByIdAndUserId(id, account.currentUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    public Goal save(Goal goal) {
        account.requireAccess(AppPage.GOALS);
        if (goal.getId() != null) findById(goal.getId());
        goal.setUser(account.currentUser());
        return repository.save(goal);
    }
    public void delete(Long id) { repository.delete(findById(id)); }
}
