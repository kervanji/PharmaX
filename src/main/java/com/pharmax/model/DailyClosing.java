package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_closings")
public class DailyClosing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "closing_date", nullable = false, unique = true)
    private LocalDate closingDate;

    @Column(name = "opening_cash")
    private Double openingCash;

    @Column(name = "total_cash_in")
    private Double totalCashIn;

    @Column(name = "total_cash_out")
    private Double totalCashOut;

    @Column(name = "expected_cash")
    private Double expectedCash;

    @Column(name = "actual_cash")
    private Double actualCash;

    @Column(name = "difference_amount")
    private Double differenceAmount;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "status")
    private String status;

    public DailyClosing() {
        this.closingDate = LocalDate.now();
        this.openingCash = 0.0;
        this.totalCashIn = 0.0;
        this.totalCashOut = 0.0;
        this.expectedCash = 0.0;
        this.actualCash = 0.0;
        this.differenceAmount = 0.0;
        this.closedAt = LocalDateTime.now();
        this.status = "CLOSED";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getClosingDate() { return closingDate; }
    public void setClosingDate(LocalDate closingDate) { this.closingDate = closingDate; }
    public Double getOpeningCash() { return openingCash != null ? openingCash : 0.0; }
    public void setOpeningCash(Double openingCash) { this.openingCash = openingCash; }
    public Double getTotalCashIn() { return totalCashIn != null ? totalCashIn : 0.0; }
    public void setTotalCashIn(Double totalCashIn) { this.totalCashIn = totalCashIn; }
    public Double getTotalCashOut() { return totalCashOut != null ? totalCashOut : 0.0; }
    public void setTotalCashOut(Double totalCashOut) { this.totalCashOut = totalCashOut; }
    public Double getExpectedCash() { return expectedCash != null ? expectedCash : 0.0; }
    public void setExpectedCash(Double expectedCash) { this.expectedCash = expectedCash; }
    public Double getActualCash() { return actualCash != null ? actualCash : 0.0; }
    public void setActualCash(Double actualCash) { this.actualCash = actualCash; }
    public Double getDifferenceAmount() { return differenceAmount != null ? differenceAmount : 0.0; }
    public void setDifferenceAmount(Double differenceAmount) { this.differenceAmount = differenceAmount; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
