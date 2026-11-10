package com.mylifeplanner.config;

import com.mylifeplanner.model.*;
import com.mylifeplanner.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner seed(JdbcTemplate jdbcTemplate, TaskRepository tasks, ActivityRepository activities,
                           FinanceTransactionRepository transactions, GoalRepository goals, AppUserRepository users) {
        return args -> {
            AppUser owner = users.findByUsername("erwintorr").orElseGet(() -> {
                AppUser user = new AppUser();
                user.setUsername("erwintorr");
                user.setPasswordHash("$2a$12$fZFjQ1FbeIte5VQ7P01CiuG05cyltCTAMLhOU9XiRSOeL4FmcQYPK");
                return users.save(user);
            });
            // The original account remains the administrator; its password and records are preserved.
            if (!owner.isAdministrator()) {
                owner.setAdministrator(true);
                users.save(owner);
            }
            for (String table : java.util.List.of("tasks", "activities", "goals", "finance_transactions", "finance_categories")) {
                jdbcTemplate.update("UPDATE " + table + " SET user_id = ? WHERE user_id IS NULL", owner.getId());
                jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN user_id SET NOT NULL");
            }
            jdbcTemplate.update("UPDATE finance_transactions SET registration_date = COALESCE(transaction_date, accounting_month, CURRENT_DATE) WHERE registration_date IS NULL");
            jdbcTemplate.update("UPDATE finance_transactions SET funding_source = 'MONTHLY_BUDGET' WHERE funding_source IS NULL");
            jdbcTemplate.update("UPDATE finance_transactions SET automatic_carryover = FALSE WHERE automatic_carryover IS NULL");
            jdbcTemplate.update("UPDATE scheduled_payments SET funding_source = 'MONTHLY_BUDGET' WHERE funding_source IS NULL");
            migrateLegacyTaskStatuses(jdbcTemplate);
            migrateFinanceTransactionTypes(jdbcTemplate);
            migrateCategoryOwnership(jdbcTemplate);

            if (tasks.count() == 0) {
                Task t = new Task(); t.setTitle("Revisar pendientes de la semana"); t.setDescription("Ordenar actividades importantes"); t.setDueDate(LocalDate.now().plusDays(1)); t.setPriority(Priority.ALTA); t.setUser(owner); tasks.save(t);
            }
            if (activities.count() == 0) {
                Activity a = new Activity(); a.setTitle("Bloque de planificación semanal"); a.setCategory("Personal"); a.setActivityDate(LocalDate.now()); a.setStartTime(LocalTime.of(20,0)); a.setEndTime(LocalTime.of(20,30)); a.setUser(owner); activities.save(a);
            }
            if (transactions.count() == 0) {
                FinanceTransaction i = new FinanceTransaction(); i.setType(TransactionType.INGRESO); i.setAmount(new BigDecimal("8500.00")); i.setCategory("Salario"); i.setDescription("Ingreso mensual"); i.setTransactionDate(LocalDate.now().withDayOfMonth(1)); i.setUser(owner); transactions.save(i);
                FinanceTransaction g = new FinanceTransaction(); g.setType(TransactionType.GASTO); g.setAmount(new BigDecimal("1000.00")); g.setCategory("Vivienda"); g.setDescription("Alquiler y servicios"); g.setTransactionDate(LocalDate.now().withDayOfMonth(2)); g.setUser(owner); transactions.save(g);
                FinanceTransaction a = new FinanceTransaction(); a.setType(TransactionType.AHORRO); a.setAmount(new BigDecimal("700.00")); a.setCategory("Ahorro"); a.setDescription("Ahorro mensual"); a.setTransactionDate(LocalDate.now().withDayOfMonth(3)); a.setUser(owner); transactions.save(a);
            }
            if (goals.count() == 0) {
                Goal g = new Goal(); g.setName("Fondo de ahorro"); g.setTargetAmount(new BigDecimal("10000")); g.setCurrentAmount(new BigDecimal("3500")); g.setUser(owner); goals.save(g);
            }
        };
    }

    private void migrateCategoryOwnership(JdbcTemplate jdbcTemplate) {
        // Older databases required globally unique category names. Users may now share a name.
        var legacyConstraints = jdbcTemplate.queryForList("""
                SELECT tc.CONSTRAINT_NAME
                  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                  JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kc
                    ON kc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME AND kc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
                 WHERE tc.TABLE_SCHEMA = CURRENT_SCHEMA AND tc.TABLE_NAME = 'FINANCE_CATEGORIES'
                   AND tc.CONSTRAINT_TYPE = 'UNIQUE'
                 GROUP BY tc.CONSTRAINT_NAME
                HAVING COUNT(*) = 1 AND MAX(kc.COLUMN_NAME) = 'NAME'
                """, String.class);
        for (String constraint : legacyConstraints) {
            jdbcTemplate.execute("ALTER TABLE finance_categories DROP CONSTRAINT \"" + constraint.replace("\"", "\"\"") + "\"");
        }
    }

    private void migrateFinanceTransactionTypes(JdbcTemplate jdbcTemplate) {
        // Hibernate's schema update does not expand existing H2 ENUM columns.
        // Keep stored names and allow every current type before seeding or saving movements.
        String allowedTypes = java.util.Arrays.stream(TransactionType.values())
                .map(Enum::name)
                .sorted()
                .map(name -> "'" + name + "'")
                .collect(java.util.stream.Collectors.joining(", "));
        jdbcTemplate.execute("ALTER TABLE finance_transactions ALTER COLUMN type ENUM(" + allowedTypes + ")");
    }

    private void migrateLegacyTaskStatuses(JdbcTemplate jdbcTemplate) {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'TASKS'",
                Integer.class);

        if (tableCount == null || tableCount == 0) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE tasks ALTER COLUMN status VARCHAR(30)");
        jdbcTemplate.update("""
                UPDATE tasks
                   SET status = CASE status
                       WHEN 'PENDIENTE' THEN 'POR_HACER'
                       WHEN 'EN_PROGRESO' THEN 'EN_PROCESO'
                       WHEN 'COMPLETADO' THEN 'FINALIZADO'
                       ELSE status
                   END
                 WHERE status IN ('PENDIENTE', 'EN_PROGRESO', 'COMPLETADO')
                """);
    }
}
