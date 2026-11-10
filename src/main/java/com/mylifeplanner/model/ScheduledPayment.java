package com.mylifeplanner.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "scheduled_payments")
@AssociationOverride(name = "user", joinColumns = @JoinColumn(name = "user_id", nullable = false))
public class ScheduledPayment extends UserOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String description;
    @Column(nullable = false, length = 120)
    private String creditor;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false)
    private LocalDate dueDate;
    private int installmentNumber;
    private int installmentCount;
    @Enumerated(EnumType.STRING)
    @Column(name = "funding_source")
    private FundingSource fundingSource = FundingSource.MONTHLY_BUDGET;
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transaction_id", unique = true)
    private FinanceTransaction transaction;

    public Long getId() { return id; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getCreditor() { return creditor; }
    public void setCreditor(String value) { creditor = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate value) { dueDate = value; }
    public int getInstallmentNumber() { return installmentNumber; }
    public void setInstallmentNumber(int value) { installmentNumber = value; }
    public int getInstallmentCount() { return installmentCount; }
    public void setInstallmentCount(int value) { installmentCount = value; }
    public FundingSource getFundingSource() {
        return fundingSource == null ? FundingSource.MONTHLY_BUDGET : fundingSource;
    }
    public void setFundingSource(FundingSource value) {
        fundingSource = value == null ? FundingSource.MONTHLY_BUDGET : value;
    }
    public FinanceTransaction getTransaction() { return transaction; }
    public void setTransaction(FinanceTransaction value) { transaction = value; }
    public boolean isPaid() { return transaction != null; }
    public boolean isPaidOn(LocalDate date) {
        return transaction != null && !transaction.getTransactionDate().isAfter(date);
    }
}
