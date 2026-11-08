package com.mylifeplanner.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Entity
@Table(name = "goals")
public class Goal extends UserOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank private String name;
    @DecimalMin("0.01") private BigDecimal targetAmount;
    private BigDecimal currentAmount = BigDecimal.ZERO;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }
    public BigDecimal getCurrentAmount() { return currentAmount; }
    public void setCurrentAmount(BigDecimal currentAmount) { this.currentAmount = currentAmount; }
    public int getProgressPercent() {
        if (targetAmount == null || targetAmount.signum() <= 0 || currentAmount == null) return 0;
        return currentAmount.multiply(BigDecimal.valueOf(100)).divide(targetAmount, 0, java.math.RoundingMode.HALF_UP).min(BigDecimal.valueOf(100)).intValue();
    }
}
