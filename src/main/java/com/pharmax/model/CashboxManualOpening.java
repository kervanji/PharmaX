package com.pharmax.model;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cashbox_manual_opening")
public class CashboxManualOpening {
    @Id
    @Column(name = "opening_date", nullable = false)
    private LocalDate openingDate;

    @Column(name = "opening_cash", nullable = false)
    private Double openingCash;

    @Column(name = "set_by")
    private String setBy;

    @Column(name = "set_at")
    private LocalDateTime setAt;

    public CashboxManualOpening() {
        this.openingDate = LocalDate.now();
        this.openingCash = 0.0;
        this.setAt = LocalDateTime.now();
    }

    public LocalDate getOpeningDate() { return openingDate; }
    public void setOpeningDate(LocalDate openingDate) { this.openingDate = openingDate; }
    public Double getOpeningCash() { return openingCash != null ? openingCash : 0.0; }
    public void setOpeningCash(Double openingCash) { this.openingCash = openingCash; }
    public String getSetBy() { return setBy; }
    public void setSetBy(String setBy) { this.setBy = setBy; }
    public LocalDateTime getSetAt() { return setAt; }
    public void setSetAt(LocalDateTime setAt) { this.setAt = setAt; }
}
