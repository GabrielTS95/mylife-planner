package com.mylifeplanner.service;

import com.mylifeplanner.controller.support.CycleSettingsForm;
import com.mylifeplanner.controller.support.PaymentForm;
import com.mylifeplanner.model.*;
import com.mylifeplanner.repository.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Validated
public class FinanceCycleService {
    private final AccountService accounts;
    private final AppUserRepository users;
    private final ScheduledPaymentRepository payments;
    private final FinanceTransactionRepository transactions;

    public FinanceCycleService(AccountService accounts, AppUserRepository users,
                               ScheduledPaymentRepository payments, FinanceTransactionRepository transactions) {
        this.accounts = accounts;
        this.users = users;
        this.payments = payments;
        this.transactions = transactions;
    }

    public record Cycle(LocalDate start, LocalDate end, LocalDate nextPayday) {}
    public record PaymentRow(ScheduledPayment payment, String status, boolean paid, boolean overdue) {}
    public record PaymentReminder(ScheduledPayment payment, long daysUntilDue, boolean overdue) {}
    public record Overview(Cycle cycle, LocalDate asOf, BigDecimal carriedBalance, BigDecimal income,
                           BigDecimal expenses, BigDecimal externalFundedExpenses, BigDecimal savings,
                           BigDecimal balance, BigDecimal pending, BigDecimal pendingFromBudget,
                           BigDecimal overdue, BigDecimal available, long undatedCount,
                           List<PaymentRow> payments, List<PaymentRow> upcoming, List<FinanceTransaction> movements) {}

    public static Cycle cycleContaining(LocalDate date, int payday) {
        if (payday < 1 || payday > 31) throw new IllegalArgumentException("El día de cobro debe estar entre 1 y 31.");
        YearMonth month = YearMonth.from(date);
        LocalDate start = paydayIn(month, payday);
        if (date.isBefore(start)) {
            month = month.minusMonths(1);
            start = paydayIn(month, payday);
        }
        LocalDate next = paydayIn(month.plusMonths(1), payday);
        return new Cycle(start, next.minusDays(1), next);
    }

