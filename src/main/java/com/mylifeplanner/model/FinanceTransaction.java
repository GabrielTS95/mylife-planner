package com.mylifeplanner.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "finance_transactions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"duplicate_source_id", "accounting_month"}),
        @UniqueConstraint(columnNames = {"user_id", "carryover_source_month"})
})
public class FinanceTransaction extends UserOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull @DecimalMin("0.01") private BigDecimal amount;
    @Column(length = 400)
    private String description;
    private String category;
    private String bank;
    @Enumerated(EnumType.STRING)
    @Column(name = "funding_source")
    private FundingSource fundingSource = FundingSource.MONTHLY_BUDGET;
    public static final java.util.List<String> BANKS = java.util.List.of("BBVA", "BCP", "INTERBANK", "SCOTIABANK", "PICHINCHA");
    // Fecha de pago indicada por el usuario.
    private LocalDate transactionDate;
    private LocalDate accountingMonth;
    private Long duplicateSourceId;
    private LocalDate carryoverSourceMonth;
    private Boolean automaticCarryover;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funding_injection_id")
    private FinanceTransaction fundingInjection;
    @OneToMany(mappedBy = "fundingInjection", fetch = FetchType.LAZY)
    @OrderBy("transactionDate ASC, id ASC")
    private List<FinanceTransaction> fundedExpenses = new ArrayList<>();

    public LocalDate getAccountingMonth() { return accountingMonth; }
    public void setAccountingMonth(LocalDate value) { accountingMonth = value; }
    public Long getDuplicateSourceId() { return duplicateSourceId; }
    public void setDuplicateSourceId(Long value) { duplicateSourceId = value; }
    public LocalDate getCarryoverSourceMonth() { return carryoverSourceMonth; }
    public void setCarryoverSourceMonth(LocalDate value) { carryoverSourceMonth = value; }
    public boolean isAutomaticCarryover() { return Boolean.TRUE.equals(automaticCarryover); }
    public void setAutomaticCarryover(boolean value) { automaticCarryover = value; }
    public boolean isCarryover() { return getType() == TransactionType.ARRASTRE; }
    public FinanceTransaction getFundingInjection() { return fundingInjection; }
    public void setFundingInjection(FinanceTransaction value) { fundingInjection = value; }
    public List<FinanceTransaction> getFundedExpenses() { return fundedExpenses; }
    public BigDecimal getAllocatedAmount() {
        return fundedExpenses.stream().map(FinanceTransaction::getAmount)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BigDecimal getUnallocatedAmount() {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.subtract(getAllocatedAmount());
        return value.max(BigDecimal.ZERO);
    }
    public String getFundedExpenseIdsCsv() {
        return fundedExpenses.stream().map(FinanceTransaction::getId).filter(java.util.Objects::nonNull)
                .map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
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
    public FundingSource getFundingSource() {
        return fundingSource == null ? FundingSource.MONTHLY_BUDGET : fundingSource;
    }
    public void setFundingSource(FundingSource fundingSource) {
        this.fundingSource = fundingSource == null ? FundingSource.MONTHLY_BUDGET : fundingSource;
    }
    public boolean isAffectsMonthlyBudget() {
        return getType() == TransactionType.GASTO && getFundingSource().isAffectsMonthlyBudget();
    }
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
