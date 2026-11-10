package com.mylifeplanner.service;

import com.mylifeplanner.model.FinanceTransaction;
import com.mylifeplanner.model.TransactionType;
import com.mylifeplanner.repository.FinanceTransactionRepository;
import org.springframework.stereotype.Service;
import com.mylifeplanner.model.AppPage;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class FinanceService {
    private final FinanceTransactionRepository repository;

    private final AccountService account;
    private final com.mylifeplanner.repository.ScheduledPaymentRepository payments;

    public FinanceService(FinanceTransactionRepository repository, AccountService account,
                          com.mylifeplanner.repository.ScheduledPaymentRepository payments) {
        this.payments = payments;
        this.account = account;
        this.repository = repository;
    }

    public List<FinanceTransaction> findAll() {
        if (!account.canAccess(AppPage.FINANCES)) return List.of();
        return repository.findAllByUserId(account.currentUser().getId()).stream()
                .sorted(Comparator
                        .comparing(FinanceTransaction::getTransactionDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(FinanceTransaction::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<FinanceTransaction> findByMonth(YearMonth month) {
        return findAll().stream()
                .filter(transaction -> isInMonth(transaction, month))
                .toList();
    }

    public List<FinanceTransaction> findByMonth(YearMonth month, String sortAmount) {
        List<FinanceTransaction> list = findByMonth(month);
        if ("desc".equalsIgnoreCase(sortAmount)) {
            return list.stream()
                    .sorted(Comparator.comparing(FinanceTransaction::getAmount, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(FinanceTransaction::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } else if ("asc".equalsIgnoreCase(sortAmount)) {
            return list.stream()
                    .sorted(Comparator.comparing(FinanceTransaction::getAmount, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(FinanceTransaction::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        }
        return list;
    }

    public boolean isDuplicatedToNextMonth(FinanceTransaction tx) {
        if (tx == null || tx.getId() == null) return false;
        Long rootId = tx.getDuplicateSourceId() != null ? tx.getDuplicateSourceId() : tx.getId();
        YearMonth nextMonth = YearMonth.parse(tx.getMovementMonth()).plusMonths(1);
        return findAll().stream()
                .anyMatch(other -> !other.getId().equals(tx.getId())
                        && rootId.equals(other.getDuplicateSourceId() != null ? other.getDuplicateSourceId() : other.getId())
                        && nextMonth.toString().equals(other.getMovementMonth()));
    }

    public Set<Long> findDuplicatedToNextMonthIds(List<FinanceTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return Collections.emptySet();
        }
        List<FinanceTransaction> all = findAll();
        Set<Long> result = new HashSet<>();
        for (FinanceTransaction tx : transactions) {
            if (tx.getId() == null) continue;
            Long rootId = tx.getDuplicateSourceId() != null ? tx.getDuplicateSourceId() : tx.getId();
            YearMonth nextMonth = YearMonth.parse(tx.getMovementMonth()).plusMonths(1);
            boolean exists = all.stream().anyMatch(other ->
                    !other.getId().equals(tx.getId())
                    && rootId.equals(other.getDuplicateSourceId() != null ? other.getDuplicateSourceId() : other.getId())
                    && nextMonth.toString().equals(other.getMovementMonth()));
            if (exists) {
                result.add(tx.getId());
            }
        }
        return result;
    }

    public List<FinanceTransaction> findByDateRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return findAll();
        }
        return findAll().stream()
                .filter(tx -> {
                    LocalDate date = tx.getReportingDate();
                    if (date == null) return false;
                    if (start != null && date.isBefore(start)) return false;
                    if (end != null && date.isAfter(end)) return false;
                    return true;
                })
                .toList();
    }

    @org.springframework.transaction.annotation.Transactional
    public FinanceTransaction save(FinanceTransaction tx) {
        account.requireAccess(AppPage.FINANCES);
        if (tx.getId() != null) requireManualMovement(tx.getId());
        if (tx.getType() == TransactionType.ARRASTRE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El saldo trasladado se genera desde el cierre mensual.");
        }
        tx.setUser(account.currentUser());
        if (tx.getCategory() != null && "Bancos".equalsIgnoreCase(tx.getCategory().strip())) {
            if (!FinanceTransaction.BANKS.contains(tx.getBank() == null ? "" : tx.getBank())) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Escoge un banco de la lista");
            }
        } else {
            tx.setBank(null);
        }
        if (tx.getType() == TransactionType.INYECCION
                && tx.getFundingSource() == com.mylifeplanner.model.FundingSource.MONTHLY_BUDGET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selecciona el fondo externo del que proviene la inyección.");
        }
        if (tx.getType() != TransactionType.GASTO && tx.getType() != TransactionType.INYECCION) {
            tx.setFundingSource(com.mylifeplanner.model.FundingSource.MONTHLY_BUDGET);
        }
        validateLinkedExpense(tx);
        LocalDate monthDate = tx.getTransactionDate() != null ? tx.getTransactionDate()
                : tx.getAccountingMonth() != null ? tx.getAccountingMonth() : LocalDate.now();
        tx.setAccountingMonth(monthDate.withDayOfMonth(1));
        if (tx.getCategory() == null || tx.getCategory().isBlank()) {
            tx.setCategory(tx.getType().getLabel());
        }
        return repository.save(tx);
    }

    public List<FinanceTransaction> findPossibleFundingExpenses() {
        return findAll().stream()
                .filter(tx -> tx.getType() == TransactionType.GASTO && !tx.isAffectsMonthlyBudget())
                .sorted(Comparator.comparing(FinanceTransaction::getReportingDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @org.springframework.transaction.annotation.Transactional
    public FinanceTransaction saveWithFundingChannels(FinanceTransaction transaction, List<Long> expenseIds) {
        if (transaction.getId() != null && transaction.getType() != TransactionType.INYECCION) {
            boolean hasChannels = findAll().stream().anyMatch(expense -> expense.getFundingInjection() != null
                    && transaction.getId().equals(expense.getFundingInjection().getId()));
            if (hasChannels) {
                throw new IllegalArgumentException("Desvincula los gastos antes de cambiar el tipo de esta inyección.");
            }
        }
        FinanceTransaction saved = save(transaction);
        if (saved.getType() != TransactionType.INYECCION) return saved;

        Set<Long> selectedIds = expenseIds == null ? Set.of() : new LinkedHashSet<>(expenseIds);
        List<FinanceTransaction> selected = selectedIds.stream().map(this::findById).toList();
        for (FinanceTransaction expense : selected) {
            if (expense.getType() != TransactionType.GASTO || expense.isAffectsMonthlyBudget()) {
                throw new IllegalArgumentException("Solo se pueden canalizar gastos pagados con fondos externos.");
            }
            if (!expense.getMovementMonth().equals(saved.getMovementMonth())) {
                throw new IllegalArgumentException("La inyección y los gastos seleccionados deben pertenecer al mismo mes.");
            }
            if (expense.getFundingInjection() != null
                    && !saved.getId().equals(expense.getFundingInjection().getId())) {
                throw new IllegalArgumentException("Uno de los gastos ya está canalizado por otra inyección.");
            }
        }
        BigDecimal allocated = selected.stream().map(this::amountOf).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(saved.getAmount()) > 0) {
            throw new IllegalArgumentException("Los gastos seleccionados suman S/ " + allocated
                    + " y superan la inyección de S/ " + saved.getAmount() + ".");
        }

        List<FinanceTransaction> current = findAll().stream()
                .filter(expense -> expense.getFundingInjection() != null
                        && saved.getId().equals(expense.getFundingInjection().getId()))
                .toList();
        for (FinanceTransaction expense : current) {
            if (!selectedIds.contains(expense.getId())) {
                expense.setFundingInjection(null);
                saved.getFundedExpenses().remove(expense);
                repository.save(expense);
            }
        }
        for (FinanceTransaction expense : selected) {
            expense.setFundingInjection(saved);
            expense.setFundingSource(saved.getFundingSource());
            if (!saved.getFundedExpenses().contains(expense)) saved.getFundedExpenses().add(expense);
            repository.save(expense);
        }
        repository.flush();
        return repository.findById(saved.getId()).orElse(saved);
    }

    private void validateLinkedExpense(FinanceTransaction transaction) {
        FinanceTransaction injection = transaction.getFundingInjection();
        if (injection == null) return;
        if (transaction.getType() != TransactionType.GASTO) {
            throw new IllegalArgumentException("Un gasto canalizado debe conservar el tipo Gasto.");
        }
        transaction.setFundingSource(injection.getFundingSource());
        BigDecimal otherExpenses = injection.getFundedExpenses().stream()
                .filter(expense -> transaction.getId() == null || !transaction.getId().equals(expense.getId()))
                .map(this::amountOf).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (otherExpenses.add(amountOf(transaction)).compareTo(injection.getAmount()) > 0) {
            throw new IllegalArgumentException("El nuevo importe supera el saldo de la inyección vinculada.");
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public int duplicate(Long id, String scope) {
        if (!List.of("NEXT_MONTH", "REST_OF_YEAR").contains(scope)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Selecciona un periodo de copia");
        }
        FinanceTransaction original = findById(id);
        requireManualMovement(id);
        if ("NEXT_MONTH".equals(scope) && isDuplicatedToNextMonth(original)) {
            return 0;
        }
        YearMonth sourceMonth = YearMonth.parse(original.getMovementMonth());
        int count = "NEXT_MONTH".equals(scope) ? 1 : 12 - sourceMonth.getMonthValue();
        Long rootId = original.getDuplicateSourceId() == null ? original.getId() : original.getDuplicateSourceId();
        var existingMonths = findAll().stream()
                .filter(tx -> rootId.equals(tx.getId()) || rootId.equals(tx.getDuplicateSourceId()))
                .map(FinanceTransaction::getMovementMonth).collect(java.util.stream.Collectors.toSet());
        int created = 0;
        for (int offset = 1; offset <= count; offset++) {
            YearMonth target = sourceMonth.plusMonths(offset);
            if (!existingMonths.add(target.toString())) continue;
            FinanceTransaction copy = new FinanceTransaction();
            copy.setAmount(original.getAmount());
            copy.setCategory(original.getCategory());
            copy.setBank(original.getBank());
            copy.setDescription(original.getDescription());
            copy.setType(original.getType());
            copy.setFundingSource(original.getFundingSource());
            copy.setAccountingMonth(target.atDay(1));
            copy.setDuplicateSourceId(rootId);
            if (original.getTransactionDate() != null) {
                copy.setTransactionDate(target.atDay(Math.min(original.getTransactionDate().getDayOfMonth(), target.lengthOfMonth())));
            }
            save(copy);
            created++;
        }
        return created;
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(Long id) {
        requireManualMovement(id);
        FinanceTransaction transaction = findById(id);
        if (transaction.getType() == TransactionType.INYECCION && !transaction.getFundedExpenses().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta inyección tiene gastos canalizados. Edítala y desvincúlalos antes de eliminarla.");
        }
        repository.delete(transaction);
    }

    public void requireManualMovement(Long id) {
        FinanceTransaction transaction = findById(id);
        if (transaction.isCarryover()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este movimiento representa un saldo trasladado y no se edita manualmente.");
        }
        if (payments.existsByTransactionIdAndUserId(id, account.currentUser().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este gasto pertenece a una cuota. Gestiona o anula el pago desde Mi ciclo.");
        }
    }

    public Set<Long> linkedPaymentTransactionIds() {
        account.requireAccess(AppPage.FINANCES);
        return payments.findAllByUserId(account.currentUser().getId()).stream()
                .filter(com.mylifeplanner.model.ScheduledPayment::isPaid)
                .map(p -> p.getTransaction().getId()).collect(java.util.stream.Collectors.toSet());
    }

    public FinanceTransaction findById(Long id) {
        account.requireAccess(AppPage.FINANCES);
        return repository.findByIdAndUserId(id, account.currentUser().getId()).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Movimiento no encontrado"));
    }

    public BigDecimal totalByType(TransactionType type) {
        return totalByType(findAll(), type);
    }

    public BigDecimal totalByTypeForMonth(TransactionType type, YearMonth month) {
        return totalByType(findByMonth(month), type);
    }

    public BigDecimal totalByTypeInRange(TransactionType type, LocalDate start, LocalDate end) {
        return totalByType(findByDateRange(start, end), type);
    }

    public BigDecimal totalByTypeOnDate(TransactionType type, LocalDate date) {
        return totalByType(findAll().stream()
                .filter(transaction -> date.equals(transaction.getTransactionDate()))
                .toList(), type);
    }

    public BigDecimal availableForMonth(YearMonth month) {
        return totalByTypeForMonth(TransactionType.INGRESO, month)
                .add(totalByTypeForMonth(TransactionType.ARRASTRE, month))
                .subtract(totalBudgetExpensesForMonth(month))
                .subtract(totalByTypeForMonth(TransactionType.AHORRO, month));
    }

    public BigDecimal availableInRange(LocalDate start, LocalDate end) {
        return totalByTypeInRange(TransactionType.INGRESO, start, end)
                .subtract(totalBudgetExpensesInRange(start, end))
                .subtract(totalByTypeInRange(TransactionType.AHORRO, start, end));
    }

    public BigDecimal totalInjectionsForMonth(YearMonth month) {
        return totalByTypeForMonth(TransactionType.INYECCION, month);
    }

    public BigDecimal totalCarryoverForMonth(YearMonth month) {
        return totalByTypeForMonth(TransactionType.ARRASTRE, month);
    }

    public Optional<FinanceTransaction> findCarryoverFrom(YearMonth sourceMonth) {
        LocalDate source = sourceMonth.atDay(1);
        return findAll().stream().filter(tx -> source.equals(tx.getCarryoverSourceMonth())).findFirst();
    }

    public boolean hasCarryoverFrom(YearMonth sourceMonth) {
        return findCarryoverFrom(sourceMonth).isPresent();
    }

    @org.springframework.transaction.annotation.Transactional
    public FinanceTransaction carryOverAvailable(YearMonth sourceMonth, boolean automatic) {
        account.requireAccess(AppPage.FINANCES);
        Optional<FinanceTransaction> existing = findCarryoverFrom(sourceMonth);
        if (existing.isPresent()) return existing.get();
        if (automatic && !sourceMonth.atEndOfMonth().isBefore(LocalDate.now())) return null;
        BigDecimal amount = availableForMonth(sourceMonth);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            if (automatic) return null;
            throw new IllegalArgumentException("No hay un disponible positivo para trasladar al siguiente mes.");
        }
        YearMonth targetMonth = sourceMonth.plusMonths(1);
        FinanceTransaction carryover = new FinanceTransaction();
        carryover.setUser(account.currentUser());
        carryover.setType(TransactionType.ARRASTRE);
        carryover.setAmount(amount);
        carryover.setDescription("Disponible trasladado de " + sourceMonth.format(
                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es-PE"))));
        carryover.setCategory("Saldo anterior");
        carryover.setAccountingMonth(targetMonth.atDay(1));
        carryover.setRegistrationDate(LocalDate.now());
        carryover.setCarryoverSourceMonth(sourceMonth.atDay(1));
        carryover.setAutomaticCarryover(automatic);
        carryover.setFundingSource(com.mylifeplanner.model.FundingSource.MONTHLY_BUDGET);
        return repository.save(carryover);
    }

    public FinanceTransaction ensureAutomaticCarryover(YearMonth targetMonth) {
        return carryOverAvailable(targetMonth.minusMonths(1), true);
    }

    public BigDecimal totalBudgetExpensesForMonth(YearMonth month) {
        return totalBudgetExpenses(findByMonth(month));
    }

    public BigDecimal totalBudgetExpensesInRange(LocalDate start, LocalDate end) {
        return totalBudgetExpenses(findByDateRange(start, end));
    }

    public BigDecimal totalExternalFundedExpensesForMonth(YearMonth month) {
        return findByMonth(month).stream()
                .filter(transaction -> transaction.getType() == TransactionType.GASTO)
                .filter(transaction -> !transaction.isAffectsMonthlyBudget())
                .map(this::amountOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<String, BigDecimal> expensesByCategory(YearMonth month) {
        return expensesByCategoryFromList(findByMonth(month));
    }

    public Map<String, BigDecimal> expensesByCategoryInRange(LocalDate start, LocalDate end) {
        return expensesByCategoryFromList(findByDateRange(start, end));
    }

    private Map<String, BigDecimal> expensesByCategoryFromList(List<FinanceTransaction> list) {
        Map<String, BigDecimal> expenses = new LinkedHashMap<>();
        for (FinanceTransaction transaction : list) {
            if (transaction.getType() == TransactionType.GASTO) {
                String category = transaction.getCategory() == null || transaction.getCategory().isBlank()
                        ? "Otros"
                        : transaction.getCategory();
                expenses.merge(category, amountOf(transaction), BigDecimal::add);
            }
        }
        return expenses;
    }

    public List<String> dayLabels(YearMonth month) {
        return java.util.stream.IntStream.rangeClosed(1, month.lengthOfMonth())
                .mapToObj(String::valueOf)
                .toList();
    }

    public List<BigDecimal> dailyTotalsByType(TransactionType type, YearMonth month) {
        return java.util.stream.IntStream.rangeClosed(1, month.lengthOfMonth())
                .mapToObj(day -> totalByTypeOnDate(type, month.atDay(day)))
                .toList();
    }

    public List<String> monthLabels(int months) {
        YearMonth currentMonth = YearMonth.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("es-PE"));
        return java.util.stream.IntStream.iterate(months - 1, i -> i >= 0, i -> i - 1)
                .mapToObj(i -> currentMonth.minusMonths(i).format(formatter))
                .toList();
    }

    public List<BigDecimal> monthlyTotalsByType(TransactionType type, int months) {
        YearMonth currentMonth = YearMonth.now();
        return java.util.stream.IntStream.iterate(months - 1, i -> i >= 0, i -> i - 1)
                .mapToObj(i -> totalByTypeForMonth(type, currentMonth.minusMonths(i)))
                .toList();
    }

    public long countByTypeInRange(TransactionType type, LocalDate start, LocalDate end) {
        return findByDateRange(start, end).stream()
                .filter(tx -> tx.getType() == type)
                .count();
    }

    private BigDecimal totalByType(List<FinanceTransaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(transaction -> transaction.getType() == type)
                .map(this::amountOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalBudgetExpenses(List<FinanceTransaction> transactions) {
        return transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.GASTO)
                .filter(FinanceTransaction::isAffectsMonthlyBudget)
                .map(this::amountOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isInMonth(FinanceTransaction transaction, YearMonth month) {
        LocalDate date = transaction.getReportingDate();
        return date != null && YearMonth.from(date).equals(month);
    }

    private BigDecimal amountOf(FinanceTransaction transaction) {
        return transaction.getAmount() == null ? BigDecimal.ZERO : transaction.getAmount();
    }
}
