package com.mylifeplanner.model;

public enum TransactionType {
    INGRESO("Ingreso", "income"),
    GASTO("Gasto", "expense"),
    AHORRO("Ahorro", "saving");

    private final String label;
    private final String cssClass;

    TransactionType(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }

    public String getLabel() {
        return label;
    }

    public String getCssClass() {
        return cssClass;
    }
}
