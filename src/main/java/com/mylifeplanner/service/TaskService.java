package com.mylifeplanner.service;

import com.mylifeplanner.model.Task;
import com.mylifeplanner.model.TaskStatus;
import com.mylifeplanner.repository.TaskRepository;
import org.springframework.stereotype.Service;

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
        return repository.findAll().stream()
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
        task.setUser(account.getOwner());
        return repository.save(task);
    }

    public Task findById(Long id) {
        return repository.findById(id).orElseThrow();
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public long count() {
        return repository.count();
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
