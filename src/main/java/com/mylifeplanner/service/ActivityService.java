package com.mylifeplanner.service;

import com.mylifeplanner.model.Activity;
import com.mylifeplanner.repository.ActivityRepository;
import org.springframework.stereotype.Service;
import com.mylifeplanner.model.AppPage;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ActivityService {
    private final ActivityRepository repository;

    private final AccountService account;

    public ActivityService(ActivityRepository repository, AccountService account) {
        this.account = account;
        this.repository = repository;
    }

    public List<Activity> findAll() {
        if (!account.canAccess(AppPage.ACTIVITIES)) return List.of();
        return repository.findAllByUserId(account.currentUser().getId()).stream()
                .sorted(activityComparator())
                .toList();
    }

    public List<Activity> findByDateRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return findAll();
        }
        return findAll().stream()
                .filter(activity -> {
                    LocalDate date = activity.getActivityDate();
                    if (date == null) return false;
                    if (start != null && date.isBefore(start)) return false;
                    if (end != null && date.isAfter(end)) return false;
                    return true;
                })
                .toList();
    }

    public Activity save(Activity activity) {
        account.requireAccess(AppPage.ACTIVITIES);
        if (activity.getId() != null) findById(activity.getId());
        activity.setUser(account.currentUser());
        return repository.save(activity);
    }

    public Activity findById(Long id) {
        account.requireAccess(AppPage.ACTIVITIES);
        return repository.findByIdAndUserId(id, account.currentUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public void delete(Long id) {
        repository.delete(findById(id));
    }

    public Map<LocalDate, List<Activity>> findGroupedByWeek(LocalDate weekStart) {
        Map<LocalDate, List<Activity>> activitiesByDate = new LinkedHashMap<>();
        for (int day = 0; day < 7; day++) {
            activitiesByDate.put(weekStart.plusDays(day), new ArrayList<>());
        }

        LocalDate weekEnd = weekStart.plusDays(6);
        for (Activity activity : findAll()) {
            LocalDate date = activity.getActivityDate();
            if (date != null && !date.isBefore(weekStart) && !date.isAfter(weekEnd)) {
                activitiesByDate.get(date).add(activity);
            }
        }
        return activitiesByDate;
    }

    private Comparator<Activity> activityComparator() {
        return Comparator
                .comparing(Activity::getActivityDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Activity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Activity::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
