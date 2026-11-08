package com.mylifeplanner.model;

import jakarta.persistence.*;

@Entity
@Table(name = "finance_categories")
public class FinanceCategory extends UserOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
