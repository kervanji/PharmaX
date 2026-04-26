package com.pharmax.model.dto;

import java.time.LocalDateTime;

public class StatementItem {
    private LocalDateTime date;
    private String type; // Sale, Receipt, Payment, Return
    private String referenceNumber;
    private String description;
    private Double debit; // المدين (لنا) - e.g. Sale
    private Double credit; // الدائن (علينا) - e.g. Receipt
    private Double balance; // الرصيد
    private String currency;
    private Object sourceObject; // For linking back to original record
    private boolean detailRow; // Sub-row for detailed view

    // ── Product detail fields (only set when detailRow = true) ──
    private String productName;
    private Double itemQty;
    private Double itemUnitPrice;
    private Double itemTotal;

    public StatementItem() {
    }

    public StatementItem(LocalDateTime date, String type, String referenceNumber, String description,
            Double debit, Double credit, String currency, Object sourceObject) {
        this.date = date;
        this.type = type;
        this.referenceNumber = referenceNumber;
        this.description = description;
        this.debit = debit;
        this.credit = credit;
        this.currency = currency;
        this.sourceObject = sourceObject;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getDebit() {
        return debit;
    }

    public void setDebit(Double debit) {
        this.debit = debit;
    }

    public Double getCredit() {
        return credit;
    }

    public void setCredit(Double credit) {
        this.credit = credit;
    }

    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Object getSourceObject() {
        return sourceObject;
    }

    public void setSourceObject(Object sourceObject) {
        this.sourceObject = sourceObject;
    }

    public boolean isDetailRow() {
        return detailRow;
    }

    public void setDetailRow(boolean detailRow) {
        this.detailRow = detailRow;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Double getItemQty() {
        return itemQty;
    }

    public void setItemQty(Double itemQty) {
        this.itemQty = itemQty;
    }

    public Double getItemUnitPrice() {
        return itemUnitPrice;
    }

    public void setItemUnitPrice(Double itemUnitPrice) {
        this.itemUnitPrice = itemUnitPrice;
    }

    public Double getItemTotal() {
        return itemTotal;
    }

    public void setItemTotal(Double itemTotal) {
        this.itemTotal = itemTotal;
    }
}
