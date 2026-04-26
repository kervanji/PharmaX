package com.pharmax.model;

import javax.persistence.*;

@Entity
@Table(name = "sale_items")
public class SaleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @Column(nullable = false)
    private Double quantity;
    
    @Column(name = "unit_price", nullable = false)
    private Double unitPrice;
    
    @Column(name = "total_price", nullable = false)
    private Double totalPrice;
    
    @Column(name = "discount_percentage")
    private Double discountPercentage;
    
    @Column(name = "discount_amount")
    private Double discountAmount;
    
    @Column(name = "price_type")
    private String priceType;

    @Column(name = "sold_unit")
    private String soldUnit;

    @Column(name = "conversion_factor")
    private Double conversionFactor;

    @Column(name = "base_quantity")
    private Double baseQuantity;

    public SaleItem() {
        this.discountPercentage = 0.0;
        this.discountAmount = 0.0;
        this.priceType = "مفرد";
        this.conversionFactor = 1.0;
        this.baseQuantity = 0.0;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Sale getSale() { return sale; }
    public void setSale(Sale sale) { this.sale = sale; }
    
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    
    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
    
    public Double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(Double totalPrice) { this.totalPrice = totalPrice; }
    
    public Double getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(Double discountPercentage) { this.discountPercentage = discountPercentage; }
    
    public Double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(Double discountAmount) { this.discountAmount = discountAmount; }
    
    public String getPriceType() { return priceType; }
    public void setPriceType(String priceType) { this.priceType = priceType; }

    public String getSoldUnit() { return soldUnit; }
    public void setSoldUnit(String soldUnit) { this.soldUnit = soldUnit; }

    public Double getConversionFactor() { return conversionFactor; }
    public void setConversionFactor(Double conversionFactor) { this.conversionFactor = conversionFactor; }

    public Double getBaseQuantity() { return baseQuantity; }
    public void setBaseQuantity(Double baseQuantity) { this.baseQuantity = baseQuantity; }

    public double getEffectiveConversionFactor() {
        return conversionFactor != null && conversionFactor > 0 ? conversionFactor : 1.0;
    }

    public double getEffectiveBaseQuantity() {
        if (baseQuantity != null && baseQuantity > 0) {
            return baseQuantity;
        }
        double qty = quantity != null ? quantity : 0.0;
        return qty * getEffectiveConversionFactor();
    }
}
