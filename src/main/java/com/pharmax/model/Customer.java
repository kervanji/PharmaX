package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "customer_code", nullable = false, unique = true)
    private String customerCode;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column
    private String address;
    
    @Column(name = "project_location")
    private String projectLocation;
    
    @Deprecated
    @Column(name = "email")
    private String email;
    
    @Deprecated
    @Column(name = "tax_id")
    private String taxId;
    
    @Deprecated
    @Column(name = "credit_limit")
    private Double creditLimit;
    
    @Column(name = "current_balance")
    private Double currentBalance;
    
    @Column(name = "balance_iqd")
    private Double balanceIqd;
    
    @Column(name = "balance_usd")
    private Double balanceUsd;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Sale> sales;
    

    public Customer() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.currentBalance = 0.0;
        this.balanceIqd = 0.0;
        this.balanceUsd = 0.0;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public String getProjectLocation() { return projectLocation; }
    public void setProjectLocation(String projectLocation) { this.projectLocation = projectLocation; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    
    public Double getCreditLimit() { return creditLimit; }
    public void setCreditLimit(Double creditLimit) { this.creditLimit = creditLimit; }
    
    public Double getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(Double currentBalance) { this.currentBalance = currentBalance; }
    
    public Double getBalanceIqd() { return balanceIqd != null ? balanceIqd : 0.0; }
    public void setBalanceIqd(Double balanceIqd) { this.balanceIqd = balanceIqd; }
    
    public Double getBalanceUsd() { return balanceUsd != null ? balanceUsd : 0.0; }
    public void setBalanceUsd(Double balanceUsd) { this.balanceUsd = balanceUsd; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public List<Sale> getSales() { return sales; }
    public void setSales(List<Sale> sales) { this.sales = sales; }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
