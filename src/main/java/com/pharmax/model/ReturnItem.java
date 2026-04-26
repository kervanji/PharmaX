package com.pharmax.model;

import javax.persistence.*;

@Entity
@Table(name = "return_items")
public class ReturnItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    private SaleReturn saleReturn;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_sale_item_id")
    private SaleItem originalSaleItem;
    
    @Column(nullable = false)
    private Double quantity;
    
    @Column(name = "unit_price", nullable = false)
    private Double unitPrice;
    
    @Column(name = "total_price", nullable = false)
    private Double totalPrice;
    
    @Column(name = "return_reason")
    private String returnReason;
    
    @Column(name = "condition_status")
    private String conditionStatus; // GOOD, DAMAGED, DEFECTIVE

    public ReturnItem() {
        this.conditionStatus = "GOOD";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public SaleReturn getSaleReturn() { return saleReturn; }
    public void setSaleReturn(SaleReturn saleReturn) { this.saleReturn = saleReturn; }
    
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    
    public SaleItem getOriginalSaleItem() { return originalSaleItem; }
    public void setOriginalSaleItem(SaleItem originalSaleItem) { this.originalSaleItem = originalSaleItem; }
    
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    
    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
    
    public Double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(Double totalPrice) { this.totalPrice = totalPrice; }
    
    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String returnReason) { this.returnReason = returnReason; }
    
    public String getConditionStatus() { return conditionStatus; }
    public void setConditionStatus(String conditionStatus) { this.conditionStatus = conditionStatus; }
}
