package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * سند مالي (قبض أو دفع)
 */
@Entity
@Table(name = "vouchers")
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "voucher_number", nullable = false, unique = true)
    private String voucherNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false)
    private VoucherType voucherType;
    
    @Column(name = "voucher_date", nullable = false)
    private LocalDateTime voucherDate;
    
    @Column(name = "currency", nullable = false)
    private String currency; // دينار أو دولار
    
    @Column(name = "exchange_rate")
    private Double exchangeRate; // سعر الصرف
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    
    @Column(name = "cash_account")
    private String cashAccount; // حساب الصندوق

    @Column(name = "amount", nullable = false)
    private Double amount;
    
    @Column(name = "discount_percentage")
    private Double discountPercentage;
    
    @Column(name = "discount_amount")
    private Double discountAmount;
    
    @Column(name = "net_amount", nullable = false)
    private Double netAmount; // المبلغ بعد الخصم
    
    @Column(name = "amount_in_words")
    private String amountInWords; // المبلغ كتابةً
    
    @Column(name = "description")
    private String description; // البيان
    
    @Column(name = "payment_method")
    private String paymentMethod; // نقدي، شيك، تحويل
    
    @Column(name = "reference_number")
    private String referenceNumber; // رقم الشيك أو التحويل
    
    @Column(name = "is_installment")
    private Boolean isInstallment; // هل هو قسط؟
    
    @Column(name = "total_installments")
    private Integer totalInstallments; // عدد الأقساط الكلي
    
    @Column(name = "installment_number")
    private Integer installmentNumber; // رقم القسط الحالي
    
    @Column(name = "parent_voucher_id")
    private Long parentVoucherId; // السند الأصلي للأقساط
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "is_cancelled")
    private Boolean isCancelled;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "cancelled_by")
    private String cancelledBy;
    
    @Column(name = "cancel_reason")
    private String cancelReason;
    
    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<VoucherItem> items = new ArrayList<>();
    
    @OneToMany(mappedBy = "parentVoucher", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Installment> installments = new ArrayList<>();

    public Voucher() {
        this.voucherDate = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.currency = "دينار";
        this.exchangeRate = 1.0;
        this.discountPercentage = 0.0;
        this.discountAmount = 0.0;
        this.isInstallment = false;
        this.isCancelled = false;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getVoucherNumber() { return voucherNumber; }
    public void setVoucherNumber(String voucherNumber) { this.voucherNumber = voucherNumber; }
    
    public VoucherType getVoucherType() { return voucherType; }
    public void setVoucherType(VoucherType voucherType) { this.voucherType = voucherType; }
    
    public LocalDateTime getVoucherDate() { return voucherDate; }
    public void setVoucherDate(LocalDateTime voucherDate) { this.voucherDate = voucherDate; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public Double getExchangeRate() { return exchangeRate != null ? exchangeRate : 1.0; }
    public void setExchangeRate(Double exchangeRate) { this.exchangeRate = exchangeRate; }
    
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    
    public String getCashAccount() { return cashAccount; }
    public void setCashAccount(String cashAccount) { this.cashAccount = cashAccount; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    
    public Double getDiscountPercentage() { return discountPercentage != null ? discountPercentage : 0.0; }
    public void setDiscountPercentage(Double discountPercentage) { this.discountPercentage = discountPercentage; }
    
    public Double getDiscountAmount() { return discountAmount != null ? discountAmount : 0.0; }
    public void setDiscountAmount(Double discountAmount) { this.discountAmount = discountAmount; }
    
    public Double getNetAmount() { return netAmount; }
    public void setNetAmount(Double netAmount) { this.netAmount = netAmount; }
    
    public String getAmountInWords() { return amountInWords; }
    public void setAmountInWords(String amountInWords) { this.amountInWords = amountInWords; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    
    public Boolean getIsInstallment() { return isInstallment != null ? isInstallment : false; }
    public void setIsInstallment(Boolean isInstallment) { this.isInstallment = isInstallment; }
    
    public Integer getTotalInstallments() { return totalInstallments; }
    public void setTotalInstallments(Integer totalInstallments) { this.totalInstallments = totalInstallments; }
    
    public Integer getInstallmentNumber() { return installmentNumber; }
    public void setInstallmentNumber(Integer installmentNumber) { this.installmentNumber = installmentNumber; }
    
    public Long getParentVoucherId() { return parentVoucherId; }
    public void setParentVoucherId(Long parentVoucherId) { this.parentVoucherId = parentVoucherId; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Boolean getIsCancelled() { return isCancelled != null ? isCancelled : false; }
    public void setIsCancelled(Boolean isCancelled) { this.isCancelled = isCancelled; }
    
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    
    public List<VoucherItem> getItems() { return items; }
    public void setItems(List<VoucherItem> items) { this.items = items; }
    
    public List<Installment> getInstallments() { return installments; }
    public void setInstallments(List<Installment> installments) { this.installments = installments; }
    
    public void addItem(VoucherItem item) {
        items.add(item);
        item.setVoucher(this);
    }
    
    public void removeItem(VoucherItem item) {
        items.remove(item);
        item.setVoucher(null);
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
