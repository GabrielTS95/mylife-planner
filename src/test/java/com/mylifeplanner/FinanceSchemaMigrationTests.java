package com.mylifeplanner;

import com.mylifeplanner.model.TransactionType;
import com.mylifeplanner.repository.FinanceTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:legacy-finance;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:legacy-finance-schema.sql"
})
@AutoConfigureMockMvc
@WithMockUser(username = "erwintorr", roles = "OWNER")
class FinanceSchemaMigrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private FinanceTransactionRepository repository;
    @Autowired private CommandLineRunner seed;
    @Autowired private com.mylifeplanner.repository.AppUserRepository users;
    @Autowired private com.mylifeplanner.repository.FinanceCategoryRepository categories;

    @Test
    void legacyDatabaseAcceptsSavingsAndPreservesMovementsAcrossRepeatedInitialization() throws Exception {
        var income = repository.findById(1001L).orElseThrow();
        assertThat(income.getType()).isEqualTo(TransactionType.INGRESO);
        assertThat(income.getAmount()).isEqualByComparingTo("1500.00");
        assertThat(income.getDescription()).isEqualTo("Ingreso anterior");
        var expense = repository.findById(1002L).orElseThrow();
        assertThat(expense.getType()).isEqualTo(TransactionType.GASTO);
        assertThat(expense.getAmount()).isEqualByComparingTo("250.00");
        assertThat(expense.getDescription()).isEqualTo("Gasto anterior");

        mvc.perform(post("/finances").with(csrf())
                        .param("type", "AHORRO").param("amount", "300.00")
                        .param("description", "Ahorro tras migracion")
                        .param("transactionDate", "2026-11-07"))
                .andExpect(redirectedUrl("/finances?month=2026-11"));

        // Repeating startup must also preserve savings already stored in the new schema.
        seed.run();

        assertThat(repository.count()).isEqualTo(3);
        assertThat(repository.findById(1001L).orElseThrow().getType()).isEqualTo(TransactionType.INGRESO);
        assertThat(repository.findById(1002L).orElseThrow().getType()).isEqualTo(TransactionType.GASTO);
        var saving = repository.findAll().stream()
                .filter(tx -> tx.getType() == TransactionType.AHORRO).findFirst().orElseThrow();
        assertThat(saving.getAmount()).isEqualByComparingTo("300.00");
        assertThat(saving.getDescription()).isEqualTo("Ahorro tras migracion");
        assertThat(saving.getCategory()).isEqualTo("Ahorro");
        assertThat(users.findByUsername("erwintorr").orElseThrow().isAdministrator()).isTrue();
        var secondUser = new com.mylifeplanner.model.AppUser();
        secondUser.setUsername("migration.member");
        secondUser.setPasswordHash("test-only-unused-hash");
        users.saveAndFlush(secondUser);
        var sharedName = new com.mylifeplanner.model.FinanceCategory();
        sharedName.setUser(secondUser);
        sharedName.setName("Legacy category");
        categories.saveAndFlush(sharedName);
        assertThat(categories.count()).isEqualTo(2);
    }
}