    @Transactional(readOnly = true)
    public List<PaymentReminder> paymentReminders(LocalDate today) {
        accounts.requireAccess(AppPage.FINANCES);
        return payments.findAllByUserId(accounts.currentUser().getId()).stream()
                .filter(payment -> !payment.isPaid())
                .map(payment -> {
                    long days = ChronoUnit.DAYS.between(today, payment.getDueDate());
                    return new PaymentReminder(payment, days, days < 0);
                })
                .sorted(Comparator.comparing(PaymentReminder::overdue).reversed()
                        .thenComparing(reminder -> reminder.payment().getDueDate())
                        .thenComparing(reminder -> reminder.payment().getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> currentMovementIds(LocalDate today) {
        accounts.requireAccess(AppPage.FINANCES);
        AppUser user = accounts.currentUser();
        Cycle cycle = cycleContaining(today, user.getPayday());
        LocalDate openingDate = user.getFinanceOpeningDate();
        return transactions.findAllByUserId(user.getId()).stream()
                .filter(tx -> tx.getTransactionDate() != null)
                .filter(tx -> !tx.getTransactionDate().isBefore(cycle.start())
                        && !tx.getTransactionDate().isAfter(cycle.end()))
                .filter(tx -> openingDate == null || !tx.getTransactionDate().isBefore(openingDate))
                .filter(tx -> tx.getType() == TransactionType.INGRESO
                        || tx.getType() == TransactionType.GASTO
                        || tx.getType() == TransactionType.AHORRO)
                .map(FinanceTransaction::getId)
                .collect(Collectors.toSet());
    }

    private static LocalDate paydayIn(YearMonth month, int payday) {
        return month.atDay(Math.min(payday, month.lengthOfMonth()));
    }

    @Transactional
    public void configure(@Valid CycleSettingsForm form) {
        accounts.requireAccess(AppPage.FINANCES);
        AppUser user = accounts.currentUser();
        user.setPayday(form.getPayday());
        user.setFinanceOpeningBalance(form.getOpeningBalance());
        user.setFinanceOpeningDate(form.getOpeningDate());
        users.save(user);
    }

    @Transactional(readOnly = true)
    public Overview overview(LocalDate reference, LocalDate today) {
        accounts.requireAccess(AppPage.FINANCES);
        if (reference.isAfter(today)) throw new IllegalArgumentException("Selecciona el ciclo actual o uno anterior.");
        AppUser user = accounts.currentUser();
        Cycle cycle = cycleContaining(reference, user.getPayday());
        LocalDate cutoff = cycle.end().isBefore(today) ? cycle.end() : today;
        LocalDate openingDate = user.getFinanceOpeningDate();
        List<FinanceTransaction> all = transactions.findAllByUserId(user.getId());
        // Only dated, received/paid movements contribute to cash. Future and undated entries do not.
        List<FinanceTransaction> realized = all.stream()
                .filter(tx -> tx.getTransactionDate() != null && !tx.getTransactionDate().isAfter(cutoff))
                .filter(tx -> tx.getType() == TransactionType.INGRESO
                        || tx.getType() == TransactionType.GASTO
                        || tx.getType() == TransactionType.AHORRO)
                .filter(tx -> openingDate == null || !tx.getTransactionDate().isBefore(openingDate))
                .toList();
        BigDecimal carried = openingDate == null || !openingDate.isAfter(cutoff)
                ? user.getFinanceOpeningBalance() : BigDecimal.ZERO;
        for (FinanceTransaction tx : realized) {
            if (tx.getTransactionDate().isBefore(cycle.start())) {
                if (tx.getType() == TransactionType.INGRESO) {
                    carried = carried.add(tx.getAmount());
                } else if (tx.getType() == TransactionType.AHORRO || tx.isAffectsMonthlyBudget()) {
                    carried = carried.subtract(tx.getAmount());
                }
            }
        }
        List<FinanceTransaction> movements = realized.stream()
                .filter(tx -> !tx.getTransactionDate().isBefore(cycle.start()))
                .sorted(Comparator.comparing(FinanceTransaction::getTransactionDate).reversed()
                        .thenComparing(FinanceTransaction::getId, Comparator.reverseOrder()))
                .toList();
        BigDecimal income = total(movements, TransactionType.INGRESO);
        BigDecimal expenses = total(movements, TransactionType.GASTO);
        BigDecimal budgetExpenses = movements.stream()
                .filter(tx -> tx.getType() == TransactionType.GASTO && tx.isAffectsMonthlyBudget())
                .map(FinanceTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal externalFundedExpenses = expenses.subtract(budgetExpenses);
        BigDecimal savings = total(movements, TransactionType.AHORRO);
        BigDecimal balance = carried.add(income).subtract(budgetExpenses).subtract(savings);
        List<ScheduledPayment> allPayments = payments.findAllByUserId(user.getId()).stream()
                .sorted(Comparator.comparing(ScheduledPayment::getDueDate).thenComparing(ScheduledPayment::getId))
                .toList();
        List<PaymentRow> rows = allPayments.stream()
                .filter(p -> (!p.getDueDate().isAfter(cycle.end())
                        && (!p.getDueDate().isBefore(cycle.start()) || !p.isPaidOn(cycle.start().minusDays(1))))
                        || (p.isPaidOn(cutoff) && !p.getTransaction().getTransactionDate().isBefore(cycle.start())))
                .map(p -> row(p, cutoff)).toList();
        BigDecimal pending = rows.stream().filter(p -> !p.paid()).map(p -> p.payment().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendingFromBudget = rows.stream()
                .filter(p -> !p.paid() && p.payment().getFundingSource().isAffectsMonthlyBudget())
                .map(p -> p.payment().getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overdue = rows.stream().filter(PaymentRow::overdue).map(p -> p.payment().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PaymentRow> upcoming = allPayments.stream()
                .filter(p -> p.getDueDate().isAfter(cycle.end()) && !p.isPaidOn(cutoff))
                .map(p -> row(p, cutoff)).toList();
        return new Overview(cycle, cutoff, carried, income, expenses, externalFundedExpenses, savings,
                balance, pending, pendingFromBudget, overdue, balance.subtract(pendingFromBudget),
                all.stream().filter(t -> t.getTransactionDate() == null).count(),
                rows, upcoming, movements);
    }

    private PaymentRow row(ScheduledPayment payment, LocalDate date) {
        boolean paid = payment.isPaidOn(date);
        boolean overdue = !paid && payment.getDueDate().isBefore(date);
        return new PaymentRow(payment, paid ? "Pagado" : overdue ? "Vencido" : "Pendiente", paid, overdue);
    }

    private BigDecimal total(List<FinanceTransaction> movements, TransactionType type) {
        return movements.stream().filter(t -> t.getType() == type).map(FinanceTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void create(@Valid PaymentForm form) {
        accounts.requireAccess(AppPage.FINANCES);
        AppUser user = accounts.currentUser();
        for (int i = 0; i < form.getInstallments(); i++) {
            ScheduledPayment payment = new ScheduledPayment();
            apply(payment, form);
            // Anchor to the original day: Jan 31 -> Feb 28/29 -> Mar 31.
            payment.setDueDate(form.getDueDate().plusMonths(i));
            payment.setUser(user);
            payment.setInstallmentNumber(i + 1);
            payment.setInstallmentCount(form.getInstallments());
            payments.save(payment);
        }
    }

    @Transactional(readOnly = true)
    public ScheduledPayment find(Long id) {
        accounts.requireAccess(AppPage.FINANCES);
        return payments.findByIdAndUserId(id, accounts.currentUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
    }

    private ScheduledPayment lock(Long id) {
        accounts.requireAccess(AppPage.FINANCES);
        return payments.lockOwned(id, accounts.currentUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
    }

    private void requirePending(ScheduledPayment payment) {
        if (payment.isPaid()) throw new IllegalArgumentException("Primero anula el pago para corregir esta cuota.");
    }

    private void apply(ScheduledPayment payment, PaymentForm form) {
        payment.setDescription(form.getDescription().strip());
        payment.setCreditor(form.getCreditor().strip());
        payment.setAmount(form.getAmount());
        payment.setDueDate(form.getDueDate());
        payment.setFundingSource(form.getFundingSource());
    }

    @Transactional
    public void update(Long id, @Valid PaymentForm form) {
        ScheduledPayment payment = lock(id);
        requirePending(payment);
        apply(payment, form);
        payments.save(payment);
    }

    @Transactional
    public void delete(Long id) {
        ScheduledPayment payment = lock(id);
        requirePending(payment);
        payments.delete(payment);
    }

    @Transactional
    public boolean pay(Long id, LocalDate date) {
        ScheduledPayment payment = lock(id);
        // The row lock and unique transaction link make repeat/concurrent submissions idempotent.
        if (payment.isPaid()) return false;
        if (date == null || date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Indica la fecha real del pago, hasta hoy.");
        }
        FinanceTransaction tx = new FinanceTransaction();
        tx.setUser(payment.getUser());
        tx.setType(TransactionType.GASTO);
        tx.setAmount(payment.getAmount());
        tx.setDescription(payment.getDescription() + " · " + payment.getCreditor()
                + (payment.getInstallmentCount() > 1 ? " · Cuota " + payment.getInstallmentNumber()
                + "/" + payment.getInstallmentCount() : ""));
        tx.setCategory("Deudas y pagos");
        tx.setFundingSource(payment.getFundingSource());
        tx.setTransactionDate(date);
        tx.setAccountingMonth(date.withDayOfMonth(1));
        payment.setTransaction(transactions.save(tx));
        payments.save(payment);
        return true;
    }

    @Transactional
    public void reopen(Long id) {
        ScheduledPayment payment = lock(id);
        if (!payment.isPaid()) return;
        FinanceTransaction tx = payment.getTransaction();
        payment.setTransaction(null);
        payments.saveAndFlush(payment);
        transactions.delete(tx);
    }
}
