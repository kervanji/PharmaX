package com.pharmax.service;

import com.pharmax.database.DatabaseManager;
import com.pharmax.model.User;
import com.pharmax.model.UserRole;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final AccessControlService accessControlService = new AccessControlService();
    private final AuditLogService auditLogService = new AuditLogService();
    
    public AuthService() {
        ensureDefaultAdminExists();
    }
    
    private void ensureDefaultAdminExists() {
        try {
            List<User> admins = getUsersByRole(UserRole.ADMIN);
            if (admins.isEmpty()) {
                // Create default admin with PIN "1234"
                User admin = new User("admin", "المدير", hashPin("1234"), UserRole.ADMIN);
                saveUser(admin);
                logger.info("Default admin user created with PIN: 1234");
            }
        } catch (Exception e) {
            logger.error("Failed to ensure default admin exists", e);
        }
    }

    public boolean verifyAdminPin(String pin) {
        if (pin == null || pin.isBlank()) {
            return false;
        }

        String hashedPin = hashPin(pin);
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<User> query = session.createQuery(
                "FROM User u WHERE u.role = :role AND u.isActive = true",
                User.class
            );
            query.setParameter("role", UserRole.ADMIN);
            List<User> admins = query.list();
            for (User admin : admins) {
                if (admin != null && hashedPin.equals(admin.getPinHash())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            logger.error("Failed to verify admin PIN", e);
            return false;
        }
    }
    
    public Optional<User> authenticate(String username, String pin) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<User> query = session.createQuery(
                "FROM User u WHERE u.username = :username", User.class);
            query.setParameter("username", username);
            User user = query.uniqueResult();
            
            if (user == null) {
                logger.warn("Authentication failed: User not found - {}", username);
                auditLogService.record("LOGIN_FAILED", "user", null, "اسم المستخدم غير موجود: " + username);
                return Optional.empty();
            }
            
            if (!user.isActive()) {
                logger.warn("Authentication failed: User inactive - {}", username);
                auditLogService.record("LOGIN_FAILED", "user", user.getId(), "المستخدم معطل: " + username);
                return Optional.empty();
            }
            
            String hashedPin = hashPin(pin);
            if (user.getPinHash().equals(hashedPin)) {
                // Success - update last login
                user.setLastLoginAt(LocalDateTime.now());
                persistUser(user, false);
                logger.info("User authenticated successfully: {}", username);
                return Optional.of(user);
            } else {
                logger.warn("Authentication failed: Invalid PIN - {}", username);
                auditLogService.record("LOGIN_FAILED", "user", user.getId(), "رمز الدخول غير صحيح للمستخدم: " + username);
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.error("Authentication error", e);
            return Optional.empty();
        }
    }
    
    public User saveUser(User user) {
        boolean allowBootstrap = accessControlService.getCurrentUser() == null && getAllUsers().isEmpty();
        if (!allowBootstrap) {
            accessControlService.requireManageUsers("USER_CREATE", "user", user != null ? user.getId() : null);
        }
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.persist(user);
            if (!allowBootstrap && user != null) {
                auditLogService.record(session, "USER_CREATED", "user", user.getId(),
                        "تم إنشاء المستخدم: " + user.getUsername() + " بالدور " + user.getRole().name());
            }
            transaction.commit();
            if (user != null) {
                logger.info("User saved: {}", user.getUsername());
            }
            return user;
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            logger.error("Failed to save user", e);
            throw e;
        }
    }
    
    public void updateUser(User user) {
        accessControlService.requireManageUsers("USER_UPDATE", "user", user != null ? user.getId() : null);
        persistUser(user, true);
    }

    private void persistUser(User user, boolean audit) {
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            user.setUpdatedAt(LocalDateTime.now());
            session.merge(user);
            if (audit) {
                auditLogService.record(session, "USER_UPDATED", "user", user.getId(),
                        "تم تحديث المستخدم: " + user.getUsername() + " بالدور " + user.getRole().name());
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            logger.error("Failed to update user", e);
            throw e;
        }
    }
    
    public void deleteUser(Long userId) {
        accessControlService.requireManageUsers("USER_DELETE", "user", userId);
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            User user = session.get(User.class, userId);
            if (user != null) {
                auditLogService.record(session, "USER_DELETED", "user", user.getId(),
                        "تم حذف المستخدم: " + user.getUsername());
                session.remove(user);
            }
            transaction.commit();
            logger.info("User deleted: {}", userId);
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            logger.error("Failed to delete user", e);
            throw e;
        }
    }
    
    public List<User> getAllUsers() {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            return session.createQuery("FROM User ORDER BY createdAt DESC", User.class).list();
        }
    }
    
    public List<User> getActiveUsers() {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            return session.createQuery("FROM User u WHERE u.isActive = true ORDER BY u.displayName", User.class).list();
        }
    }
    
    public List<User> getUsersByRole(UserRole role) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<User> query = session.createQuery(
                "FROM User u WHERE u.role = :role", User.class);
            query.setParameter("role", role);
            return query.list();
        }
    }
    
    public Optional<User> getUserByUsername(String username) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<User> query = session.createQuery(
                "FROM User u WHERE u.username = :username", User.class);
            query.setParameter("username", username);
            return Optional.ofNullable(query.uniqueResult());
        }
    }
    
    public Optional<User> getUserById(Long id) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            return Optional.ofNullable(session.get(User.class, id));
        }
    }
    
    public boolean isUsernameExists(String username) {
        return getUserByUsername(username).isPresent();
    }
    
    public void changePin(User user, String newPin) {
        user.setPinHash(hashPin(newPin));
        accessControlService.requireManageUsers("USER_PIN_CHANGE", "user", user != null ? user.getId() : null);
        persistUser(user, true);
        logger.info("PIN changed for user: {}", user.getUsername());
    }
    
    public void toggleUserActive(Long userId) {
        accessControlService.requireManageUsers("USER_TOGGLE_ACTIVE", "user", userId);
        getUserById(userId).ifPresent(user -> {
            user.setActive(!user.isActive());
            persistUser(user, true);
            auditLogService.record("USER_STATUS_CHANGED", "user", user.getId(),
                    "تم تغيير حالة المستخدم " + user.getUsername() + " إلى " + (user.isActive() ? "نشط" : "معطل"));
            logger.info("User {} active status changed to: {}", user.getUsername(), user.isActive());
        });
    }
    
    public void unlockUser(Long userId) {
        accessControlService.requireManageUsers("USER_UNLOCK", "user", userId);
        getUserById(userId).ifPresent(user -> {
            user.resetFailedAttempts();
            persistUser(user, true);
            auditLogService.record("USER_UNLOCKED", "user", user.getId(),
                    "تم فك قفل المستخدم: " + user.getUsername());
            logger.info("User {} unlocked", user.getUsername());
        });
    }
    
    public String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pin.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
    
    public int getActiveAdminCount() {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<Long> query = session.createQuery(
                "SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.isActive = true", Long.class);
            query.setParameter("role", UserRole.ADMIN);
            return query.uniqueResult().intValue();
        }
    }
}
