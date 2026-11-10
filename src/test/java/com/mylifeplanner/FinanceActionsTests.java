package com.mylifeplanner;

import com.mylifeplanner.model.FinanceTransaction;
import com.mylifeplanner.model.TransactionType;
import com.mylifeplanner.repository.FinanceTransactionRepository;
import com.mylifeplanner.service.FinanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "erwintorr", roles = "OWNER")
class FinanceActionsTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FinanceService financeService;

    @Autowired
    private FinanceTransactionRepository repository;

    private FinanceService finance() {
        // MVC requests clear the thread's security context; direct service calls need the test user too.
        org.springframework.security.core.context.SecurityContextHolder.setContext(
                org.springframework.security.test.context.TestSecurityContextHolder.getContext());
        return financeService;
    }

    private FinanceTransaction movement() {
        FinanceTransaction tx = new FinanceTransaction();
        tx.setAmount(new BigDecimal("40.00"));
        tx.setDescription("Movimiento de prueba");
        tx.setCategory("Servicios");
        tx.setTransactionDate(LocalDate.now());
        tx.setType(TransactionType.GASTO);
        return finance().save(tx);
    }

    @Test
    void bankValidationRequiresAcceptedBankOnlyForBancosCategory() throws Exception {
        FinanceTransaction tx = movement();
        mvc.perform(post("/finances/{id}/edit", tx.getId()).with(csrf()).param("amount", "50")
                        .param("category", "Bancos").param("bank", "INVALIDO"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/finances/{id}/edit", tx.getId()).with(csrf()).param("amount", "50")
                        .param("category", "Bancos").param("bank", "BBVA"))
                .andExpect(status().is3xxRedirection());
        assertThat(repository.findById(tx.getId()).orElseThrow().getBank()).isEqualTo("BBVA");

        mvc.perform(post("/finances/{id}/edit", tx.getId()).with(csrf()).param("amount", "50")
                        .param("category", "Servicios").param("bank", "BBVA"))
                .andExpect(status().is3xxRedirection());
        assertThat(repository.findById(tx.getId()).orElseThrow().getBank()).isNull();
    }

    @Test
    void acceptsCustomCategoryCreatedByClient() throws Exception {
        mvc.perform(post("/finances/categories").with(csrf()).param("name", "Mascotas"))
                .andExpect(status().is3xxRedirection());

        FinanceTransaction tx = movement();
        mvc.perform(post("/finances/{id}/edit", tx.getId()).with(csrf()).param("amount", "30")
                        .param("category", "Mascotas"))
                .andExpect(status().is3xxRedirection());
        assertThat(repository.findById(tx.getId()).orElseThrow().getCategory()).isEqualTo("Mascotas");
    }

    @Test
    void createsMovementWithCustomCategoryDirectly() throws Exception {
        mvc.perform(post("/finances").with(csrf()).param("amount", "95.50")
                        .param("category", "Gimnasio").param("type", "GASTO"))
                .andExpect(status().is3xxRedirection());
        FinanceTransaction created = repository.findAll().stream()
                .filter(tx -> "Gimnasio".equals(tx.getCategory())).findFirst().orElseThrow();
        assertThat(created.getAmount()).isEqualByComparingTo("95.50");
    }

    @Test
    void rejectsEmptyOrDuplicateCategory() throws Exception {
        mvc.perform(post("/finances/categories").with(csrf()).param("name", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("categoryError"));

        mvc.perform(post("/finances/categories").with(csrf()).param("name", "Vivienda"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("categoryError"));
    }

    @Test
    void monthViewShowsTransactionsBelongingToSelectedMonth() throws Exception {
        FinanceTransaction october = movement();
        october.setTransactionDate(LocalDate.of(2026, 10, 15));
        repository.saveAndFlush(october);

        FinanceTransaction november = movement();
        november.setTransactionDate(LocalDate.of(2026, 11, 2));
        repository.saveAndFlush(november);

        mvc.perform(get("/finances").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("transactions", org.hamcrest.Matchers.hasItem(october)))
                .andExpect(model().attribute("transactions", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(november))));

        mvc.perform(get("/finances").param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("transactions", org.hamcrest.Matchers.hasItem(november)))
                .andExpect(model().attribute("transactions", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(october))));
    }

    @Test
    void separateMonthAndYearFilterAcrossYears() throws Exception {
        FinanceTransaction tx = movement();
        tx.setTransactionDate(LocalDate.of(2025, 2, 10));
        repository.saveAndFlush(tx);
        mvc.perform(get("/finances").param("month", "2").param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedMonth", java.time.YearMonth.of(2025, 2)))
                .andExpect(model().attribute("transactions", org.hamcrest.Matchers.hasItem(tx)))
                .andExpect(content().string(containsString("id=\"year\"")));
        mvc.perform(get("/finances").param("month", "2").param("year", "2026"))
                .andExpect(model().attribute("transactions", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(tx))));
        mvc.perform(get("/finances").param("month", "13").param("year", "invalid"))
                .andExpect(model().attribute("selectedMonth", java.time.YearMonth.now()));
    }

    @Test
    void registrationDateIsAutomaticAndSeparateFromPaymentDate() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate paymentDate = today.plusMonths(2);
        mvc.perform(post("/finances").with(csrf()).param("amount", "50").param("category", "Otros")
                        .param("description", "Registration date test")
                        .param("transactionDate", paymentDate.toString())
                        .param("registrationDate", "2000-01-01"))
                .andExpect(status().is3xxRedirection());
        FinanceTransaction tx = repository.findAll().stream()
                .filter(t -> "Registration date test".equals(t.getDescription())).findFirst().orElseThrow();
        assertThat(tx.getRegistrationDate()).isEqualTo(today);
        assertThat(tx.getTransactionDate()).isEqualTo(paymentDate);
        mvc.perform(post("/finances/{id}/edit", tx.getId()).with(csrf()).param("amount", "60")
                        .param("category", "Otros").param("transactionDate", paymentDate.plusDays(1).toString())
                        .param("registrationDate", "2001-01-01"))
                .andExpect(status().is3xxRedirection());
        repository.flush();
        assertThat(repository.findById(tx.getId()).orElseThrow().getRegistrationDate()).isEqualTo(today);
        assertThat(repository.findById(tx.getId()).orElseThrow().getTransactionDate()).isEqualTo(paymentDate.plusDays(1));
    }

    @Test
    void successfulActionsProvideFeedbackAndValidationErrorsDoNot() throws Exception {
        FinanceTransaction tx = movement();
        mvc.perform(post("/finances/{id}/edit", tx.getId()).with(csrf()).param("amount", "75").param("category", "Otros"))
                .andExpect(flash().attribute("actionSuccess", "Los cambios se guardaron correctamente."));
        mvc.perform(post("/finances/{id}/delete", tx.getId()).with(csrf()))
                .andExpect(flash().attributeExists("actionSuccess"));
        mvc.perform(post("/finances/categories").with(csrf()).param("name", "Vivienda"))
                .andExpect(flash().attributeExists("categoryError"))
                .andExpect(flash().attributeCount(2));
        mvc.perform(post("/tasks").with(csrf()).param("title", "Feedback task"))
                .andExpect(flash().attributeExists("actionSuccess"));
        mvc.perform(post("/activities").with(csrf()).param("title", "Feedback activity")
                        .param("activityDate", "2026-10-24"))
                .andExpect(flash().attributeExists("actionSuccess"));
        mvc.perform(post("/goals").with(csrf()).param("name", "Feedback goal").param("targetAmount", "100"))
                .andExpect(flash().attributeExists("actionSuccess"));
        for (String page : java.util.List.of("/finances", "/tasks", "/activities", "/goals", "/")) {
            mvc.perform(get(page)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("/js/alerts.js")));
        }
    }

    @Test
    void undatedMovementBelongsToChosenMonthAndCanBeEdited() throws Exception {
        mvc.perform(post("/finances").with(csrf()).param("amount", "34").param("category", "Otros")
                        .param("description", "No payment date test").param("movementMonth", "2027-06"))
                .andExpect(redirectedUrl("/finances?month=2027-06"));
        FinanceTransaction tx = repository.findAll().stream()
                .filter(t -> "No payment date test".equals(t.getDescription())).findFirst().orElseThrow();
        assertThat(tx.getTransactionDate()).isNull();
        assertThat(tx.getRegistrationDate()).isEqualTo(LocalDate.now());
        assertThat(finance().findByMonth(java.time.YearMonth.of(2027, 6))).contains(tx);
        assertThat(finance().totalByTypeForMonth(TransactionType.GASTO, java.time.YearMonth.of(2027, 6)))
                .isEqualByComparingTo("34");
        mvc.perform(post("/finances/{id}/edit", tx.getId()).with(csrf()).param("amount", "34")
                        .param("category", "Otros").param("transactionDate", "2027-06-28"))
                .andExpect(redirectedUrl("/finances?month=2027-06"));
        tx = repository.findById(tx.getId()).orElseThrow();
        assertThat(tx.getTransactionDate()).isEqualTo(LocalDate.of(2027, 6, 28));
        mvc.perform(post("/finances/{id}/edit", tx.getId()).with(csrf()).param("amount", "34")
                        .param("category", "Otros").param("movementMonth", "2027-06"))
                .andExpect(redirectedUrl("/finances?month=2027-06"));
        tx = repository.findById(tx.getId()).orElseThrow();
        assertThat(tx.getTransactionDate()).isNull();
    }

    @Test
    void copiesToRemainingMonthsClampDatesAndDoNotRepeat() throws Exception {
        FinanceTransaction tx = movement();
        tx.setTransactionDate(LocalDate.of(2028, 1, 31));
        tx.setType(TransactionType.INGRESO);
        tx.setCategory("Bancos");
        tx.setBank("BBVA");
        finance().save(tx);
        mvc.perform(post("/finances/{id}/duplicate", tx.getId()).with(csrf()).param("scope", "REST_OF_YEAR"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("actionSuccess"));
        var copies = repository.findAll().stream().filter(t -> tx.getId().equals(t.getDuplicateSourceId())).toList();
        assertThat(copies).hasSize(11);
        FinanceTransaction february = copies.stream().filter(t -> t.getTransactionDate().getMonthValue() == 2).findFirst().orElseThrow();
        assertThat(february.getTransactionDate()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(february.getAmount()).isEqualByComparingTo(tx.getAmount());
        assertThat(february.getBank()).isEqualTo("BBVA");
        assertThat(february.getType()).isEqualTo(TransactionType.INGRESO);
        assertThat(february.getRegistrationDate()).isEqualTo(LocalDate.now());
        assertThat(finance().duplicate(tx.getId(), "REST_OF_YEAR")).isZero();
        assertThat(finance().duplicate(tx.getId(), "NEXT_MONTH")).isZero();
        assertThat(tx.getTransactionDate()).isEqualTo(LocalDate.of(2028, 1, 31));
    }

    @Test
    void copiesWithoutPaymentDateAcrossYearBoundary() {
        FinanceTransaction tx = movement();
        tx.setTransactionDate(null);
        tx.setAccountingMonth(LocalDate.of(2027, 12, 1));
        finance().save(tx);
        assertThat(finance().duplicate(tx.getId(), "REST_OF_YEAR")).isZero();
        assertThat(finance().duplicate(tx.getId(), "NEXT_MONTH")).isEqualTo(1);
        FinanceTransaction copy = repository.findAll().stream()
                .filter(t -> tx.getId().equals(t.getDuplicateSourceId())).findFirst().orElseThrow();
        assertThat(copy.getTransactionDate()).isNull();
        assertThat(copy.getMovementMonth()).isEqualTo("2028-01");
        assertThat(finance().findByMonth(java.time.YearMonth.of(2028, 1))).contains(copy);
    }

    @Test
    void rejectsInvalidAmountAndMissingMovement() throws Exception {
        FinanceTransaction tx = movement();
        mvc.perform(post("/finances/{id}/edit", tx.getId()).with(csrf()).param("amount", "0"))
                .andExpect(status().isBadRequest());
        assertThat(repository.findById(tx.getId()).orElseThrow().getAmount()).isEqualByComparingTo("40.00");
        mvc.perform(post("/finances/-1/edit").with(csrf()).param("amount", "10"))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateChainStepByStepOnlyAllowsDuplicatingFromCurrentStep() throws Exception {
        // Octubre 2026
        FinanceTransaction txOct = movement();
        txOct.setTransactionDate(LocalDate.of(2026, 10, 10));
        txOct.setDescription("Suscripcion Gym Octubre");
        finance().save(txOct);

        assertThat(finance().isDuplicatedToNextMonth(txOct)).isFalse();

        // Duplicar a Noviembre
        mvc.perform(post("/finances/{id}/duplicate", txOct.getId()).with(csrf()).param("scope", "NEXT_MONTH"))
                .andExpect(status().is3xxRedirection());

        FinanceTransaction txNov = repository.findAll().stream()
                .filter(t -> "2026-11".equals(t.getMovementMonth()) && txOct.getId().equals(t.getDuplicateSourceId()))
                .findFirst().orElseThrow();

        // Octubre ya fue duplicado a Noviembre -> deshabilitado
        assertThat(finance().isDuplicatedToNextMonth(txOct)).isTrue();
        // Noviembre NO ha sido duplicado a Diciembre -> habilitado
        assertThat(finance().isDuplicatedToNextMonth(txNov)).isFalse();

        // Intentar duplicar de nuevo desde Octubre a Noviembre debe retornar 0 y no duplicar
        assertThat(finance().duplicate(txOct.getId(), "NEXT_MONTH")).isZero();

        // Verificar que en la vista de Octubre el botón de txOct está deshabilitado
        mvc.perform(get("/finances").param("month", "10").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya fue duplicado al siguiente mes")))
                .andExpect(content().string(containsString("disabled")));

        // En la vista de Noviembre, txNov NO está deshabilitado
        mvc.perform(get("/finances").param("month", "11").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Duplicar al siguiente mes")));

        // Ahora desde Noviembre duplicamos a Diciembre
        mvc.perform(post("/finances/{id}/duplicate", txNov.getId()).with(csrf()).param("scope", "NEXT_MONTH"))
                .andExpect(status().is3xxRedirection());

        FinanceTransaction txDec = repository.findAll().stream()
                .filter(t -> "2026-12".equals(t.getMovementMonth()) && txOct.getId().equals(t.getDuplicateSourceId()))
                .findFirst().orElseThrow();

        // Ahora Noviembre también está deshabilitado porque ya está en Diciembre
        assertThat(finance().isDuplicatedToNextMonth(txNov)).isTrue();
        // Diciembre está habilitado para Enero
        assertThat(finance().isDuplicatedToNextMonth(txDec)).isFalse();
    }

    @Test
    void sortAmountsAscendingAndDescending() throws Exception {
        java.time.YearMonth testMonth = java.time.YearMonth.of(2026, 4);

        FinanceTransaction t1 = new FinanceTransaction();
        t1.setAmount(new BigDecimal("120.00"));
        t1.setDescription("Monto mediano");
        t1.setCategory("Otros");
        t1.setTransactionDate(testMonth.atDay(5));
        finance().save(t1);

        FinanceTransaction t2 = new FinanceTransaction();
        t2.setAmount(new BigDecimal("25.00"));
        t2.setDescription("Monto chico");
        t2.setCategory("Otros");
        t2.setTransactionDate(testMonth.atDay(10));
        finance().save(t2);

        FinanceTransaction t3 = new FinanceTransaction();
        t3.setAmount(new BigDecimal("900.00"));
        t3.setDescription("Monto grande");
        t3.setCategory("Otros");
        t3.setTransactionDate(testMonth.atDay(15));
        finance().save(t3);

        // Mayor a menor (desc)
        List<FinanceTransaction> descList = finance().findByMonth(testMonth, "desc");
        assertThat(descList).isNotEmpty();
        assertThat(descList.get(0).getAmount()).isEqualByComparingTo("900.00");
        assertThat(descList.get(1).getAmount()).isEqualByComparingTo("120.00");
        assertThat(descList.get(2).getAmount()).isEqualByComparingTo("25.00");

        // Menor a mayor (asc)
        List<FinanceTransaction> ascList = finance().findByMonth(testMonth, "asc");
        assertThat(ascList).isNotEmpty();
        assertThat(ascList.get(0).getAmount()).isEqualByComparingTo("25.00");
        assertThat(ascList.get(1).getAmount()).isEqualByComparingTo("120.00");
        assertThat(ascList.get(2).getAmount()).isEqualByComparingTo("900.00");

        // MVC model check
        mvc.perform(get("/finances").param("month", "4").param("year", "2026").param("sortAmount", "desc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sortAmount", "desc"))
                .andExpect(content().string(containsString("bi-sort-numeric-down-alt")));

        mvc.perform(get("/finances").param("month", "4").param("year", "2026").param("sortAmount", "asc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sortAmount", "asc"))
                .andExpect(content().string(containsString("bi-sort-numeric-down")));
    }

    @Test
    void paginatesMovementsByTenAndFormatsPaymentDates() throws Exception {
        java.time.YearMonth testMonth = java.time.YearMonth.of(2030, 7);
        for (int day = 1; day <= 12; day++) {
            FinanceTransaction tx = new FinanceTransaction();
            tx.setAmount(BigDecimal.valueOf(day));
            tx.setDescription("Movimiento paginado " + day);
            tx.setCategory("Otros");
            tx.setTransactionDate(testMonth.atDay(day));
            finance().save(tx);
        }

        mvc.perform(get("/finances").param("month", "7").param("year", "2030").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("transactions", org.hamcrest.Matchers.hasSize(10)))
                .andExpect(model().attribute("totalTransactions", 12))
                .andExpect(model().attribute("currentPage", 1))
                .andExpect(model().attribute("totalPages", 2))
                .andExpect(content().string(containsString("12/07/2030")))
                .andExpect(content().string(containsString("Mostrando")))
                .andExpect(content().string(containsString("Página <strong>1</strong> de <strong>2</strong>")));

        mvc.perform(get("/finances").param("month", "7").param("year", "2030").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("transactions", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("pageStart", 11))
                .andExpect(model().attribute("pageEnd", 12))
                .andExpect(content().string(containsString("02/07/2030")));
    }

    @Test
    void cardHeadersAndDashForMissingPaymentDate() throws Exception {
        FinanceTransaction tx = new FinanceTransaction();
        tx.setAmount(new BigDecimal("80.00"));
        tx.setDescription("Transaccion sin fecha de pago");
        tx.setCategory("Servicios");
        tx.setTransactionDate(null);
        tx.setAccountingMonth(LocalDate.of(2026, 3, 1));
        finance().save(tx);

        mvc.perform(get("/finances").param("month", "3").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<th style=\"width: 50px;\" class=\"text-center\">#</th>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Fecha Registro"))))
                .andExpect(content().string(containsString("<th>Fecha de pago</th>")))
                .andExpect(content().string(containsString("<th>Detalle</th>")))
                .andExpect(content().string(containsString("<th>Categoría</th>")))
                .andExpect(content().string(containsString("<th>Tipo</th>")))
                .andExpect(content().string(containsString("Monto")))
                .andExpect(content().string(containsString("Acciones")))
                // Muestra un guion '-' para los que son sin fecha de pago
                .andExpect(content().string(containsString(
                        "<td class=\"finance-cell-date\" data-label=\"Fecha de pago\">-</td>")));
    }
}
