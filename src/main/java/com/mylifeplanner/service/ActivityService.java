package com.mylifeplanner.service;

import com.mylifeplanner.model.Activity;
import com.mylifeplanner.repository.ActivityRepository;
import org.springframework.stereotype.Service;

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
        return repository.findAll().stream()
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
        activity.setUser(account.getOwner());
        return repository.save(activity);
    }

    public void delete(Long id) {
        repository.deleteById(id);
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
