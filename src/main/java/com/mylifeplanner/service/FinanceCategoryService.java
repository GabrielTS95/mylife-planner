package com.mylifeplanner.service;

import com.mylifeplanner.model.FinanceCategory;
import com.mylifeplanner.model.AppPage;
import com.mylifeplanner.repository.FinanceCategoryRepository;
import com.mylifeplanner.repository.FinanceTransactionRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;

@Service
public class FinanceCategoryService {
    private final FinanceCategoryRepository repository;
    private final FinanceTransactionRepository transactions;

    private final AccountService account;

    public FinanceCategoryService(FinanceCategoryRepository repository, FinanceTransactionRepository transactions, AccountService account) {
        this.account = account;
        this.repository = repository;
        this.transactions = transactions;
    }

    public List<String> findAll() {
        account.requireAccess(AppPage.FINANCES);
        List<String> names = new ArrayList<>(List.of("Vivienda", "Alimentacion", "Transporte", "Salud / Gym",
                "Estudios", "Ocio", "Ahorro", "Salario", "Negocio", "Otros"));
        repository.findAllByUserId(account.currentUser().getId()).forEach(category -> addName(names, category.getName()));
        transactions.findAllByUserId(account.currentUser().getId()).forEach(tx -> addName(names, tx.getCategory()));
        return names;
    }

    private void addName(List<String> names, String name) {
        if (name != null && !name.isBlank() && names.stream().noneMatch(n -> n.equalsIgnoreCase(name.strip()))) {
            names.add(name.strip());
        }
    }

    public String create(String value) {
        String name = value == null ? "" : value.strip();
        if (name.isEmpty() || name.length() > 100) {
            throw new IllegalArgumentException("Ingresa una categoría de entre 1 y 100 caracteres.");
        }
        if (findAll().stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) {
            throw new IllegalArgumentException("Esta categoría ya existe.");
        }
        FinanceCategory category = new FinanceCategory();
        category.setName(name);
        category.setUser(account.currentUser());
        repository.save(category);
        return name;
    }
}
