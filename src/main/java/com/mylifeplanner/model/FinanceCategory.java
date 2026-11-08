package com.mylifeplanner.model;

import jakarta.persistence.*;

@Entity
@Table(name = "finance_categories", uniqueConstraints = @UniqueConstraint(name = "uk_finance_category_user_name", columnNames = {"user_id", "name"}))
public class FinanceCategory extends UserOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
