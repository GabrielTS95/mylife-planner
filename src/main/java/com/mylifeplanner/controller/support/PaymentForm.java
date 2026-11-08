package com.mylifeplanner.controller.support;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class PaymentForm {
    @NotBlank(message = "Indica el concepto del pago.") @Size(max = 120)
    private String description;
    @NotBlank(message = "Indica a quién debes pagar.") @Size(max = 120)
    private String creditor;
    @NotNull(message = "Indica el importe de cada cuota.") @DecimalMin("0.01") @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;
    @NotNull(message = "Indica la fecha de vencimiento.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;
    @Min(1) @Max(120)
    private int installments = 1;

    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getCreditor() { return creditor; }
    public void setCreditor(String value) { creditor = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate value) { dueDate = value; }
    public int getInstallments() { return installments; }
    public void setInstallments(int value) { installments = value; }
}
