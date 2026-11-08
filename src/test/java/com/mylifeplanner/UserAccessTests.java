package com.mylifeplanner;

import com.mylifeplanner.model.*;
import com.mylifeplanner.repository.*;
import com.mylifeplanner.service.UserManagementService.TemporaryCredential;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

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
class UserAccessTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired FinanceTransactionRepository finances;
    @Autowired TaskRepository tasks;
    @Autowired ActivityRepository activities;
    @Autowired GoalRepository goals;
    @Autowired FinanceCategoryRepository categories;
    @Autowired PasswordEncoder encoder;
    private static final String PASSWORD = "member-test-password";

    private AppUser member(String name, AppPage... pages) {
        AppUser user = new AppUser();
        user.setUsername(name);
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.setPages(Set.of(pages));
        return users.saveAndFlush(user);
    }
    private MockHttpSession login(String name, String password, String redirect) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin().user(name).password(password))
                .andExpect(authenticated().withUsername(name)).andExpect(redirectedUrl(redirect))
                .andReturn().getRequest().getSession(false);
    }
    private TemporaryCredential createTemporary(String name) throws Exception {
        var result = mvc.perform(post("/settings/users").with(user("erwintorr")).with(csrf())
                        .param("username", name).param("pages", "TASKS"))
                .andExpect(redirectedUrl("/settings")).andExpect(flash().attributeExists("temporaryCredential"))
                .andReturn();
        return (TemporaryCredential) result.getFlashMap().get("temporaryCredential");
    }

    @Test
    void administratorCreatesUsersWithOneTimeCredentialsAndPreservesItsOwnAccess() throws Exception {
        mvc.perform(get("/settings").with(user("erwintorr")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Agregar usuario")));
        var credential = createTemporary("New.Member");
        AppUser created = users.findByUsername("new.member").orElseThrow();
        assertThat(created.isAdministrator()).isFalse();
        assertThat(created.getPages()).containsExactly(AppPage.TASKS);
        assertThat(created.isMustChangePassword()).isTrue();
        assertThat(encoder.matches(credential.password(), created.getPasswordHash())).isTrue();
        assertThat(credential.expiresAt()).isAfter(Instant.now());
        mvc.perform(get("/settings").with(user("erwintorr")).flashAttr("temporaryCredential", credential))
                .andExpect(status().isOk()).andExpect(content().string(containsString(credential.password())))
                .andExpect(content().string(not(containsString(created.getPasswordHash()))));
        mvc.perform(get("/settings").with(user("erwintorr")))
                .andExpect(content().string(not(containsString(credential.password()))));
        mvc.perform(post("/settings/users").with(user("erwintorr")).with(csrf()).param("username", "NEW.MEMBER"))
                .andExpect(flash().attributeExists("settingsError"));
        Long ownerId = users.findByUsername("erwintorr").orElseThrow().getId();
        mvc.perform(post("/settings/users/{id}/access", ownerId).with(user("erwintorr")).with(csrf()))
                .andExpect(flash().attributeExists("settingsError"));
        assertThat(users.findById(ownerId).orElseThrow().isEnabled()).isTrue();
        mvc.perform(post("/settings/users/{id}/password", ownerId).with(user("erwintorr")).with(csrf()))
                .andExpect(flash().attributeExists("settingsError"));
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeAnyModuleAndCannotBeReused() throws Exception {
        var credential = createTemporary("temporary.member");
        var session = login(credential.username(), credential.password(), "/account/password");
        mvc.perform(get("/tasks").session(session)).andExpect(redirectedUrl("/account/password"));
        mvc.perform(post("/tasks").session(session).with(csrf()).param("title", "No crear"))
                .andExpect(redirectedUrl("/account/password"));
        mvc.perform(get("/account/password").session(session)).andExpect(status().isOk());
        mvc.perform(post("/account/password").session(session).with(csrf())
                        .param("currentPassword", credential.password()).param("newPassword", "permanent-passphrase")
                        .param("confirmation", "different-passphrase"))
                .andExpect(model().attributeExists("passwordError"));
        assertThat(users.findByUsername(credential.username()).orElseThrow().isMustChangePassword()).isTrue();
        mvc.perform(post("/account/password").session(session).with(csrf())
                        .param("currentPassword", credential.password()).param("newPassword", "permanent-passphrase")
                        .param("confirmation", "permanent-passphrase"))
                .andExpect(redirectedUrl("/login?passwordChanged"));
        assertThat(session.isInvalid()).isTrue();
        assertThat(users.findByUsername(credential.username()).orElseThrow().isMustChangePassword()).isFalse();
        mvc.perform(formLogin().user(credential.username()).password(credential.password())).andExpect(unauthenticated());
        var newSession = login(credential.username(), "permanent-passphrase", "/tasks");
        mvc.perform(get("/tasks").session(newSession)).andExpect(status().isOk());
    }

    @Test
    void expiredAndDisabledCredentialsAreRejected() throws Exception {
        var credential = createTemporary("expired.member");
        AppUser expired = users.findByUsername(credential.username()).orElseThrow();
        expired.setTemporaryPasswordExpiresAt(Instant.now().minusSeconds(1));
        users.saveAndFlush(expired);
        mvc.perform(formLogin().user(credential.username()).password(credential.password())).andExpect(unauthenticated());
        AppUser disabled = member("disabled.member", AppPage.HOME);
        disabled.setEnabled(false);
        users.saveAndFlush(disabled);
        mvc.perform(formLogin().user(disabled.getUsername()).password(PASSWORD)).andExpect(unauthenticated());
    }

    @Test
    void permissionsProtectUrlsFormsMenusAndHomeRedirect() throws Exception {
        member("tasks.member", AppPage.TASKS);
        var session = login("tasks.member", PASSWORD, "/tasks");
        mvc.perform(get("/tasks").session(session)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/settings\""))))
                .andExpect(content().string(not(containsString("href=\"/finances\""))));
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/tasks"));
        for (String path : java.util.List.of("/settings", "/finances", "/goals", "/activities"))
            mvc.perform(get(path).session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/settings/users").session(session).with(csrf()).param("username", "intruder"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/finances").session(session).with(csrf()).param("amount", "100"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/settings/users").with(user("erwintorr")).param("username", "no.csrf"))
                .andExpect(status().isForbidden());
    }

    @Test
    void permissionChangesApplyToExistingSessionsAndResetRevokesThem() throws Exception {
        AppUser member = member("live.member", AppPage.TASKS);
        var session = login(member.getUsername(), PASSWORD, "/tasks");
        mvc.perform(post("/settings/users/{id}/access", member.getId()).with(user("erwintorr")).with(csrf())
                        .param("pages", "GOALS").param("enabled", "true"))
                .andExpect(redirectedUrl("/settings"));
        mvc.perform(get("/tasks").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/goals").session(session)).andExpect(status().isOk());
        var reset = mvc.perform(post("/settings/users/{id}/password", member.getId()).with(user("erwintorr")).with(csrf()))
                .andExpect(flash().attributeExists("temporaryCredential")).andReturn();
        mvc.perform(get("/goals").session(session)).andExpect(redirectedUrl("/login?expired"));
        assertThat(session.isInvalid()).isTrue();
        var credential = (TemporaryCredential) reset.getFlashMap().get("temporaryCredential");
        mvc.perform(formLogin().user(member.getUsername()).password(PASSWORD)).andExpect(unauthenticated());
        login(member.getUsername(), credential.password(), "/account/password");
    }

    @Test
    void disablingAUserRevokesExistingSessionAndEmptyPermissionsHaveALandingPage() throws Exception {
        AppUser member = member("empty.member");
        var session = login(member.getUsername(), PASSWORD, "/account");
        mvc.perform(get("/account").session(session)).andExpect(status().isOk());
        mvc.perform(post("/settings/users/{id}/access", member.getId()).with(user("erwintorr")).with(csrf()))
                .andExpect(redirectedUrl("/settings"));
        mvc.perform(get("/account").session(session)).andExpect(redirectedUrl("/login?expired"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void homeDoesNotRevealDisabledModulesEvenIfUserHasExistingData() throws Exception {
        AppUser member = member("home.member", AppPage.HOME);
        FinanceTransaction tx = new FinanceTransaction();
        tx.setUser(member); tx.setAmount(new BigDecimal("98765")); tx.setDescription("Hidden finance detail");
        finances.saveAndFlush(tx);
        mvc.perform(get("/").with(user(member.getUsername())))
                .andExpect(status().isOk()).andExpect(model().attribute("transactions", empty()))
                .andExpect(content().string(not(containsString("98765"))))
                .andExpect(content().string(not(containsString("Hidden finance detail"))))
                .andExpect(content().string(not(containsString("id=\"section-finances\""))));
    }

    @Test
    void dataOwnershipPreventsReadingEditingDeletingAndForgedOwnershipAcrossModules() throws Exception {
        AppUser owner = users.findByUsername("erwintorr").orElseThrow();
        AppUser member = member("isolated.member", AppPage.values());
        FinanceTransaction finance = finances.findAllByUserId(owner.getId()).getFirst();
        Task task = tasks.findAllByUserId(owner.getId()).getFirst();
        Activity activity = activities.findAllByUserId(owner.getId()).getFirst();
        Goal goal = goals.findAllByUserId(owner.getId()).getFirst();
        FinanceCategory category = new FinanceCategory();
        category.setUser(owner); category.setName("Owner private category"); categories.saveAndFlush(category);
        var identity = user(member.getUsername());
        mvc.perform(get("/finances").with(identity)).andExpect(model().attribute("transactions", empty()))
                .andExpect(model().attribute("recentTransactions", empty()))
                .andExpect(content().string(not(containsString("Owner private category"))));
        mvc.perform(get("/tasks").with(identity)).andExpect(model().attribute("tasks", empty()));
        mvc.perform(get("/goals").with(identity)).andExpect(model().attribute("goals", empty()));
        mvc.perform(post("/finances/{id}/edit", finance.getId()).with(identity).with(csrf()).param("amount", "1"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/finances/{id}/duplicate", finance.getId()).with(identity).with(csrf()).param("scope", "NEXT_MONTH"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/tasks").with(identity).with(csrf()).param("id", task.getId().toString()).param("title", "stolen"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/goals").with(identity).with(csrf()).param("id", goal.getId().toString()).param("name", "stolen"))
                .andExpect(status().isNotFound());
        for (var entry : java.util.Map.of("finances", finance.getId(), "tasks", task.getId(), "activities", activity.getId(), "goals", goal.getId()).entrySet())
            mvc.perform(post("/{module}/{id}/delete", entry.getKey(), entry.getValue()).with(identity).with(csrf()))
                    .andExpect(status().isNotFound());
        mvc.perform(post("/finances").with(identity).with(csrf()).param("amount", "20").param("description", "Own saving")
                        .param("type", "AHORRO").param("user.id", owner.getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(finances.findAllByUserId(member.getId())).hasSize(1);
        assertThat(finances.findAllByUserId(member.getId()).getFirst().getUser().getId()).isEqualTo(member.getId());
        assertThat(finances.findById(finance.getId()).orElseThrow().getUser().getId()).isEqualTo(owner.getId());
    }
}
