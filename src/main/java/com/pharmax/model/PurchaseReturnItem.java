package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_return_items")
public class PurchaseReturnItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "purchase_return_id", nullable = false)
    private PurchaseReturn purchaseReturn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "source_voucher_item_id")
    private VoucherItem sourceVoucherItem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id")
    private ProductBatch batch;

    @Column(name = "batch_number_snapshot")
    private String batchNumberSnapshot;

    @Column(name = "expiration_date_snapshot")
    private String expirationDateSnapshot;

    @Column(name = "quantity")
    private Double quantity;

    @Column(name = "unit_cost")
    private Double unitCost;

    @Column(name = "line_total")
    private Double lineTotal;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public PurchaseReturnItem() {
        this.quantity = 0.0;
        this.unitCost = 0.0;
        this.lineTotal = 0.0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PurchaseReturn getPurchaseReturn() {
        return purchaseReturn;
    }

    public void setPurchaseReturn(PurchaseReturn purchaseReturn) {
        this.purchaseReturn = purchaseReturn;
    }

    public VoucherItem getSourceVoucherItem() {
        return sourceVoucherItem;
    }

    public void setSourceVoucherItem(VoucherItem sourceVoucherItem) {
        this.sourceVoucherItem = sourceVoucherItem;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public ProductBatch getBatch() {
        return batch;
    }

    public void setBatch(ProductBatch batch) {
        this.batch = batch;
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

    public Double getQuantity() {
        return quantity != null ? quantity : 0.0;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public Double getUnitCost() {
        return unitCost != null ? unitCost : 0.0;
    }

    public void setUnitCost(Double unitCost) {
        this.unitCost = unitCost;
    }

    public Double getLineTotal() {
        return lineTotal != null ? lineTotal : 0.0;
    }

    public void setLineTotal(Double lineTotal) {
        this.lineTotal = lineTotal;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
