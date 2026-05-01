package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id")
    private ProductBatch batch;

    @Column(name = "movement_type", nullable = false)
    private String movementType;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reference_item_id")
    private Long referenceItemId;

    @Column(name = "quantity_delta", nullable = false)
    private Double quantityDelta;

    @Column(name = "quantity_before")
    private Double quantityBefore;

    @Column(name = "quantity_after")
    private Double quantityAfter;

    @Column(name = "unit_cost_snapshot")
    private Double unitCostSnapshot;

    @Column(name = "note")
    private String note;

    @Column(name = "actor")
    private String actor;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public InventoryMovement() {
        this.quantityDelta = 0.0;
        this.createdAt = LocalDateTime.now();
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

    public ProductBatch getBatch() {
        return batch;
    }

    public void setBatch(ProductBatch batch) {
        this.batch = batch;
    }

    public String getMovementType() {
        return movementType;
    }

    public void setMovementType(String movementType) {
        this.movementType = movementType;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public Long getReferenceItemId() {
        return referenceItemId;
    }

    public void setReferenceItemId(Long referenceItemId) {
        this.referenceItemId = referenceItemId;
    }

    public Double getQuantityDelta() {
        return quantityDelta != null ? quantityDelta : 0.0;
    }

    public void setQuantityDelta(Double quantityDelta) {
        this.quantityDelta = quantityDelta;
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

    public Double getUnitCostSnapshot() {
        return unitCostSnapshot;
    }

    public void setUnitCostSnapshot(Double unitCostSnapshot) {
        this.unitCostSnapshot = unitCostSnapshot;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
