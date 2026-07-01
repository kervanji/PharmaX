package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cashbox_ledger")
public class CashboxLedger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @Column(name = "entry_type", nullable = false)
    private String entryType;

    @Column(name = "direction", nullable = false)
    private String direction;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "currency")
    private String currency;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_item_id")
    private Long sourceItemId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    private Customer supplier;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id")
    private Customer account;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "description")
    private String description;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "related_created_by")
    private String relatedCreatedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "voided")
    private Boolean voided;

    @Column(name = "void_reason")
    private String voidReason;

    public CashboxLedger() {
        this.transactionDate = LocalDateTime.now();
        this.amount = 0.0;
        this.currency = "دينار";
        this.createdAt = LocalDateTime.now();
        this.voided = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Double getAmount() { return amount != null ? amount : 0.0; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getSourceItemId() { return sourceItemId; }
    public void setSourceItemId(Long sourceItemId) { this.sourceItemId = sourceItemId; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public Customer getSupplier() { return supplier; }
    public void setSupplier(Customer supplier) { this.supplier = supplier; }
    public Customer getAccount() { return account; }
    public void setAccount(Customer account) { this.account = account; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getRelatedCreatedBy() { return relatedCreatedBy; }
    public void setRelatedCreatedBy(String relatedCreatedBy) { this.relatedCreatedBy = relatedCreatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Boolean getVoided() { return voided != null ? voided : false; }
    public void setVoided(Boolean voided) { this.voided = voided; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
}
