package com.pharmax.service;

import com.pharmax.database.DatabaseManager;
import com.pharmax.model.AuditLog;
import com.pharmax.model.User;
import com.pharmax.util.SessionManager;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditLogService {
    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    public void record(String actionType, String entityType, Long entityId, String details) {
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            record(session, actionType, entityType, entityId, details);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            logger.warn("Failed to record audit log action {}", actionType, e);
        }
    }

    public void record(Session session, String actionType, String entityType, Long entityId, String details) {
        if (session == null) {
            throw new IllegalArgumentException("جلسة قاعدة البيانات مطلوبة لتسجيل التدقيق");
        }
        AuditLog log = new AuditLog();
        log.setActionType(actionType);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);

        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            log.setUserId(currentUser.getId());
            log.setUsernameSnapshot(currentUser.getUsername());
            log.setRoleSnapshot(currentUser.getRole() != null ? currentUser.getRole().name() : null);
        } else {
            log.setUsernameSnapshot("SYSTEM");
            log.setRoleSnapshot("SYSTEM");
        }

        session.save(log);
    }

    public void recordPermissionDenied(String actionType, String entityType, Long entityId, String details) {
        record("PERMISSION_DENIED", entityType, entityId, actionType + " :: " + details);
    }
}
