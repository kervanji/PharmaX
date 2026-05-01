package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_units",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_units_product_unit", columnNames = {"product_id", "unit_name"}),
                @UniqueConstraint(name = "uk_product_units_barcode", columnNames = {"barcode"})
        },
        indexes = {
                @Index(name = "idx_product_units_product", columnList = "product_id"),
                @Index(name = "idx_product_units_barcode", columnList = "barcode")
        })
public class ProductUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "unit_name", nullable = false)
    private String unitName;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "conversion_factor", nullable = false)
    private Double conversionFactor;

    @Column(name = "sale_price")
    private Double salePrice;

    @Column(name = "sale_price_usd")
    private Double salePriceUsd;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ProductUnit() {
        this.conversionFactor = 1.0;
        this.isDefault = false;
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public ProductUnit(Product product, String unitName, Double conversionFactor) {
        this();
        this.product = product;
        this.unitName = unitName;
        this.conversionFactor = conversionFactor;
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

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public Double getConversionFactor() {
        return conversionFactor;
    }

    public void setConversionFactor(Double conversionFactor) {
        this.conversionFactor = conversionFactor;
    }

    public Double getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(Double salePrice) {
        this.salePrice = salePrice;
    }

    public Double getSalePriceUsd() {
        return salePriceUsd;
    }

    public void setSalePriceUsd(Double salePriceUsd) {
        this.salePriceUsd = salePriceUsd;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
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

    public double getEffectiveConversionFactor() {
        return conversionFactor != null && conversionFactor > 0 ? conversionFactor : 1.0;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return unitName != null ? unitName : "";
    }
}
