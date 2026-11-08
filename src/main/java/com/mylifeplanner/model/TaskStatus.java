package com.mylifeplanner.model;

public enum TaskStatus {
    POR_HACER("Por hacer", "todo"),
    EN_PROCESO("En proceso", "progress"),
    BLOQUEADO("Bloqueado", "blocked"),
    FINALIZADO("Finalizado", "done");

    private final String label;
    private final String cssClass;

    TaskStatus(String label, String cssClass) {
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
