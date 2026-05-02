package com.pharmax.service;

import com.pharmax.model.User;
import com.pharmax.util.SessionManager;

public class AccessControlService {
    private final AuditLogService auditLogService = new AuditLogService();

    public void requireManageUsers(String actionType, String entityType, Long entityId) {
        if (!SessionManager.getInstance().canManageUsers()) {
            deny(actionType, entityType, entityId, "ليس لديك صلاحية إدارة المستخدمين");
        }
    }

    public void requireProductEdit(String actionType, String entityType, Long entityId) {
        if (!SessionManager.getInstance().canEditProducts()) {
            deny(actionType, entityType, entityId, "ليس لديك صلاحية تعديل المخزون أو المنتجات");
        }
    }

    public void requireInvoiceDeletionPrivilege(String actionType, String entityType, Long entityId) {
        if (!SessionManager.getInstance().canDeleteInvoices()) {
            deny(actionType, entityType, entityId, "ليس لديك صلاحية تنفيذ هذا الإجراء الحساس على الفواتير");
        }
    }

    public void requireAdmin(String actionType, String entityType, Long entityId) {
        if (!SessionManager.getInstance().isAdmin()) {
            deny(actionType, entityType, entityId, "هذا الإجراء متاح للمدير فقط في الإصدار الحالي");
        }
    }

    public User getCurrentUser() {
        return SessionManager.getInstance().getCurrentUser();
    }

    private void deny(String actionType, String entityType, Long entityId, String details) {
        auditLogService.recordPermissionDenied(actionType, entityType, entityId, details);
        throw new SecurityException(details);
    }
}
