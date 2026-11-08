package com.mylifeplanner.controller.support;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class CycleSettingsForm {
    @Min(1) @Max(31)
    private int payday = 28;
    @NotNull @Digits(integer = 12, fraction = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;
    @PastOrPresent(message = "La fecha del saldo inicial no puede ser futura.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate openingDate;

    public int getPayday() { return payday; }
    public void setPayday(int value) { payday = value; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal value) { openingBalance = value; }
    public LocalDate getOpeningDate() { return openingDate; }
    public void setOpeningDate(LocalDate value) { openingDate = value; }
}
