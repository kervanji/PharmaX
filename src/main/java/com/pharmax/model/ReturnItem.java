package com.pharmax.model;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id")
    private ProductBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_item_batch_id")
    private SaleItemBatch saleItemBatch;
    
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

    @Column(name = "batch_number_snapshot")
    private String batchNumberSnapshot;

    @Column(name = "expiration_date_snapshot")
    private String expirationDateSnapshot;

    @OneToMany(mappedBy = "returnItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<ReturnItemBatch> batchRestorations = new ArrayList<>();

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

    public ProductBatch getBatch() { return batch; }
    public void setBatch(ProductBatch batch) { this.batch = batch; }

    public SaleItemBatch getSaleItemBatch() { return saleItemBatch; }
    public void setSaleItemBatch(SaleItemBatch saleItemBatch) { this.saleItemBatch = saleItemBatch; }
    
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

    public String getBatchNumberSnapshot() { return batchNumberSnapshot; }
    public void setBatchNumberSnapshot(String batchNumberSnapshot) { this.batchNumberSnapshot = batchNumberSnapshot; }

    public String getExpirationDateSnapshot() { return expirationDateSnapshot; }
    public void setExpirationDateSnapshot(String expirationDateSnapshot) { this.expirationDateSnapshot = expirationDateSnapshot; }

    public List<ReturnItemBatch> getBatchRestorations() { return batchRestorations; }
    public void setBatchRestorations(List<ReturnItemBatch> batchRestorations) { this.batchRestorations = batchRestorations; }

    public void addBatchRestoration(ReturnItemBatch restoration) {
        batchRestorations.add(restoration);
        restoration.setReturnItem(this);
    }
}
