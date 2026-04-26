package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", nullable = false, unique = true)
    private String productCode;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "category")
    private String category;

    @Column(name = "unit_price")
    private Double unitPrice;

    @Column(name = "wholesale_price")
    private Double wholesalePrice;

    @Column(name = "special_price")
    private Double specialPrice;

    @Column(name = "cost_price")
    private Double costPrice;

    @Column(name = "cost_price_usd")
    private Double costPriceUsd;

    @Column(name = "unit_price_usd")
    private Double unitPriceUsd;

    @Column(name = "wholesale_price_usd")
    private Double wholesalePriceUsd;

    @Column(name = "special_price_usd")
    private Double specialPriceUsd;

    @Column(name = "quantity_in_stock")
    private Double quantityInStock;

    @Column(name = "minimum_stock")
    private Double minimumStock;

    @Column(name = "maximum_stock")
    private Double maximumStock;

    @Column(name = "unit_of_measure")
    private String unitOfMeasure;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SaleItem> saleItems;

    public Product() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.quantityInStock = 0.0;
        this.minimumStock = 0.0;
        this.isActive = true;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Double getWholesalePrice() {
        return wholesalePrice;
    }

    public void setWholesalePrice(Double wholesalePrice) {
        this.wholesalePrice = wholesalePrice;
    }

    public Double getSpecialPrice() {
        return specialPrice;
    }

    public void setSpecialPrice(Double specialPrice) {
        this.specialPrice = specialPrice;
    }

    public Double getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(Double costPrice) {
        this.costPrice = costPrice;
    }

    public Double getCostPriceUsd() {
        return costPriceUsd;
    }

    public void setCostPriceUsd(Double costPriceUsd) {
        this.costPriceUsd = costPriceUsd;
    }

    public Double getUnitPriceUsd() {
        return unitPriceUsd;
    }

    public void setUnitPriceUsd(Double unitPriceUsd) {
        this.unitPriceUsd = unitPriceUsd;
    }

    public Double getWholesalePriceUsd() {
        return wholesalePriceUsd;
    }

    public void setWholesalePriceUsd(Double wholesalePriceUsd) {
        this.wholesalePriceUsd = wholesalePriceUsd;
    }

    public Double getSpecialPriceUsd() {
        return specialPriceUsd;
    }

    public void setSpecialPriceUsd(Double specialPriceUsd) {
        this.specialPriceUsd = specialPriceUsd;
    }

    public Double getQuantityInStock() {
        return quantityInStock;
    }

    public void setQuantityInStock(Double quantityInStock) {
        this.quantityInStock = quantityInStock;
    }

    public Double getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(Double minimumStock) {
        this.minimumStock = minimumStock;
    }

    public Double getMaximumStock() {
        return maximumStock;
    }

    public void setMaximumStock(Double maximumStock) {
        this.maximumStock = maximumStock;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
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

    public List<SaleItem> getSaleItems() {
        return saleItems;
    }

    public void setSaleItems(List<SaleItem> saleItems) {
        this.saleItems = saleItems;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
