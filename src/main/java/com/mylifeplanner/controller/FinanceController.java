package com.mylifeplanner.controller;

import com.mylifeplanner.model.FinanceTransaction;
import com.mylifeplanner.model.FundingSource;
import com.mylifeplanner.model.TransactionType;
import com.mylifeplanner.service.FinanceCategoryService;
import com.mylifeplanner.service.FinanceCycleService;
import com.mylifeplanner.service.FinanceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Controller
@RequestMapping("/finances")
public class FinanceController {

    private static final int TRANSACTIONS_PER_PAGE = 10;

    private final FinanceService service;
    private final FinanceCategoryService categories;
    private final FinanceCycleService cycles;

    public FinanceController(FinanceService service, FinanceCategoryService categories,
                             FinanceCycleService cycles) {
        this.service = service;
        this.categories = categories;
        this.cycles = cycles;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String month,
                       @RequestParam(required = false) String year,
                       @RequestParam(required = false) String sortAmount,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        YearMonth selectedMonth = resolveMonth(month, year);
        if (selectedMonth.equals(YearMonth.now())) {
            service.ensureAutomaticCarryover(selectedMonth);
        }
        List<FinanceTransaction> monthTransactions = service.findByMonth(selectedMonth, sortAmount);
        int totalTransactions = monthTransactions.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalTransactions / TRANSACTIONS_PER_PAGE));
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        int fromIndex = Math.min((currentPage - 1) * TRANSACTIONS_PER_PAGE, totalTransactions);
        int toIndex = Math.min(fromIndex + TRANSACTIONS_PER_PAGE, totalTransactions);
        List<FinanceTransaction> transactions = monthTransactions.subList(fromIndex, toIndex);
        Set<Long> duplicatedToNextMonthIds = service.findDuplicatedToNextMonthIds(transactions);

        BigDecimal income = service.totalByTypeForMonth(TransactionType.INGRESO, selectedMonth);
        BigDecimal expense = service.totalByTypeForMonth(TransactionType.GASTO, selectedMonth);
        BigDecimal budgetExpense = service.totalBudgetExpensesForMonth(selectedMonth);
        BigDecimal externalFundedExpense = service.totalExternalFundedExpensesForMonth(selectedMonth);
        BigDecimal injections = service.totalInjectionsForMonth(selectedMonth);
        BigDecimal carryover = service.totalCarryoverForMonth(selectedMonth);
        BigDecimal saving = service.totalByTypeForMonth(TransactionType.AHORRO, selectedMonth);
        BigDecimal balance = service.availableForMonth(selectedMonth);

        BigDecimal previousExpense = service.totalByTypeForMonth(TransactionType.GASTO, selectedMonth.minusMonths(1));

        List<YearMonth> filterMonths = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            filterMonths.add(YearMonth.of(selectedMonth.getYear(), m));
        }
        List<String> filterMonthNames = List.of("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                "Julio", "Agosto", "Setiembre", "Octubre", "Noviembre", "Diciembre");
        List<Integer> filterYears = availableYears(selectedMonth.getYear());

        model.addAttribute("activePage", "finances");
        model.addAttribute("transaction", new FinanceTransaction());
        model.addAttribute("types", List.of(TransactionType.INGRESO, TransactionType.GASTO,
                TransactionType.AHORRO, TransactionType.INYECCION));
        model.addAttribute("fundingSources", FundingSource.values());
        model.addAttribute("transactions", transactions);
        model.addAttribute("totalTransactions", totalTransactions);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageStart", totalTransactions == 0 ? 0 : fromIndex + 1);
        model.addAttribute("pageEnd", toIndex);
        model.addAttribute("linkedPaymentTransactionIds", service.linkedPaymentTransactionIds());
        model.addAttribute("cycleMovementTransactionIds", cycles.cycleMovementIds());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("duplicatedToNextMonthIds", duplicatedToNextMonthIds);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("sortAmount", sortAmount != null ? sortAmount : "");
        model.addAttribute("income", income);
        model.addAttribute("monthIncome", income);
        model.addAttribute("expense", expense);
        model.addAttribute("monthExpenses", expense);
        model.addAttribute("monthBudgetExpenses", budgetExpense);
        model.addAttribute("monthExternalFundedExpenses", externalFundedExpense);
        model.addAttribute("monthInjections", injections);
        model.addAttribute("monthCarryover", carryover);
        model.addAttribute("saving", saving);
        model.addAttribute("monthSavings", saving);
        model.addAttribute("balance", balance);
        model.addAttribute("monthAvailable", balance);
        model.addAttribute("previousExpense", previousExpense);
        model.addAttribute("lastMonthExpenses", previousExpense);
        model.addAttribute("availableYears", filterYears);
        model.addAttribute("filterYears", filterYears);
        model.addAttribute("filterMonths", filterMonths);
        model.addAttribute("filterMonthNames", filterMonthNames);
        model.addAttribute("categories", categories.findAll());
        model.addAttribute("banks", FinanceTransaction.BANKS);
        model.addAttribute("recentTransactions", service.findAll().stream().limit(5).toList());
        model.addAttribute("possibleFundingExpenses", service.findPossibleFundingExpenses());
        model.addAttribute("carryoverCreated", service.hasCarryoverFrom(selectedMonth));
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1));

        populateCharts(model, selectedMonth);

        return "finances/list";
    }

    private YearMonth resolveMonth(String monthParam, String yearParam) {
        if (monthParam != null && !monthParam.isBlank()) {
            try {
                if (monthParam.contains("-")) {
                    return YearMonth.parse(monthParam.trim());
                }
                int month = Integer.parseInt(monthParam.trim());
                if (month >= 1 && month <= 12) {
                    int parsedYear = YearMonth.now().getYear();
                    if (yearParam != null && !yearParam.isBlank()) {
                        try {
                            int y = Integer.parseInt(yearParam.trim());
                            if (y >= 2000 && y <= 2100) {
                                parsedYear = y;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    return YearMonth.of(parsedYear, month);
                }
            } catch (Exception ignored) {
            }
        }
        return YearMonth.now();
    }

    private List<Integer> availableYears(int selectedYear) {
        int current = YearMonth.now().getYear();
        int min = Math.min(selectedYear, current - 3);
        int max = Math.max(selectedYear, current + 5);
        List<Integer> years = new ArrayList<>();
        for (int y = min; y <= max; y++) {
            years.add(y);
        }
        return years;
    }

    private void populateCharts(Model model, YearMonth month) {
        int length = month.lengthOfMonth();
        List<String> dailyLabels = new ArrayList<>();
        List<BigDecimal> dailyIncome = new ArrayList<>();
        List<BigDecimal> dailyExpenses = new ArrayList<>();
        List<BigDecimal> dailySavings = new ArrayList<>();

        for (int day = 1; day <= length; day++) {
            LocalDate date = month.atDay(day);
            dailyLabels.add(String.valueOf(day));
            dailyIncome.add(service.totalByTypeOnDate(TransactionType.INGRESO, date));
            dailyExpenses.add(service.totalByTypeOnDate(TransactionType.GASTO, date));
            dailySavings.add(service.totalByTypeOnDate(TransactionType.AHORRO, date));
        }

        model.addAttribute("dailyLabels", dailyLabels);
        model.addAttribute("dailyIncome", dailyIncome);
        model.addAttribute("dailyExpenses", dailyExpenses);
        model.addAttribute("dailySavings", dailySavings);

        Map<String, BigDecimal> expensesByCategory = service.expensesByCategory(month);
        model.addAttribute("categoryLabels", new ArrayList<>(expensesByCategory.keySet()));
        model.addAttribute("categoryExpenses", new ArrayList<>(expensesByCategory.values()));

        List<String> monthLabels = new ArrayList<>();
        List<BigDecimal> monthlyIncome = new ArrayList<>();
        List<BigDecimal> monthlyExpenses = new ArrayList<>();
        List<BigDecimal> monthlySavings = new ArrayList<>();

        for (int i = 5; i >= 0; i--) {
            YearMonth historical = month.minusMonths(i);
            monthLabels.add(historical.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("es-ES"))));
            monthlyIncome.add(service.totalByTypeForMonth(TransactionType.INGRESO, historical));
            monthlyExpenses.add(service.totalByTypeForMonth(TransactionType.GASTO, historical));
            monthlySavings.add(service.totalByTypeForMonth(TransactionType.AHORRO, historical));
        }

        model.addAttribute("monthLabels", monthLabels);
        model.addAttribute("monthlyIncome", monthlyIncome);
        model.addAttribute("monthlyExpenses", monthlyExpenses);
        model.addAttribute("monthlySavings", monthlySavings);

        model.addAttribute("financeChartLabels", List.of("Ingresos", "Gastos", "Ahorros"));
        model.addAttribute("financeChartValues", List.of(
                service.totalByTypeForMonth(TransactionType.INGRESO, month),
                service.totalByTypeForMonth(TransactionType.GASTO, month),
                service.totalByTypeForMonth(TransactionType.AHORRO, month)
        ));
    }

    @PostMapping
    public String save(@jakarta.validation.Valid @ModelAttribute FinanceTransaction transaction,
                       @RequestParam(required = false) String movementMonth,
                       @RequestParam(required = false) List<Long> expenseIds,
                       RedirectAttributes attributes) {
        transaction.setId(null);
        transaction.setDuplicateSourceId(null);
        transaction.setRegistrationDate(LocalDate.now());
        transaction.setAccountingMonth(parseMonth(movementMonth).atDay(1));
        try {
            service.saveWithFundingChannels(transaction, expenseIds);
        } catch (IllegalArgumentException exception) {
            attributes.addFlashAttribute("actionError", exception.getMessage());
        }
        return "redirect:/finances?month=" + transaction.getMovementMonth();
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @jakarta.validation.Valid @ModelAttribute FinanceTransaction transaction,
                         @RequestParam(required = false) String movementMonth,
                         @RequestParam(required = false) List<Long> expenseIds,
                         RedirectAttributes attributes) {
        service.requireManualMovement(id);
        FinanceTransaction existing = service.findById(id);
        existing.setAmount(transaction.getAmount());
        existing.setDescription(transaction.getDescription());
        existing.setCategory(transaction.getCategory());
        existing.setBank(transaction.getBank());
        existing.setType(transaction.getType());
        existing.setFundingSource(transaction.getFundingSource());
        existing.setAccountingMonth(parseMonth(movementMonth == null ? existing.getMovementMonth() : movementMonth).atDay(1));
        existing.setTransactionDate(transaction.getTransactionDate());
        try {
            service.saveWithFundingChannels(existing, expenseIds);
        } catch (IllegalArgumentException exception) {
            attributes.addFlashAttribute("actionError", exception.getMessage());
        }
        return "redirect:/finances?month=" + existing.getMovementMonth();
    }

    @PostMapping("/{id}/duplicate")
    public String duplicate(@PathVariable Long id,
                            @RequestParam(defaultValue = "NEXT_MONTH") String scope,
                            @RequestParam(required = false) String sortAmount,
                            RedirectAttributes attributes) {
        String month = service.findById(id).getMovementMonth();
        int created = service.duplicate(id, scope);
        attributes.addFlashAttribute("actionSuccess", created == 0
                ? "No hay meses pendientes de copiar en el periodo seleccionado o este movimiento ya fue duplicado al siguiente mes."
                : "Se crearon " + created + " movimientos correctamente. Revisa los meses de destino.");
        String redirect = "redirect:/finances?month=" + month;
        if (sortAmount != null && !sortAmount.isBlank()) {
            redirect += "&sortAmount=" + sortAmount;
        }
        return redirect;
    }

    @PostMapping("/carryover")
    public String carryover(@RequestParam String month, RedirectAttributes attributes) {
        YearMonth sourceMonth = parseMonth(month);
        try {
            boolean existed = service.hasCarryoverFrom(sourceMonth);
            FinanceTransaction movement = service.carryOverAvailable(sourceMonth, false);
            attributes.addFlashAttribute("actionSuccess", existed
                    ? "El disponible de ese mes ya estaba trasladado."
                    : "Disponible trasladado correctamente a " + sourceMonth.plusMonths(1) + ".");
            return "redirect:/finances?month=" + movement.getMovementMonth();
        } catch (IllegalArgumentException exception) {
            attributes.addFlashAttribute("actionError", exception.getMessage());
            return "redirect:/finances?month=" + sourceMonth;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam(required = false) String month) {
        service.delete(id);
        return "redirect:/finances?month=" + parseMonth(month);
    }

    @PostMapping("/categories")
    public String createCategory(@RequestParam String name, @RequestParam(required = false) String month,
                                 RedirectAttributes attributes) {
        try {
            String category = categories.create(name);
            attributes.addFlashAttribute("categorySuccess", "Categoría creada: " + category);
        } catch (IllegalArgumentException exception) {
            attributes.addFlashAttribute("categoryError", exception.getMessage());
            attributes.addFlashAttribute("categoryName", name);
        }
        return "redirect:/finances?month=" + parseMonth(month);
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException ex) {
            return YearMonth.now();
        }
    }
}
