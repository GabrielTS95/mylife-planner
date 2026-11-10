package com.mylifeplanner;

import com.mylifeplanner.controller.support.CycleSettingsForm;
import com.mylifeplanner.controller.support.PaymentForm;
import com.mylifeplanner.model.*;
import com.mylifeplanner.repository.*;
import com.mylifeplanner.service.FinanceCycleService;
import com.mylifeplanner.service.FinanceService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FinanceCycleTests {
    @Autowired FinanceCycleService cycles;
    @Autowired FinanceService finances;
    @Autowired AppUserRepository users;
    @Autowired ScheduledPaymentRepository payments;
    @Autowired FinanceTransactionRepository transactions;
    @Autowired MockMvc mvc;
    AppUser owner;

    @BeforeEach
    void setup() {
        owner = member("cycle-" + UUID.randomUUID(), AppPage.FINANCES);
        authenticate(owner.getUsername());
    }

    @AfterEach
    void clearSecurity() { TestSecurityContextHolder.clearContext(); }

    private AppUser member(String username, AppPage... pages) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash("unused-test-hash");
        user.setPages(Set.of(pages));
        return users.saveAndFlush(user);
    }

    private void authenticate(String username) {
        TestSecurityContextHolder.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, "", List.of()));
    }

    private FinanceTransaction movement(String amount, TransactionType type, LocalDate date) {
        FinanceTransaction tx = new FinanceTransaction();
        tx.setUser(owner);
        tx.setAmount(new BigDecimal(amount));
        tx.setType(type);
        tx.setTransactionDate(date);
        tx.setDescription("Movimiento del ciclo");
        return transactions.saveAndFlush(tx);
    }

    private PaymentForm form(String description, String amount, LocalDate date, int installments) {
        PaymentForm form = new PaymentForm();
        form.setDescription(description);
        form.setCreditor("Acreedor de prueba");
        form.setAmount(new BigDecimal(amount));
        form.setDueDate(date);
        form.setInstallments(installments);
        return form;
    }

    private ScheduledPayment pending(String description, String amount, LocalDate date) {
        cycles.create(form(description, amount, date, 1));
        return payments.findAllByUserId(owner.getId()).stream()
                .filter(p -> p.getDescription().equals(description)).findFirst().orElseThrow();
    }

    @Test
    void summarizesCarryoverReceivedMoneyAndOutstandingDebtsAcrossCalendarMonths() {
        CycleSettingsForm settings = new CycleSettingsForm();
        settings.setOpeningBalance(new BigDecimal("100"));
        settings.setOpeningDate(LocalDate.of(2020, 10, 1));
        cycles.configure(settings);
        movement("99999", TransactionType.INGRESO, LocalDate.of(2020, 9, 1));
        movement("500", TransactionType.INGRESO, LocalDate.of(2020, 10, 20));
        movement("100", TransactionType.GASTO, LocalDate.of(2020, 10, 27));
        movement("50", TransactionType.AHORRO, LocalDate.of(2020, 10, 27));
        movement("3000", TransactionType.INGRESO, LocalDate.of(2020, 10, 28));
        movement("800", TransactionType.GASTO, LocalDate.of(2020, 11, 2));
        movement("200", TransactionType.AHORRO, LocalDate.of(2020, 11, 2));
        movement("9999", TransactionType.INGRESO, LocalDate.of(2020, 11, 21));
        movement("10", TransactionType.INGRESO, null);
        pending("Vencido anterior", "200", LocalDate.of(2020, 10, 26));
        pending("Cuota actual", "900", LocalDate.of(2020, 11, 10));
        pending("Cuota siguiente", "400", LocalDate.of(2020, 11, 28));

        var summary = cycles.overview(LocalDate.of(2020, 11, 8), LocalDate.of(2020, 11, 8));
        assertThat(summary.cycle().start()).isEqualTo("2020-10-28");
        assertThat(summary.carriedBalance()).isEqualByComparingTo("450");
        assertThat(summary.balance()).isEqualByComparingTo("2450");
        assertThat(summary.pending()).isEqualByComparingTo("1100");
        assertThat(summary.overdue()).isEqualByComparingTo("200");
        assertThat(summary.available()).isEqualByComparingTo("1350");
        assertThat(summary.undatedCount()).isEqualTo(1);
        assertThat(summary.upcoming()).hasSize(1);
    }

    @Test
    void payingOnceMovesReservationToExpenseWithoutDoubleSubtractingAndCanBeReopened() {
        LocalDate today = LocalDate.now();
        movement("3000", TransactionType.INGRESO, today);
        var payment = pending("Cuota", "900", today);
        var before = cycles.overview(today, today);
        assertThat(before.available()).isEqualByComparingTo("2100");
        assertThat(cycles.pay(payment.getId(), today)).isTrue();
        assertThat(cycles.pay(payment.getId(), today)).isFalse();
        var after = cycles.overview(today, today);
        assertThat(after.available()).isEqualByComparingTo(before.available());
        assertThat(after.pending()).isZero();
        assertThat(after.expenses()).isEqualByComparingTo("900");
        assertThat(after.payments().getFirst().status()).isEqualTo("Pagado");
        assertThat(transactions.findAllByUserId(owner.getId())).hasSize(2);
        Long txId = cycles.find(payment.getId()).getTransaction().getId();
        cycles.reopen(payment.getId());
        cycles.reopen(payment.getId());
        assertThat(transactions.findById(txId)).isEmpty();
        assertThat(cycles.overview(today, today).available()).isEqualByComparingTo(before.available());
        assertThat(cycles.find(payment.getId()).isPaid()).isFalse();
    }

    @Test
    void savingsFundedPaymentsAreTrackedWithoutReducingTheMonthlyBudget() throws Exception {
        LocalDate today = LocalDate.now();
        movement("6000", TransactionType.INGRESO, today);
        FinanceTransaction garage = movement("5000", TransactionType.GASTO, today);
        garage.setDescription("Cuota cochera");
        garage.setFundingSource(FundingSource.SAVINGS);
        transactions.saveAndFlush(garage);
        mvc.perform(post("/finances").with(user(owner.getUsername())).with(csrf())
                        .param("type", "INYECCION").param("amount", "8000")
                        .param("fundingSource", "SAVINGS").param("category", "Vivienda")
                        .param("description", "Inyección para cochera")
                        .param("transactionDate", today.toString()).param("movementMonth", java.time.YearMonth.from(today).toString())
                        .param("expenseIds", garage.getId().toString()))
                .andExpect(redirectedUrl("/finances?month=" + java.time.YearMonth.from(today)));
        authenticate(owner.getUsername());
        FinanceTransaction injection = transactions.findAllByUserId(owner.getId()).stream()
                .filter(tx -> tx.getType() == TransactionType.INYECCION).findFirst().orElseThrow();
        FinanceTransaction linkedGarage = transactions.findById(garage.getId()).orElseThrow();
        assertThat(linkedGarage.getFundingInjection().getId()).isEqualTo(injection.getId());
        assertThat(injection.getAllocatedAmount()).isEqualByComparingTo("5000");
        assertThat(injection.getUnallocatedAmount()).isEqualByComparingTo("3000");

        assertThat(finances.totalByTypeForMonth(TransactionType.GASTO, java.time.YearMonth.from(today)))
                .isEqualByComparingTo("5000");
        assertThat(finances.totalExternalFundedExpensesForMonth(java.time.YearMonth.from(today)))
                .isEqualByComparingTo("5000");
        assertThat(finances.availableForMonth(java.time.YearMonth.from(today)))
                .isEqualByComparingTo("6000");
        assertThat(finances.totalInjectionsForMonth(java.time.YearMonth.from(today)))
                .isEqualByComparingTo("8000");

        PaymentForm futurePayment = form("Pago con ahorros", "700", today, 1);
        futurePayment.setFundingSource(FundingSource.SAVINGS);
        cycles.create(futurePayment);
        ScheduledPayment payment = payments.findAllByUserId(owner.getId()).getFirst();
        var pending = cycles.overview(today, today);
        assertThat(pending.pending()).isEqualByComparingTo("700");
        assertThat(pending.pendingFromBudget()).isZero();
        assertThat(pending.available()).isEqualByComparingTo("6000");

        cycles.pay(payment.getId(), today);
        var paid = cycles.overview(today, today);
        assertThat(paid.expenses()).isEqualByComparingTo("5700");
        assertThat(paid.externalFundedExpenses()).isEqualByComparingTo("5700");
        assertThat(paid.available()).isEqualByComparingTo("6000");
        assertThat(cycles.find(payment.getId()).getTransaction().getFundingSource()).isEqualTo(FundingSource.SAVINGS);
    }

    @Test
    void dashboardRotatesUpcomingAndOverduePaymentRemindersWithExactDays() throws Exception {
        LocalDate today = LocalDate.now();
        owner.setPages(Set.of(AppPage.HOME, AppPage.FINANCES));
        users.saveAndFlush(owner);
        pending("Cuota vencida", "150", today.minusDays(3));
        pending("Cuota próxima", "90", today.plusDays(5));

        var reminders = cycles.paymentReminders(today);
        assertThat(reminders).extracting(FinanceCycleService.PaymentReminder::daysUntilDue)
                .containsExactly(-3L, 5L);
        assertThat(reminders).extracting(FinanceCycleService.PaymentReminder::overdue)
                .containsExactly(true, false);

        mvc.perform(get("/").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-interval=\"10000\"")))
                .andExpect(content().string(containsString("payment-reminder-overdue")))
                .andExpect(content().string(containsString("Venció hace 3 días")))
                .andExpect(content().string(containsString("Faltan 5 días")))
                .andExpect(content().string(containsString("Cuota vencida")))
                .andExpect(content().string(containsString("Cuota próxima")));
    }

    @Test
    void carriesAvailableBalanceOnceAndKeepsItSeparateFromIncome() throws Exception {
        java.time.YearMonth source = java.time.YearMonth.from(LocalDate.now()).plusMonths(3);
        movement("2400", TransactionType.INGRESO, source.atDay(5));
        movement("900", TransactionType.GASTO, source.atDay(10));

        mvc.perform(post("/finances/carryover").with(user(owner.getUsername())).with(csrf())
                        .param("month", source.toString()))
                .andExpect(redirectedUrl("/finances?month=" + source.plusMonths(1)))
                .andExpect(flash().attributeExists("actionSuccess"));
        authenticate(owner.getUsername());
        FinanceTransaction carryover = finances.findCarryoverFrom(source).orElseThrow();
        FinanceTransaction repeated = finances.carryOverAvailable(source, false);

        assertThat(repeated.getId()).isEqualTo(carryover.getId());
        assertThat(carryover.getType()).isEqualTo(TransactionType.ARRASTRE);
        assertThat(carryover.getAmount()).isEqualByComparingTo("1500");
        assertThat(carryover.getMovementMonth()).isEqualTo(source.plusMonths(1).toString());
        assertThat(finances.availableForMonth(source.plusMonths(1))).isEqualByComparingTo("1500");
        assertThat(finances.totalByTypeForMonth(TransactionType.INGRESO, source.plusMonths(1))).isZero();
        assertThat(transactions.findAllByUserId(owner.getId()).stream()
                .filter(tx -> tx.getType() == TransactionType.ARRASTRE)).hasSize(1);
    }

    @Test
    void historicalSnapshotKeepsDebtPendingWhenItWasPaidInALaterCycle() {
        var payment = pending("Deuda antigua", "120", LocalDate.of(2020, 11, 10));
        cycles.pay(payment.getId(), LocalDate.of(2020, 12, 1));
        var old = cycles.overview(LocalDate.of(2020, 11, 10), LocalDate.now());
        assertThat(old.pending()).isEqualByComparingTo("120");
        assertThat(old.expenses()).isZero();
        assertThat(old.payments().getFirst().status()).isEqualTo("Vencido");
        var paidCycle = cycles.overview(LocalDate.of(2020, 12, 1), LocalDate.now());
        assertThat(paidCycle.pending()).isZero();
        assertThat(paidCycle.expenses()).isEqualByComparingTo("120");
        assertThat(paidCycle.payments().getFirst().status()).isEqualTo("Pagado");
    }

    @Test
    void createsMonthlyInstallmentsAndEditingOneDoesNotChangeTheOthers() {
        cycles.create(form("Préstamo", "75.50", LocalDate.of(2028, 1, 31), 3));
        var installments = payments.findAllByUserId(owner.getId()).stream()
                .sorted(java.util.Comparator.comparing(ScheduledPayment::getDueDate)).toList();
        assertThat(installments).extracting(ScheduledPayment::getDueDate)
                .containsExactly(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 31));
        assertThat(transactions.findAllByUserId(owner.getId())).isEmpty();
        cycles.update(installments.get(1).getId(), form("Cuota ajustada", "80", LocalDate.of(2028, 3, 1), 1));
        assertThat(cycles.find(installments.get(0).getId()).getAmount()).isEqualByComparingTo("75.50");
        assertThat(cycles.find(installments.get(1).getId()).getInstallmentNumber()).isEqualTo(2);
        cycles.delete(installments.get(2).getId());
        assertThat(payments.findAllByUserId(owner.getId())).hasSize(2);
    }

    @Test
    void pagesRenderAndValidationPreservesInputWithoutCreatingRecords() throws Exception {
        var payment = pending("Pago visible", "80", LocalDate.now());
        pending("Próximo pago", "50", LocalDate.now().plusMonths(2));
        mvc.perform(get("/finances/cycle").with(user(owner.getUsername())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Tras compromisos")))
                .andExpect(content().string(containsString("id=\"newPaymentModal\"")))
                .andExpect(content().string(containsString("id=\"cycleSettingsModal\"")))
                .andExpect(content().string(containsString("Pago visible")))
                .andExpect(content().string(containsString("Próximo pago")));
        mvc.perform(get("/finances/cycle/payments/{id}/edit", payment.getId()).with(user(owner.getUsername())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Pago visible")));
        mvc.perform(post("/finances/cycle/payments").with(user(owner.getUsername())).with(csrf())
                        .param("description", "Conservar texto").param("creditor", "Banco").param("amount", "0")
                        .param("dueDate", LocalDate.now().toString()).param("installments", "121"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("paymentForm", "amount", "installments"))
                .andExpect(model().attribute("openCycleModal", "newPaymentModal"))
                .andExpect(content().string(containsString("Conservar texto")));
        assertThat(payments.findAllByUserId(owner.getId())).hasSize(2);
        mvc.perform(post("/finances/cycle/settings").with(user(owner.getUsername())).with(csrf())
                        .param("payday", "32").param("openingBalance", "0").param("openingDate", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("settingsForm", "payday", "openingDate"))
                .andExpect(model().attribute("openCycleModal", "cycleSettingsModal"));
        assertThat(users.findById(owner.getId()).orElseThrow().getPayday()).isEqualTo(28);
    }

    @Test
    void paidExpensesCannotBeChangedDuplicatedOrDeletedFromMovements() throws Exception {
        var payment = pending("Cuota protegida", "60", LocalDate.now());
        cycles.pay(payment.getId(), LocalDate.now());
        Long txId = cycles.find(payment.getId()).getTransaction().getId();
        FinanceTransaction manual = movement("25", TransactionType.INGRESO, LocalDate.now());
        mvc.perform(get("/finances").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("cycleMovementTransactionIds",
                        org.hamcrest.Matchers.hasItems(txId, manual.getId())))
                .andExpect(content().string(containsString("Gestionar pago en Mi ciclo")))
                .andExpect(content().string(containsString("class=\"cycle-link-badge\"")))
                .andExpect(content().string(containsString("aria-label=\"Ver este movimiento en Mi ciclo\">C</a>")));
        mvc.perform(post("/finances/{id}/edit", txId).with(user(owner.getUsername())).with(csrf())
                        .param("amount", "1").param("type", "INGRESO"))
                .andExpect(status().isConflict());
        mvc.perform(post("/finances/{id}/duplicate", txId).with(user(owner.getUsername())).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post("/finances/{id}/delete", txId).with(user(owner.getUsername())).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(transactions.findById(txId).orElseThrow().getAmount()).isEqualByComparingTo("60");
        mvc.perform(get("/finances/cycle").with(user(owner.getUsername()))).andExpect(status().isOk());
    }

    @Test
    void marksFutureDatedMovementsThatBelongToTheCurrentCycle() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        FinanceTransaction futureMovement = movement("80", TransactionType.GASTO,
                LocalDate.of(2026, 11, 24));

        assertThat(cycles.currentMovementIds(today)).contains(futureMovement.getId());
        assertThat(cycles.overview(today, today).movements()).doesNotContain(futureMovement);
    }

    @Test
    void enforcesOwnershipPermissionsCsrfAndActualPaymentDates() throws Exception {
        var payment = pending("Pago privado", "40", LocalDate.now());
        var other = member("other-" + UUID.randomUUID(), AppPage.FINANCES);
        for (String action : List.of("pay", "reopen", "delete", "edit")) {
            mvc.perform(post("/finances/cycle/payments/{id}/" + action, payment.getId())
                            .with(user(other.getUsername())).with(csrf()).param("paymentDate", LocalDate.now().toString())
                            .param("description", "Intento ajeno").param("creditor", "Banco").param("amount", "10")
                            .param("dueDate", LocalDate.now().toString()))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/finances/cycle/payments/{id}/edit", payment.getId()).with(user(other.getUsername())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/finances/cycle").with(user(other.getUsername())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Pago privado"))));
        mvc.perform(post("/finances/cycle/payments/{id}/pay", payment.getId()).with(user(owner.getUsername()))
                        .param("paymentDate", LocalDate.now().toString())).andExpect(status().isForbidden());
        mvc.perform(post("/finances/cycle/payments/{id}/pay", payment.getId()).with(user(owner.getUsername())).with(csrf())
                        .param("paymentDate", LocalDate.now().plusDays(1).toString()))
                .andExpect(redirectedUrl("/finances/cycle")).andExpect(flash().attributeExists("cycleError"))
                .andExpect(flash().attributeCount(1));
        var restricted = member("restricted-" + UUID.randomUUID(), AppPage.TASKS);
        mvc.perform(get("/finances/cycle").with(user(restricted.getUsername()))).andExpect(status().isForbidden());
        mvc.perform(post("/finances/cycle/settings").with(user(restricted.getUsername())).with(csrf())
                        .param("payday", "5").param("openingBalance", "100"))
                .andExpect(status().isForbidden());
        assertThat(payments.findById(payment.getId()).orElseThrow().isPaid()).isFalse();
        assertThat(transactions.findAllByUserId(owner.getId())).isEmpty();
    }

    @Test
    void completeHttpWorkflowPersistsSettingsAndConnectsPaymentsToExpenses() throws Exception {
        String today = LocalDate.now().toString();
        mvc.perform(post("/finances/cycle/settings").with(user(owner.getUsername())).with(csrf())
                        .param("payday", "15").param("openingBalance", "250").param("openingDate", today))
                .andExpect(redirectedUrl("/finances/cycle"));
        assertThat(users.findById(owner.getId()).orElseThrow().getPayday()).isEqualTo(15);
        mvc.perform(post("/finances/cycle/payments").with(user(owner.getUsername())).with(csrf())
                        .param("description", "Pago HTTP").param("creditor", "Mi banco").param("amount", "80.50")
                        .param("dueDate", today).param("installments", "2")
                        .param("user.id", "1").param("transaction.id", "1"))
                .andExpect(redirectedUrl("/finances/cycle"));
        var created = payments.findAllByUserId(owner.getId()).stream()
                .sorted(java.util.Comparator.comparing(ScheduledPayment::getDueDate)).toList();
        assertThat(created).hasSize(2);
        Long id = created.getFirst().getId();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/finances/cycle/payments/{id}/pay", id).with(user(owner.getUsername())).with(csrf())
                            .param("paymentDate", today))
                    .andExpect(redirectedUrl("/finances/cycle")).andExpect(flash().attributeExists("actionSuccess"));
        }
        assertThat(transactions.findAllByUserId(owner.getId())).hasSize(1);
        mvc.perform(post("/finances/cycle/payments/{id}/delete", id).with(user(owner.getUsername())).with(csrf()))
                .andExpect(flash().attributeExists("cycleError"));
        mvc.perform(post("/finances/cycle/payments/{id}/reopen", id).with(user(owner.getUsername())).with(csrf()))
                .andExpect(redirectedUrl("/finances/cycle"));
        assertThat(transactions.findAllByUserId(owner.getId())).isEmpty();
        mvc.perform(post("/finances/cycle/payments/{id}/edit", id).with(user(owner.getUsername())).with(csrf())
                        .param("description", "Cuota corregida").param("creditor", "Mi banco").param("amount", "95")
                        .param("dueDate", today))
                .andExpect(redirectedUrl("/finances/cycle"));
        assertThat(payments.findById(id).orElseThrow().getAmount()).isEqualByComparingTo("95");
        assertThat(payments.findById(created.getLast().getId()).orElseThrow().getAmount()).isEqualByComparingTo("80.50");
    }

    @Test
    void earlyPaymentAndNegativeBalanceRemainVisibleAndNewCycleCarriesCashOnly() {
        LocalDate first = LocalDate.of(2020, 10, 28);
        movement("200", TransactionType.INGRESO, first);
        var early = pending("Pago anticipado", "300", LocalDate.of(2020, 12, 5));
        cycles.pay(early.getId(), first);
        var cycle = cycles.overview(first, first);
        assertThat(cycle.available()).isEqualByComparingTo("-100");
        assertThat(cycle.payments()).hasSize(1);
        assertThat(cycle.upcoming()).isEmpty();
        var next = cycles.overview(LocalDate.of(2020, 11, 28), LocalDate.of(2020, 11, 28));
        assertThat(next.carriedBalance()).isEqualByComparingTo("-100");
        assertThat(next.expenses()).isZero();
        assertThat(next.pending()).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentPaymentRequestsCreateOnlyOneExpense() throws Exception {
        var payment = pending("Cuota concurrente", "100", LocalDate.now());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> work = () -> {
                authenticate(owner.getUsername());
                ready.countDown();
                try {
                    if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No concurrent start");
                    return cycles.pay(payment.getId(), LocalDate.now());
                } finally { TestSecurityContextHolder.clearContext(); }
            };
            Future<Boolean> one = pool.submit(work);
            Future<Boolean> two = pool.submit(work);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(transactions.findAllByUserId(owner.getId())).hasSize(1);
        } finally {
            authenticate(owner.getUsername());
            cycles.reopen(payment.getId());
            cycles.delete(payment.getId());
            users.deleteById(owner.getId());
        }
    }
}
