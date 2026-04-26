package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "sales")
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "sale_code", nullable = false, unique = true)
    private String saleCode;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    
    @Column(name = "sale_date")
    private LocalDateTime saleDate;
    
    @Column(name = "currency")
    private String currency;
    
    @Column(name = "project_location")
    private String projectLocation;
    
    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;
    
    @Column(name = "discount_amount", nullable = false)
    private Double discountAmount;
    
    @Column(name = "tax_amount", nullable = false)
    private Double taxAmount;
    
    @Column(name = "final_amount", nullable = false)
    private Double finalAmount;

    @Column(name = "paid_amount")
    private Double paidAmount;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    @Column(name = "payment_status")
    private String paymentStatus;
    
    @Column
    private String notes;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<SaleItem> saleItems;
    
    @OneToOne(mappedBy = "sale", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Receipt receipt;

    public Sale() {
        this.saleDate = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.discountAmount = 0.0;
        this.taxAmount = 0.0;
        this.paymentStatus = "PENDING";
        this.paidAmount = 0.0;
        this.currency = "دينار";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getSaleCode() { return saleCode; }
    public void setSaleCode(String saleCode) { this.saleCode = saleCode; }
    
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    
    public LocalDateTime getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDateTime saleDate) { this.saleDate = saleDate; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getProjectLocation() { return projectLocation; }
    public void setProjectLocation(String projectLocation) { this.projectLocation = projectLocation; }
    
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    
    public Double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(Double discountAmount) { this.discountAmount = discountAmount; }
    
    public Double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(Double taxAmount) { this.taxAmount = taxAmount; }
    
    public Double getFinalAmount() { return finalAmount; }
    public void setFinalAmount(Double finalAmount) { this.finalAmount = finalAmount; }

    public Double getPaidAmount() { return paidAmount; }
    public void setPaidAmount(Double paidAmount) { this.paidAmount = paidAmount; }
    
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public List<SaleItem> getSaleItems() { return saleItems; }
    public void setSaleItems(List<SaleItem> saleItems) { this.saleItems = saleItems; }
    
    public Receipt getReceipt() { return receipt; }
    public void setReceipt(Receipt receipt) { this.receipt = receipt; }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
