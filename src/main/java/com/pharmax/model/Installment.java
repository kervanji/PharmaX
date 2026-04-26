package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * قسط دفع
 */
@Entity
@Table(name = "installments")
public class Installment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_voucher_id", nullable = false)
    private Voucher parentVoucher;
    
    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;
    
    @Column(name = "amount", nullable = false)
    private Double amount;
    
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    
    @Column(name = "is_paid")
    private Boolean isPaid;
    
    @Column(name = "paid_amount")
    private Double paidAmount;
    
    @Column(name = "paid_date")
    private LocalDate paidDate;
    
    @Column(name = "payment_voucher_id")
    private Long paymentVoucherId; // السند المستخدم للدفع
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Installment() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isPaid = false;
        this.paidAmount = 0.0;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Voucher getParentVoucher() { return parentVoucher; }
    public void setParentVoucher(Voucher parentVoucher) { this.parentVoucher = parentVoucher; }
    
    public Integer getInstallmentNumber() { return installmentNumber; }
    public void setInstallmentNumber(Integer installmentNumber) { this.installmentNumber = installmentNumber; }
    
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    
    public Boolean getIsPaid() { return isPaid != null ? isPaid : false; }
    public void setIsPaid(Boolean isPaid) { this.isPaid = isPaid; }
    
    public Double getPaidAmount() { return paidAmount != null ? paidAmount : 0.0; }
    public void setPaidAmount(Double paidAmount) { this.paidAmount = paidAmount; }
    
    public LocalDate getPaidDate() { return paidDate; }
    public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }
    
    public Long getPaymentVoucherId() { return paymentVoucherId; }
    public void setPaymentVoucherId(Long paymentVoucherId) { this.paymentVoucherId = paymentVoucherId; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Double getRemainingAmount() {
        return amount - paidAmount;
    }
    
    public boolean isOverdue() {
        return !isPaid && dueDate.isBefore(LocalDate.now());
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
