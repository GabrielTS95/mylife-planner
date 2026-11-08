package com.mylifeplanner.service;

import com.mylifeplanner.model.Task;
import com.mylifeplanner.model.TaskStatus;
import com.mylifeplanner.repository.TaskRepository;
import org.springframework.stereotype.Service;
import com.mylifeplanner.model.AppPage;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskService {
    private final TaskRepository repository;

    private final AccountService account;

    public TaskService(TaskRepository repository, AccountService account) {
        this.account = account;
        this.repository = repository;
    }

    public List<Task> findAll() {
        if (!account.canAccess(AppPage.TASKS)) return List.of();
        return repository.findAllByUserId(account.currentUser().getId()).stream()
                .sorted(taskComparator())
                .toList();
    }

    public List<Task> findByDateRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return findAll();
        }
        return findAll().stream()
                .filter(task -> {
                    LocalDate due = task.getDueDate();
                    if (due == null) return false;
                    if (start != null && due.isBefore(start)) return false;
                    if (end != null && due.isAfter(end)) return false;
                    return true;
                })
                .toList();
    }

    public Task save(Task task) {
        account.requireAccess(AppPage.TASKS);
        if (task.getId() != null) findById(task.getId());
        task.setUser(account.currentUser());
        return repository.save(task);
    }

    public Task findById(Long id) {
        account.requireAccess(AppPage.TASKS);
        return repository.findByIdAndUserId(id, account.currentUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public void delete(Long id) {
        repository.delete(findById(id));
    }

    public long count() {
        return findAll().size();
    }

    public Map<TaskStatus, List<Task>> findAllGroupedByStatus() {
        return groupTasksByStatus(findAll());
    }

    public Map<TaskStatus, List<Task>> groupTasksByStatus(List<Task> taskList) {
        Map<TaskStatus, List<Task>> groupedTasks = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            groupedTasks.put(status, new ArrayList<>());
        }
        for (Task task : taskList) {
            TaskStatus status = task.getStatus() == null ? TaskStatus.POR_HACER : task.getStatus();
            groupedTasks.get(status).add(task);
        }
        return groupedTasks;
    }

    private Comparator<Task> taskComparator() {
        return Comparator
                .comparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(task -> task.getPriority() == null ? Integer.MAX_VALUE : task.getPriority().ordinal())
                .thenComparing(Task::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
