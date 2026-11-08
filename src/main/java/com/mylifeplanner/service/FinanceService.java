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

    public FinanceTransaction save(FinanceTransaction tx) {
        account.requireAccess(AppPage.FINANCES);
        if (tx.getId() != null) requireManualMovement(tx.getId());
        tx.setUser(account.currentUser());
        if (tx.getCategory() != null && "Bancos".equalsIgnoreCase(tx.getCategory().strip())) {
            if (!FinanceTransaction.BANKS.contains(tx.getBank() == null ? "" : tx.getBank())) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Escoge un banco de la lista");
            }
        } else {
            tx.setBank(null);
        }
        LocalDate monthDate = tx.getTransactionDate() != null ? tx.getTransactionDate()
                : tx.getAccountingMonth() != null ? tx.getAccountingMonth() : LocalDate.now();
        tx.setAccountingMonth(monthDate.withDayOfMonth(1));
        if (tx.getCategory() == null || tx.getCategory().isBlank()) {
            tx.setCategory(tx.getType().getLabel());
        }
        return repository.save(tx);
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

    public void delete(Long id) {
        requireManualMovement(id);
        repository.delete(findById(id));
    }

    public void requireManualMovement(Long id) {
        findById(id);
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
                .subtract(totalByTypeForMonth(TransactionType.GASTO, month))
                .subtract(totalByTypeForMonth(TransactionType.AHORRO, month));
    }

    public BigDecimal availableInRange(LocalDate start, LocalDate end) {
        return totalByTypeInRange(TransactionType.INGRESO, start, end)
                .subtract(totalByTypeInRange(TransactionType.GASTO, start, end))
                .subtract(totalByTypeInRange(TransactionType.AHORRO, start, end));
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

    private boolean isInMonth(FinanceTransaction transaction, YearMonth month) {
        LocalDate date = transaction.getReportingDate();
        return date != null && YearMonth.from(date).equals(month);
    }

    private BigDecimal amountOf(FinanceTransaction transaction) {
        return transaction.getAmount() == null ? BigDecimal.ZERO : transaction.getAmount();
    }
}
