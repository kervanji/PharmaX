package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "quick_sale_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_quick_sale_group_product", columnNames = {"group_id", "product_id"}))
public class QuickSaleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private QuickSaleGroup group;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_unit_id")
    private ProductUnit productUnit;

    @Type(type = "org.hibernate.type.BinaryType")
    @Column(name = "image_data", columnDefinition = "BLOB")
    private byte[] imageData;

    @Column(name = "image_mime_type")
    private String imageMimeType;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "accent_color")
    private String accentColor;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public QuickSaleGroup getGroup() { return group; }
    public void setGroup(QuickSaleGroup group) { this.group = group; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public ProductUnit getProductUnit() { return productUnit; }
    public void setProductUnit(ProductUnit productUnit) { this.productUnit = productUnit; }
    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }
    public String getImageMimeType() { return imageMimeType; }
    public void setImageMimeType(String imageMimeType) { this.imageMimeType = imageMimeType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }
}
