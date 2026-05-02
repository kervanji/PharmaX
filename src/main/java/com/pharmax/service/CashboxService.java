package com.pharmax.service;

import com.pharmax.database.DatabaseManager;
import com.pharmax.model.CashboxLedger;
import com.pharmax.model.Customer;
import com.pharmax.model.DailyClosing;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public class CashboxService {
    private static final Logger logger = LoggerFactory.getLogger(CashboxService.class);
    private final AccessControlService accessControlService = new AccessControlService();
    private final AuditLogService auditLogService = new AuditLogService();

    public CashboxLedger recordEntry(Session session,
                                     LocalDateTime transactionDate,
                                     String entryType,
                                     String direction,
                                     Double amount,
                                     String currency,
                                     String sourceType,
                                     Long sourceId,
                                     Long sourceItemId,
                                     Customer customer,
                                     Customer supplier,
                                     Customer account,
                                     String paymentMethod,
                                     String description,
                                     String createdBy) {
        if (session == null) {
            throw new IllegalArgumentException("جلسة قاعدة البيانات مطلوبة");
        }
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("مبلغ حركة الصندوق يجب أن يكون أكبر من صفر");
        }
        long normalizedSourceItemId = sourceItemId != null ? sourceItemId : 0L;
        if (existsBySource(session, entryType, sourceType, sourceId, normalizedSourceItemId)) {
            logger.info("Skipping duplicate cashbox entry {} for source {} / {}", entryType, sourceType, sourceId);
            return null;
        }

        CashboxLedger entry = new CashboxLedger();
        entry.setTransactionDate(transactionDate != null ? transactionDate : LocalDateTime.now());
        entry.setEntryType(entryType);
        entry.setDirection(direction);
        entry.setAmount(amount);
        entry.setCurrency(currency != null ? currency : "دينار");
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setSourceItemId(normalizedSourceItemId);
        entry.setCustomer(customer);
        entry.setSupplier(supplier);
        entry.setAccount(account);
        entry.setPaymentMethod(paymentMethod);
        entry.setDescription(description);
        entry.setCreatedBy(createdBy);
        session.save(entry);
        return entry;
    }

    public boolean existsBySource(Session session, String entryType, String sourceType, Long sourceId, Long sourceItemId) {
        Query<Long> query = session.createQuery(
                "SELECT COUNT(c.id) FROM CashboxLedger c " +
                        "WHERE c.entryType = :entryType " +
                        "AND c.sourceType = :sourceType " +
                        "AND c.sourceId = :sourceId " +
                        "AND c.sourceItemId = :sourceItemId",
                Long.class);
        query.setParameter("entryType", entryType);
        query.setParameter("sourceType", sourceType);
        query.setParameter("sourceId", sourceId);
        query.setParameter("sourceItemId", sourceItemId != null ? sourceItemId : 0L);
        Long count = query.uniqueResult();
        return count != null && count > 0;
    }

    public List<CashboxLedger> getLedgerForDate(LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        LocalDateTime start = effectiveDate.atStartOfDay();
        LocalDateTime end = effectiveDate.atTime(LocalTime.MAX);
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<CashboxLedger> query = session.createQuery(
                    "FROM CashboxLedger c " +
                            "WHERE c.voided = false " +
                            "AND c.transactionDate BETWEEN :start AND :end " +
                            "ORDER BY c.transactionDate ASC, c.id ASC",
                    CashboxLedger.class);
            query.setParameter("start", start);
            query.setParameter("end", end);
            return query.list();
        }
    }

    public CashTotals calculateTotals(LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        double totalIn = 0.0;
        double totalOut = 0.0;
        for (CashboxLedger entry : getLedgerForDate(effectiveDate)) {
            if ("IN".equalsIgnoreCase(entry.getDirection())) {
                totalIn += entry.getAmount();
            } else if ("OUT".equalsIgnoreCase(entry.getDirection())) {
                totalOut += entry.getAmount();
            }
        }
        double openingCash = getOpeningCashForDate(effectiveDate);
        double expectedCash = openingCash + totalIn - totalOut;
        return new CashTotals(openingCash, totalIn, totalOut, expectedCash);
    }

    public double getOpeningCashForDate(LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<DailyClosing> query = session.createQuery(
                    "FROM DailyClosing d WHERE d.closingDate < :date AND d.status = :status ORDER BY d.closingDate DESC",
                    DailyClosing.class);
            query.setParameter("date", effectiveDate);
            query.setParameter("status", "CLOSED");
            query.setMaxResults(1);
            DailyClosing previousClosing = query.uniqueResult();
            return previousClosing != null ? previousClosing.getActualCash() : 0.0;
        }
    }

    public Optional<DailyClosing> getClosingByDate(LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<DailyClosing> query = session.createQuery(
                    "FROM DailyClosing d WHERE d.closingDate = :date",
                    DailyClosing.class);
            query.setParameter("date", effectiveDate);
            return query.uniqueResultOptional();
        }
    }

    public DailyClosing closeDay(LocalDate date, Double actualCash, String notes, String closedBy) {
        accessControlService.requireAdmin("DAILY_CLOSING_CREATE", "daily_closing", null);
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        if (getClosingByDate(effectiveDate).isPresent()) {
            throw new IllegalArgumentException("يوجد إقفال يومي محفوظ لهذا التاريخ بالفعل");
        }
        CashTotals totals = calculateTotals(effectiveDate);
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            DailyClosing closing = new DailyClosing();
            closing.setClosingDate(effectiveDate);
            closing.setOpeningCash(totals.openingCash());
            closing.setTotalCashIn(totals.totalIn());
            closing.setTotalCashOut(totals.totalOut());
            closing.setExpectedCash(totals.expectedCash());
            closing.setActualCash(actualCash != null ? actualCash : totals.expectedCash());
            closing.setDifferenceAmount(closing.getActualCash() - closing.getExpectedCash());
            closing.setClosedBy(closedBy);
            closing.setClosedAt(LocalDateTime.now());
            closing.setNotes(notes);
            closing.setStatus("CLOSED");
            session.save(closing);
            auditLogService.record(session, "DAILY_CLOSING_CREATED", "daily_closing", closing.getId(),
                    "تم إقفال يوم " + effectiveDate + " برصيد متوقع " + closing.getExpectedCash());
            transaction.commit();
            return closing;
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            logger.error("Failed to close cash day", e);
            throw new RuntimeException("فشل في إقفال اليوم: " + e.getMessage(), e);
        }
    }

    public record CashTotals(double openingCash, double totalIn, double totalOut, double expectedCash) {}
}
