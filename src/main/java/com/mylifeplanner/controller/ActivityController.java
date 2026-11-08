package com.mylifeplanner.controller;

import com.mylifeplanner.controller.support.ActivityCategoryView;
import com.mylifeplanner.model.Activity;
import com.mylifeplanner.service.ActivityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

@Controller
@RequestMapping("/activities")
public class ActivityController {
    private static final int CALENDAR_START_HOUR = 0;
    private static final int CALENDAR_END_HOUR = 24;
    private static final double MINUTE_HEIGHT = 1.4;
    private static final Locale SPANISH = Locale.forLanguageTag("es");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", SPANISH);
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("EEEE", SPANISH);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ActivityService service;
    private final ActivityCategoryView categoryView;

    public ActivityController(ActivityService service, ActivityCategoryView categoryView) {
        this.service = service;
        this.categoryView = categoryView;
    }

    @GetMapping
    public String list(
            @RequestParam(value = "weekStart", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requestedWeekStart,
            @RequestParam(value = "viewMode", defaultValue = "work") String viewMode,
            @RequestParam(value = "filterCategory", defaultValue = "Todos") String categoryFilter,
            Model model) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = (requestedWeekStart != null ? requestedWeekStart : today).with(DayOfWeek.MONDAY);
        String normalizedViewMode = "full".equalsIgnoreCase(viewMode) ? "full" : "work";
        String normalizedCategory = categoryView.normalizeCategory(categoryFilter);
        int visibleDays = "full".equals(normalizedViewMode) ? 7 : 5;
        LocalDate visibleEnd = weekStart.plusDays(visibleDays - 1);

        Map<LocalDate, List<Activity>> activitiesByDate = findGroupedByVisibleDays(weekStart, visibleDays, normalizedCategory);
        List<Activity> visibleActivities = activitiesByDate.values().stream()
                .flatMap(List::stream)
                .toList();
        List<Activity> todayActivities = service.findByDateRange(today, today).stream()
                .filter(activity -> matchesCategory(activity, normalizedCategory))
                .toList();
        Activity nextActivity = findNextActivity(visibleActivities, today, LocalTime.now());

        Activity activity = new Activity();
        activity.setActivityDate(today);
        activity.setStartTime(LocalTime.of(9, 0));
        activity.setEndTime(LocalTime.of(10, 0));
        activity.setCategory("Trabajo");

        List<TimeSlot> timeSlots = buildTimeSlots();
        model.addAttribute("activePage", "activities");
        model.addAttribute("activities", visibleActivities);
        model.addAttribute("activitiesByDate", activitiesByDate);
        model.addAttribute("activity", activity);
        model.addAttribute("activityStyles", buildActivityStyles(visibleActivities));
        model.addAttribute("activityIcons", categoryView.buildIconClasses(visibleActivities));
        model.addAttribute("calendarStatusText", visibleActivities.isEmpty() ? "Semana libre" : visibleActivities.size() + " actividades planificadas");
        model.addAttribute("categories", categoryView.categories());
        model.addAttribute("categoryFilter", normalizedCategory);
        model.addAttribute("currentWeekStart", today.with(DayOfWeek.MONDAY));
        model.addAttribute("dayLabels", buildDayLabels(activitiesByDate));
        model.addAttribute("hourLabels", buildHourLabels());
        model.addAttribute("monthLabel", weekStart.format(MONTH_FORMATTER));
        model.addAttribute("nextActivity", nextActivity);
        model.addAttribute("nextWeekStart", weekStart.plusWeeks(1));
        model.addAttribute("previousWeekStart", weekStart.minusWeeks(1));
        model.addAttribute("scheduledTimeLabel", formatDuration(visibleActivities.stream().mapToInt(this::durationMinutes).sum()));
        model.addAttribute("slotCount", timeSlots.size());
        model.addAttribute("timeSlots", timeSlots);
        model.addAttribute("today", today);
        model.addAttribute("todayActivitiesCount", todayActivities.size());
        model.addAttribute("totalActivities", visibleActivities.size());
        model.addAttribute("viewMode", normalizedViewMode);
        model.addAttribute("visibleDays", visibleDays);
        model.addAttribute("weekEnd", visibleEnd);
        model.addAttribute("weekStart", weekStart);
        return "activities/list";
    }

