package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * عنصر سند (مادة مرتبطة بسند دفع للمشتريات)
 */
@Entity
@Table(name = "voucher_items")
public class VoucherItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    private Product product;
    
    @Column(name = "product_name")
    private String productName; // في حال عدم وجود المنتج في النظام
    
    @Column(name = "quantity")
    private Double quantity;
    
    @Column(name = "unit_price")
    private Double unitPrice;
    
    @Column(name = "total_price")
    private Double totalPrice;
    
    @Column(name = "unit_of_measure")
    private String unitOfMeasure;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id")
    private ProductBatch batch;

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "expiration_date")
    private String expirationDate;
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "add_to_inventory")
    private Boolean addToInventory; // هل يتم إضافته للمخزون؟
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public VoucherItem() {
        this.createdAt = LocalDateTime.now();
        this.addToInventory = true;
        this.quantity = 1.0;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Voucher getVoucher() { return voucher; }
    public void setVoucher(Voucher voucher) { this.voucher = voucher; }
    
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    
    public String getProductName() { 
        if (product != null) {
            return product.getName();
        }
        return productName; 
    }
    public void setProductName(String productName) { this.productName = productName; }
    
    public Double getQuantity() { return quantity != null ? quantity : 0.0; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    
    public Double getUnitPrice() { return unitPrice != null ? unitPrice : 0.0; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
    
    public Double getTotalPrice() { return totalPrice != null ? totalPrice : 0.0; }
    public void setTotalPrice(Double totalPrice) { this.totalPrice = totalPrice; }
    
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }

    public ProductBatch getBatch() { return batch; }
    public void setBatch(ProductBatch batch) { this.batch = batch; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }

    public LocalDate getParsedExpirationDate() {
        if (expirationDate == null || expirationDate.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(expirationDate.trim());
    }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public Boolean getAddToInventory() { return addToInventory != null ? addToInventory : true; }
    public void setAddToInventory(Boolean addToInventory) { this.addToInventory = addToInventory; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public void calculateTotal() {
        if (quantity != null && unitPrice != null) {
            this.totalPrice = quantity * unitPrice;
        }
    }
}
