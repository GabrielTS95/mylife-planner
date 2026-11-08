package com.mylifeplanner.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "finance_transactions", uniqueConstraints = @UniqueConstraint(columnNames = {"duplicate_source_id", "accounting_month"}))
public class FinanceTransaction extends UserOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull @DecimalMin("0.01") private BigDecimal amount;
    private String description;
    private String category;
    private String bank;
    public static final java.util.List<String> BANKS = java.util.List.of("BBVA", "BCP", "INTERBANK", "SCOTIABANK", "PICHINCHA");
    // Fecha de pago indicada por el usuario.
    private LocalDate transactionDate;
    private LocalDate accountingMonth;
    private Long duplicateSourceId;

    public LocalDate getAccountingMonth() { return accountingMonth; }
    public void setAccountingMonth(LocalDate value) { accountingMonth = value; }
    public Long getDuplicateSourceId() { return duplicateSourceId; }
    public void setDuplicateSourceId(Long value) { duplicateSourceId = value; }
    public LocalDate getReportingDate() {
        if (transactionDate != null) return transactionDate;
        if (accountingMonth != null) return accountingMonth;
        return registrationDate;
    }
    public String getMovementMonth() {
        LocalDate date = getReportingDate();
        return java.time.YearMonth.from(date == null ? LocalDate.now() : date).toString();
    }
    // Los movimientos anteriores pueden no tener una fecha de registro conocida.
    @Column(name = "registration_date", updatable = false)
    private LocalDate registrationDate;

    @PrePersist
    private void registerCreationDate() {
        if (registrationDate == null) {
            registrationDate = LocalDate.now();
        }
    }

    public LocalDate getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(LocalDate registrationDate) { this.registrationDate = registrationDate; }
    @Enumerated(EnumType.STRING) private TransactionType type = TransactionType.GASTO;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public TransactionType getType() { return type == null ? TransactionType.GASTO : type; }
    public void setType(TransactionType type) { this.type = type; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FinanceTransaction that = (FinanceTransaction) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
