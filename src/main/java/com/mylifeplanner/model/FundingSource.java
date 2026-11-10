package com.mylifeplanner.model;

public enum FundingSource {
    MONTHLY_BUDGET("Presupuesto mensual", true),
    SAVINGS("Ahorros", false),
    EMERGENCY_FUND("Fondo de emergencia", false),
    OTHER_FUNDS("Otros fondos", false);

    private final String label;
    private final boolean affectsMonthlyBudget;

    FundingSource(String label, boolean affectsMonthlyBudget) {
        this.label = label;
        this.affectsMonthlyBudget = affectsMonthlyBudget;
    }

    public String getLabel() {
        return label;
    }

    public boolean isAffectsMonthlyBudget() {
        return affectsMonthlyBudget;
    }
}