    @PostMapping
    public String save(
            @ModelAttribute Activity activity,
            @RequestParam(value = "weekStart", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(value = "viewMode", defaultValue = "work") String viewMode,
            @RequestParam(value = "filterCategory", defaultValue = "Todos") String categoryFilter) {
        service.save(activity);
        return redirectToCalendar(weekStart, viewMode, categoryFilter);
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable Long id,
            @RequestParam(value = "weekStart", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(value = "viewMode", defaultValue = "work") String viewMode,
            @RequestParam(value = "filterCategory", defaultValue = "Todos") String categoryFilter) {
        service.delete(id);
        return redirectToCalendar(weekStart, viewMode, categoryFilter);
    }

    private Map<LocalDate, List<Activity>> findGroupedByVisibleDays(LocalDate weekStart, int visibleDays, String categoryFilter) {
        Map<LocalDate, List<Activity>> activitiesByDate = new LinkedHashMap<>();
        for (int day = 0; day < visibleDays; day++) {
            activitiesByDate.put(weekStart.plusDays(day), new java.util.ArrayList<>());
        }

        LocalDate weekEnd = weekStart.plusDays(visibleDays - 1);
        for (Activity activity : service.findByDateRange(weekStart, weekEnd)) {
            LocalDate date = activity.getActivityDate();
            if (date != null && activitiesByDate.containsKey(date) && matchesCategory(activity, categoryFilter)) {
                activitiesByDate.get(date).add(activity);
            }
        }
        return activitiesByDate;
    }

    private Map<LocalDate, String> buildDayLabels(Map<LocalDate, List<Activity>> activitiesByDate) {
        Map<LocalDate, String> labels = new LinkedHashMap<>();
        activitiesByDate.keySet().forEach(date -> labels.put(date, capitalize(date.format(DAY_FORMATTER))));
        return labels;
    }

    private List<String> buildHourLabels() {
        return IntStream.range(CALENDAR_START_HOUR, CALENDAR_END_HOUR)
                .mapToObj(hour -> String.format("%02d:00", hour))
                .toList();
    }

    private List<TimeSlot> buildTimeSlots() {
        java.util.ArrayList<TimeSlot> slots = new java.util.ArrayList<>();
        for (int hour = CALENDAR_START_HOUR; hour < CALENDAR_END_HOUR; hour++) {
            slots.add(new TimeSlot(formatSlotTime(hour * 60), formatSlotTime(hour * 60 + 30)));
            slots.add(new TimeSlot(formatSlotTime(hour * 60 + 30), formatSlotTime((hour + 1) * 60)));
        }
        return slots;
    }

    private String formatSlotTime(int minuteOfDay) {
        int safeMinuteOfDay = Math.min(minuteOfDay, (24 * 60) - 1);
        return LocalTime.of(safeMinuteOfDay / 60, safeMinuteOfDay % 60).format(TIME_FORMATTER);
    }

    private Map<Long, String> buildActivityStyles(List<Activity> activities) {
        Map<Long, String> styles = categoryView.buildVisualStyles(activities);
        activities.stream()
                .filter(activity -> activity.getId() != null)
                .forEach(activity -> styles.put(activity.getId(), buildActivityStyle(activity)));
        return styles;
    }

    private String buildActivityStyle(Activity activity) {
        int laneMinutes = (CALENDAR_END_HOUR - CALENDAR_START_HOUR) * 60;
        LocalTime start = activity.getStartTime() != null ? activity.getStartTime() : LocalTime.of(CALENDAR_START_HOUR, 0);
        int startOffset = minutesOfDay(start) - CALENDAR_START_HOUR * 60;
        int topMinutes = Math.max(0, Math.min(startOffset, laneMinutes - 30));
        int heightMinutes = Math.max(30, durationMinutes(activity));
        int visibleHeightMinutes = Math.min(heightMinutes, laneMinutes - topMinutes);
        double topPx = topMinutes * MINUTE_HEIGHT;
        double heightPx = Math.max(44, visibleHeightMinutes * MINUTE_HEIGHT);

        return String.format(
                Locale.US,
                "--event-top: %.1fpx; --event-height: %.1fpx; %s",
                topPx,
                heightPx,
                categoryView.cssVariablesFor(activity)
        );
    }

    private int durationMinutes(Activity activity) {
        if (activity.getStartTime() == null || activity.getEndTime() == null || !activity.getEndTime().isAfter(activity.getStartTime())) {
            return 60;
        }
        return (int) Duration.between(activity.getStartTime(), activity.getEndTime()).toMinutes();
    }

    private int minutesOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private boolean matchesCategory(Activity activity, String categoryFilter) {
        return categoryView.matchesCategory(activity, categoryFilter);
    }

    private Activity findNextActivity(List<Activity> activities, LocalDate today, LocalTime now) {
        return activities.stream()
                .filter(activity -> activity.getActivityDate() != null)
                .filter(activity -> activity.getActivityDate().isAfter(today)
                        || (activity.getActivityDate().equals(today)
                        && (activity.getStartTime() == null || !activity.getStartTime().isBefore(now))))
                .findFirst()
                .orElse(null);
    }

    private String formatDuration(int minutes) {
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        if (hours == 0) {
            return remainingMinutes + " min";
        }
        if (remainingMinutes == 0) {
            return hours + " h";
        }
        return hours + " h " + remainingMinutes + " min";
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(SPANISH) + value.substring(1);
    }

    private String redirectToCalendar(LocalDate weekStart, String viewMode, String categoryFilter) {
        LocalDate normalizedWeekStart = (weekStart != null ? weekStart : LocalDate.now()).with(DayOfWeek.MONDAY);
        String normalizedViewMode = "full".equalsIgnoreCase(viewMode) ? "full" : "work";
        String normalizedCategory = categoryView.normalizeCategory(categoryFilter);
        return "redirect:" + UriComponentsBuilder.fromPath("/activities")
                .queryParam("weekStart", normalizedWeekStart)
                .queryParam("viewMode", normalizedViewMode)
                .queryParam("filterCategory", normalizedCategory)
                .build()
                .encode()
                .toUriString();
    }

    public record TimeSlot(String startTime, String endTime) {}
}
