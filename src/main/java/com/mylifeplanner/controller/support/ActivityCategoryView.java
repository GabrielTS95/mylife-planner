package com.mylifeplanner.controller.support;

import com.mylifeplanner.model.Activity;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ActivityCategoryView {
    private static final List<String> CATEGORIES = List.of(
            "Todos",
            "Trabajo",
            "Salud / Gym",
            "Estudios",
            "Personal",
            "Finanzas",
            "Ocio",
            "Otros"
    );

    public List<String> categories() {
        return CATEGORIES;
    }

    public String normalizeCategory(String category) {
        return CATEGORIES.contains(category) ? category : "Todos";
    }

    public boolean matchesCategory(Activity activity, String categoryFilter) {
        return "Todos".equals(categoryFilter) || categoryFilter.equals(activity.getCategory());
    }

    public String cssVariablesFor(Activity activity) {
        CategoryVisual visual = visualFor(activity.getCategory());
        return String.format(
                Locale.US,
                "--event-accent: %s; --event-bg: %s; --activity-accent: %s; --activity-bg: %s;",
                visual.accent(),
                visual.background(),
                visual.accent(),
                visual.background()
        );
    }

    public Map<Long, String> buildVisualStyles(Collection<Activity> activities) {
        Map<Long, String> styles = new HashMap<>();
        activities.stream()
                .filter(activity -> activity.getId() != null)
                .forEach(activity -> styles.put(activity.getId(), cssVariablesFor(activity)));
        return styles;
    }

    public Map<Long, String> buildIconClasses(Collection<Activity> activities) {
        Map<Long, String> icons = new HashMap<>();
        activities.stream()
                .filter(activity -> activity.getId() != null)
                .forEach(activity -> icons.put(activity.getId(), visualFor(activity.getCategory()).iconClass()));
        return icons;
    }

    public CategoryVisual visualFor(String category) {
        return switch (category == null ? "" : category) {
            case "Trabajo" -> new CategoryVisual("#6366f1", "rgba(99, 102, 241, .20)", "bi-briefcase-fill");
            case "Salud / Gym" -> new CategoryVisual("#22c55e", "rgba(34, 197, 94, .18)", "bi-heart-pulse-fill");
            case "Estudios" -> new CategoryVisual("#0ea5e9", "rgba(14, 165, 233, .18)", "bi-mortarboard-fill");
            case "Personal" -> new CategoryVisual("#f97316", "rgba(249, 115, 22, .18)", "bi-person-fill");
            case "Finanzas" -> new CategoryVisual("#14b8a6", "rgba(20, 184, 166, .18)", "bi-wallet2");
            case "Ocio" -> new CategoryVisual("#d946ef", "rgba(217, 70, 239, .18)", "bi-controller");
            default -> new CategoryVisual("#94a3b8", "rgba(148, 163, 184, .18)", "bi-three-dots");
        };
    }

    public record CategoryVisual(String accent, String background, String iconClass) {}
}
