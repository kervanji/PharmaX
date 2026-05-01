package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sale_item_batches")
public class SaleItemBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_item_id", nullable = false)
    private SaleItem saleItem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductBatch batch;

    @Column(name = "quantity_sold", nullable = false)
    private Double quantitySold;

    @Column(name = "batch_number_snapshot")
    private String batchNumberSnapshot;

    @Column(name = "expiration_date_snapshot")
    private String expirationDateSnapshot;

    @Column(name = "unit_cost_snapshot")
    private Double unitCostSnapshot;

    @Column(name = "quantity_before")
    private Double quantityBefore;

    @Column(name = "quantity_after")
    private Double quantityAfter;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public SaleItemBatch() {
        this.quantitySold = 0.0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SaleItem getSaleItem() {
        return saleItem;
    }

    public void setSaleItem(SaleItem saleItem) {
        this.saleItem = saleItem;
    }

    public ProductBatch getBatch() {
        return batch;
    }

    public void setBatch(ProductBatch batch) {
        this.batch = batch;
    }

    public Double getQuantitySold() {
        return quantitySold != null ? quantitySold : 0.0;
    }

    public void setQuantitySold(Double quantitySold) {
        this.quantitySold = quantitySold;
    }

    public String getBatchNumberSnapshot() {
        return batchNumberSnapshot;
    }

    public void setBatchNumberSnapshot(String batchNumberSnapshot) {
        this.batchNumberSnapshot = batchNumberSnapshot;
    }

    public String getExpirationDateSnapshot() {
        return expirationDateSnapshot;
    }

    public void setExpirationDateSnapshot(String expirationDateSnapshot) {
        this.expirationDateSnapshot = expirationDateSnapshot;
    }

    public Double getUnitCostSnapshot() {
        return unitCostSnapshot;
    }

    public void setUnitCostSnapshot(Double unitCostSnapshot) {
        this.unitCostSnapshot = unitCostSnapshot;
    }

    public Double getQuantityBefore() {
        return quantityBefore;
    }

    public void setQuantityBefore(Double quantityBefore) {
        this.quantityBefore = quantityBefore;
    }

    public Double getQuantityAfter() {
        return quantityAfter;
    }

    public void setQuantityAfter(Double quantityAfter) {
        this.quantityAfter = quantityAfter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
