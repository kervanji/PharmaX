package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_batches",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "batch_number"}))
public class ProductBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "batch_number", nullable = false)
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "quantity", nullable = false)
    private Double quantity;

    @Column(name = "original_quantity", nullable = false)
    private Double originalQuantity;

    @Column(name = "unit_cost")
    private Double unitCost;

    @Column(name = "currency")
    private String currency;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_customer_id")
    private Customer supplierCustomer;

    @Column(name = "is_opening_batch")
    private Boolean isOpeningBatch;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ProductBatch() {
        this.quantity = 0.0;
        this.originalQuantity = 0.0;
        this.currency = "دينار";
        this.isOpeningBatch = false;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Double getQuantity() {
        return quantity != null ? quantity : 0.0;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public Double getOriginalQuantity() {
        return originalQuantity != null ? originalQuantity : 0.0;
    }

    public void setOriginalQuantity(Double originalQuantity) {
        this.originalQuantity = originalQuantity;
    }

    public Double getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(Double unitCost) {
        this.unitCost = unitCost;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Customer getSupplierCustomer() {
        return supplierCustomer;
    }

    public void setSupplierCustomer(Customer supplierCustomer) {
        this.supplierCustomer = supplierCustomer;
    }

    public Boolean getIsOpeningBatch() {
        return isOpeningBatch;
    }

    public void setIsOpeningBatch(Boolean openingBatch) {
        isOpeningBatch = openingBatch;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isExpiredOn(LocalDate date) {
        return expiryDate != null && date != null && expiryDate.isBefore(date);
    }
}
