package com.pedropalazon.sales_manager_backend.customers.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Customer() {
    }

    public Customer(String name, String phone, String notes) {
        update(name, phone, notes);
    }

    public void update(String name, String phone, String notes) {
        this.name = name.trim();
        this.phone = normalizePhone(phone);
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    // El teléfono se guarda sin separadores para poder buscarlo y detectar duplicados
    // aunque se escriba con otro formato ("+34 600-123-456" y "+34600123456" son el mismo)
    public static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digitsAndPlus = phone.replaceAll("[\\s.()-]", "");
        return digitsAndPlus.isEmpty() ? null : digitsAndPlus;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
