package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "return_item_batches")
public class ReturnItemBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_item_id", nullable = false)
    private ReturnItem returnItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_item_batch_id")
    private SaleItemBatch saleItemBatch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductBatch batch;

    @Column(name = "quantity_returned", nullable = false)
    private Double quantityReturned;

    @Column(name = "batch_number_snapshot")
    private String batchNumberSnapshot;

    @Column(name = "expiration_date_snapshot")
    private String expirationDateSnapshot;

    @Column(name = "quantity_before")
    private Double quantityBefore;

    @Column(name = "quantity_after")
    private Double quantityAfter;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public ReturnItemBatch() {
        this.quantityReturned = 0.0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ReturnItem getReturnItem() { return returnItem; }
    public void setReturnItem(ReturnItem returnItem) { this.returnItem = returnItem; }

    public SaleItemBatch getSaleItemBatch() { return saleItemBatch; }
    public void setSaleItemBatch(SaleItemBatch saleItemBatch) { this.saleItemBatch = saleItemBatch; }

    public ProductBatch getBatch() { return batch; }
    public void setBatch(ProductBatch batch) { this.batch = batch; }

    public Double getQuantityReturned() { return quantityReturned != null ? quantityReturned : 0.0; }
    public void setQuantityReturned(Double quantityReturned) { this.quantityReturned = quantityReturned; }

    public String getBatchNumberSnapshot() { return batchNumberSnapshot; }
    public void setBatchNumberSnapshot(String batchNumberSnapshot) { this.batchNumberSnapshot = batchNumberSnapshot; }

    public String getExpirationDateSnapshot() { return expirationDateSnapshot; }
    public void setExpirationDateSnapshot(String expirationDateSnapshot) { this.expirationDateSnapshot = expirationDateSnapshot; }

    public Double getQuantityBefore() { return quantityBefore; }
    public void setQuantityBefore(Double quantityBefore) { this.quantityBefore = quantityBefore; }

    public Double getQuantityAfter() { return quantityAfter; }
    public void setQuantityAfter(Double quantityAfter) { this.quantityAfter = quantityAfter; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
