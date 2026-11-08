package com.mylifeplanner.model;

public enum AppPage {
    HOME("Home", "/"), FINANCES("Movimientos", "/finances"), GOALS("Metas", "/goals"),
    TASKS("Tareas", "/tasks"), ACTIVITIES("Mi semana", "/activities");

    private final String label;
    private final String path;
    AppPage(String label, String path) { this.label = label; this.path = path; }
    public String getLabel() { return label; }
    public String getPath() { return path; }
}
