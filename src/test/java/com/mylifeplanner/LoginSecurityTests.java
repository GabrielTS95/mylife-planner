package com.mylifeplanner;

import com.mylifeplanner.repository.AppUserRepository;
import com.mylifeplanner.repository.FinanceTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired FinanceTransactionRepository transactions;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    void anonymousVisitorsMustLogin() throws Exception {
        for (String path : java.util.List.of("/", "/finances", "/activities", "/tasks", "/goals")) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("type=\"password\"")));
        mvc.perform(get("/css/login.css")).andExpect(status().isOk());
    }

    @Test
    void credentialsAreCheckedAgainstDatabaseAndLogoutEndsSession() throws Exception {
        var account = users.findByUsername("erwintorr").orElseThrow();
        assertThat(account.getPasswordHash()).startsWith("$2a$12$");
        account.setPasswordHash(encoder.encode("test-password-only"));
        users.saveAndFlush(account);
        mvc.perform(formLogin().user("erwintorr").password("incorrect"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        var result = mvc.perform(formLogin().user("erwintorr").password("test-password-only"))
                .andExpect(authenticated().withUsername("erwintorr"))
                .andExpect(redirectedUrl("/")).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/finances").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout")).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void mutationsRequireCsrfAndCannotUseGet() throws Exception {
        var owner = user("erwintorr").roles("OWNER");
        Long id = transactions.findAll().getFirst().getId();
        mvc.perform(post("/finances/{id}/delete", id).with(owner)).andExpect(status().isForbidden());
        assertThat(transactions.existsById(id)).isTrue();
        mvc.perform(get("/finances/{id}/delete", id).with(owner)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/finances/{id}/delete", id).with(owner).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(transactions.existsById(id)).isFalse();
    }

    @Test
    void allPersonalTablesReferenceTheAccount() throws Exception {
        Long owner = users.findByUsername("erwintorr").orElseThrow().getId();
        for (String table : java.util.List.of("tasks", "activities", "goals", "finance_transactions", "finance_categories")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id IS NULL OR user_id <> ?", Long.class, owner)).isZero();
            assertThat(jdbc.queryForObject("SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = 'USER_ID'", String.class, table.toUpperCase())).isEqualTo("NO");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = ? AND CONSTRAINT_TYPE = 'FOREIGN KEY'", Long.class, table.toUpperCase())).isGreaterThan(0);
        }
        mvc.perform(post("/finances").with(user("erwintorr").roles("OWNER")).with(csrf())
                        .param("amount", "10").param("category", "Otros").param("description", "Owned transaction"))
                .andExpect(status().is3xxRedirection());
        assertThat(transactions.findAll().stream().filter(tx -> "Owned transaction".equals(tx.getDescription()))
                .findFirst().orElseThrow().getUser().getId()).isEqualTo(owner);
    }
}
