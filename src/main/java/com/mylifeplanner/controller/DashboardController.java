package com.mylifeplanner.controller;

import com.mylifeplanner.controller.support.ActivityCategoryView;
import com.mylifeplanner.model.Activity;
import com.mylifeplanner.model.FinanceTransaction;
import com.mylifeplanner.model.Priority;
import com.mylifeplanner.model.Task;
import com.mylifeplanner.model.TaskStatus;
import com.mylifeplanner.model.TransactionType;
import com.mylifeplanner.service.ActivityService;
import com.mylifeplanner.service.FinanceService;
import com.mylifeplanner.service.GoalService;
import com.mylifeplanner.service.TaskService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
public class DashboardController {
    private final TaskService taskService;
    private final ActivityService activityService;
    private final FinanceService financeService;
    private final GoalService goalService;
    private final ActivityCategoryView activityCategoryView;

    public DashboardController(TaskService taskService, ActivityService activityService,
                               FinanceService financeService, GoalService goalService,
                               ActivityCategoryView activityCategoryView) {
        this.taskService = taskService;
        this.activityService = activityService;
        this.financeService = financeService;
        this.goalService = goalService;
        this.activityCategoryView = activityCategoryView;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(name = "filter", required = false, defaultValue = "month") String filter,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "month", required = false) String monthStr,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {

        LocalDate today = LocalDate.now();
        Locale esLocale = Locale.forLanguageTag("es-PE");
        DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", esLocale);

        LocalDate calcStart = null;
        LocalDate calcEnd = null;
        String filterTitle = "";
        String activeFilter = filter;
        YearMonth selectedYearMonth = YearMonth.now();

        if (monthStr != null && !monthStr.isBlank()) {
            try {
                selectedYearMonth = YearMonth.parse(monthStr);
            } catch (Exception ignored) {
                selectedYearMonth = YearMonth.now();
            }
        }

        switch (activeFilter.toLowerCase()) {
            case "today":
                LocalDate targetDate = (date != null) ? date : today;
                calcStart = targetDate;
                calcEnd = targetDate;
                filterTitle = "Hoy, " + targetDate.format(DateTimeFormatter.ofPattern("dd 'de' MMMM", esLocale));
                break;
            case "week":
                LocalDate refDate = (date != null) ? date : today;
                calcStart = refDate.with(DayOfWeek.MONDAY);
                calcEnd = refDate.with(DayOfWeek.SUNDAY);
                filterTitle = "Esta Semana (" + calcStart.format(shortDateFormatter) + " - " + calcEnd.format(shortDateFormatter) + ")";
                break;
            case "range":
                calcStart = (startDate != null) ? startDate : today.minusDays(7);
                calcEnd = (endDate != null) ? endDate : today;
                if (calcEnd.isBefore(calcStart)) {
                    LocalDate temp = calcStart;
                    calcStart = calcEnd;
                    calcEnd = temp;
                }
                filterTitle = "Rango: " + calcStart.format(shortDateFormatter) + " al " + calcEnd.format(shortDateFormatter);
                break;
            case "all":
                calcStart = null;
                calcEnd = null;
                filterTitle = "Todo el historial registrado";
                break;
            case "month":
            default:
                activeFilter = "month";
                calcStart = selectedYearMonth.atDay(1);
                calcEnd = selectedYearMonth.atEndOfMonth();
                String monthName = selectedYearMonth.getMonth().getDisplayName(TextStyle.FULL, esLocale);
                monthName = monthName.substring(0, 1).toUpperCase() + monthName.substring(1);
                filterTitle = "Mes de " + monthName + " " + selectedYearMonth.getYear();
                break;
        }

        LocalDate weekStart = resolveActivityWeekStart(activeFilter, date, today);
        LocalDate weekEnd = weekStart.plusDays(6);

        // --- SECCIÓN 1: FINANZAS ---
        BigDecimal ingresos = financeService.totalByTypeInRange(TransactionType.INGRESO, calcStart, calcEnd);
        BigDecimal gastos = financeService.totalByTypeInRange(TransactionType.GASTO, calcStart, calcEnd);
        BigDecimal ahorros = financeService.totalByTypeInRange(TransactionType.AHORRO, calcStart, calcEnd);
        BigDecimal balance = ingresos.subtract(gastos).subtract(ahorros);

        List<FinanceTransaction> periodTransactions = financeService.findByDateRange(calcStart, calcEnd);
        long incomeCount = financeService.countByTypeInRange(TransactionType.INGRESO, calcStart, calcEnd);
        long expenseCount = financeService.countByTypeInRange(TransactionType.GASTO, calcStart, calcEnd);
        long savingCount = financeService.countByTypeInRange(TransactionType.AHORRO, calcStart, calcEnd);

        int savingsRate = 0;
        if (ingresos.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = ahorros.multiply(BigDecimal.valueOf(100))
                    .divide(ingresos, 0, RoundingMode.HALF_UP)
                    .intValue();
        }

        Map<String, BigDecimal> expensesByCategory = financeService.expensesByCategoryInRange(calcStart, calcEnd);

        // --- SECCIÓN 2: TAREAS ---
        List<Task> allTasks = taskService.findAll();
        Map<TaskStatus, List<Task>> tasksByStatus = taskService.findAllGroupedByStatus();
        int totalTasks = allTasks.size();
        int pendingCount = tasksByStatus.get(TaskStatus.POR_HACER).size();
        int inProgressCount = tasksByStatus.get(TaskStatus.EN_PROCESO).size();
        int blockedCount = tasksByStatus.get(TaskStatus.BLOQUEADO).size();
        int completedCount = tasksByStatus.get(TaskStatus.FINALIZADO).size();

        int doneCount = completedCount;
        int completionRate = totalTasks > 0 ? (doneCount * 100 / totalTasks) : 0;

        List<Task> highPriorityTasks = allTasks.stream()
                .filter(t -> t.getPriority() == Priority.ALTA && t.getStatus() != TaskStatus.FINALIZADO)
                .limit(5)
                .toList();

        // --- SECCIÓN 3: ACTIVIDADES DE LA SEMANA ---
        List<Activity> periodActivities = activityService.findByDateRange(calcStart, calcEnd);
        Map<LocalDate, List<Activity>> activitiesByDate = activityService.findGroupedByWeek(weekStart);
        List<Activity> weekActivities = activitiesByDate.values().stream()
                .flatMap(List::stream)
                .toList();
        int totalActivitiesWeek = activitiesByDate.values().stream().mapToInt(List::size).sum();

        // Model attributes
        model.addAttribute("activePage", "home");
        model.addAttribute("activeFilter", activeFilter);
        model.addAttribute("filterTitle", filterTitle);
        model.addAttribute("filterStartDate", calcStart != null ? calcStart.toString() : "");
        model.addAttribute("filterEndDate", calcEnd != null ? calcEnd.toString() : "");
        model.addAttribute("selectedMonth", selectedYearMonth.toString());
        model.addAttribute("today", today);
        model.addAttribute("weekStart", weekStart);
        model.addAttribute("weekEnd", weekEnd);
        model.addAttribute("dayNames", List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"));

        // Finanzas attributes
        model.addAttribute("ingresos", ingresos);
        model.addAttribute("gastos", gastos);
        model.addAttribute("ahorros", ahorros);
        model.addAttribute("balance", balance);
        model.addAttribute("incomeCount", incomeCount);
        model.addAttribute("expenseCount", expenseCount);
        model.addAttribute("savingCount", savingCount);
        model.addAttribute("savingsRate", savingsRate);
        model.addAttribute("transactions", periodTransactions);
        model.addAttribute("recentTransactions", periodTransactions.stream().limit(6).toList());
        model.addAttribute("financeChartLabels", List.of("Ingresos", "Gastos", "Ahorros"));
        model.addAttribute("financeChartValues", List.of(ingresos, gastos, ahorros));
        model.addAttribute("categoryLabels", expensesByCategory.keySet().stream().toList());
        model.addAttribute("categoryExpenses", expensesByCategory.values().stream().toList());

        // Tareas attributes
        model.addAttribute("tasks", allTasks);
        model.addAttribute("taskStatuses", TaskStatus.values());
        model.addAttribute("tasksByStatus", tasksByStatus);
        model.addAttribute("totalTasks", totalTasks);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("inProgressCount", inProgressCount);
        model.addAttribute("blockedCount", blockedCount);
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("completionRate", completionRate);
        model.addAttribute("highPriorityTasks", highPriorityTasks);
        model.addAttribute("taskChartLabels", List.of(TaskStatus.values()).stream().map(TaskStatus::getLabel).toList());
        model.addAttribute("taskChartValues", List.of(TaskStatus.values()).stream()
                .map(status -> tasksByStatus.get(status).size())
                .toList());

        // Actividades attributes
        model.addAttribute("activities", periodActivities);
        model.addAttribute("activitiesByDate", activitiesByDate);
        model.addAttribute("activityStyles", activityCategoryView.buildVisualStyles(weekActivities));
        model.addAttribute("activityIcons", activityCategoryView.buildIconClasses(weekActivities));
        model.addAttribute("totalActivitiesWeek", totalActivitiesWeek);
        model.addAttribute("activityChartLabels", List.of("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"));
        model.addAttribute("activityChartValues", activitiesByDate.values().stream()
                .map(List::size)
                .toList());

        // General
        model.addAttribute("goals", goalService.findAll());

        return "dashboard";
    }

    private LocalDate resolveActivityWeekStart(String activeFilter, LocalDate requestedDate, LocalDate today) {
        LocalDate referenceDate = "week".equalsIgnoreCase(activeFilter) && requestedDate != null
                ? requestedDate
                : today;
        return referenceDate.with(DayOfWeek.MONDAY);
    }
}
