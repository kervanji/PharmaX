package com.pharmax.service;

import com.pharmax.database.DatabaseManager;
import com.pharmax.model.Product;
import com.pharmax.model.ProductUnit;
import com.pharmax.model.QuickSaleGroup;
import com.pharmax.model.QuickSaleItem;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;

public class QuickSaleService {
    public List<QuickSaleGroup> getGroups(boolean activeOnly) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            String hql = "FROM QuickSaleGroup g " + (activeOnly ? "WHERE g.isActive = true " : "")
                    + "ORDER BY g.sortOrder, g.id";
            return session.createQuery(hql, QuickSaleGroup.class).list();
        }
    }

    public List<QuickSaleItem> getItems(Long groupId) {
        if (groupId == null) return List.of();
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            return session.createQuery(
                            "SELECT i FROM QuickSaleItem i JOIN FETCH i.product p LEFT JOIN FETCH i.productUnit "
                                    + "WHERE i.group.id = :groupId ORDER BY i.sortOrder, p.name, i.id",
                            QuickSaleItem.class)
                    .setParameter("groupId", groupId)
                    .list();
        }
    }

    public QuickSaleGroup saveGroup(QuickSaleGroup group) {
        return inTransaction(session -> {
            session.saveOrUpdate(group);
            return group;
        });
    }

    public void deleteGroup(Long groupId) {
        if (groupId == null) return;
        inTransaction(session -> {
            session.createQuery("DELETE FROM QuickSaleItem i WHERE i.group.id = :groupId")
                    .setParameter("groupId", groupId).executeUpdate();
            QuickSaleGroup group = session.get(QuickSaleGroup.class, groupId);
            if (group != null) session.delete(group);
            return null;
        });
    }

    public QuickSaleItem saveItem(QuickSaleItem item) {
        return inTransaction(session -> {
            session.saveOrUpdate(item);
            return item;
        });
    }

    public void deleteItem(Long itemId) {
        if (itemId == null) return;
        inTransaction(session -> {
            QuickSaleItem item = session.get(QuickSaleItem.class, itemId);
            if (item != null) session.delete(item);
            return null;
        });
    }

    public boolean containsProduct(Long groupId, Long productId) {
        if (groupId == null || productId == null) return false;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                            "SELECT COUNT(i) FROM QuickSaleItem i WHERE i.group.id = :groupId AND i.product.id = :productId",
                            Long.class)
                    .setParameter("groupId", groupId)
                    .setParameter("productId", productId)
                    .uniqueResult();
            return count != null && count > 0;
        }
    }

    public void syncDefaultMembership(Long productId, boolean enabled) {
        if (productId == null) return;
        inTransaction(session -> {
            QuickSaleGroup group = session.createQuery(
                            "FROM QuickSaleGroup g ORDER BY g.sortOrder, g.id", QuickSaleGroup.class)
                    .setMaxResults(1).uniqueResult();
            if (group == null) return null;
            QuickSaleItem existing = session.createQuery(
                            "FROM QuickSaleItem i WHERE i.group.id = :groupId AND i.product.id = :productId",
                            QuickSaleItem.class)
                    .setParameter("groupId", group.getId())
                    .setParameter("productId", productId)
                    .uniqueResult();
            if (enabled && existing == null) {
                QuickSaleItem item = new QuickSaleItem();
                item.setGroup(group);
                item.setProduct(session.get(Product.class, productId));
                Long count = session.createQuery(
                                "SELECT COUNT(i) FROM QuickSaleItem i WHERE i.group.id = :groupId", Long.class)
                        .setParameter("groupId", group.getId()).uniqueResult();
                item.setSortOrder(count == null ? 0 : count.intValue());
                session.save(item);
            } else if (!enabled && existing != null) {
                session.delete(existing);
            }
            return null;
        });
    }

    public ProductUnit resolveUnit(QuickSaleItem item) {
        if (item != null && item.getProductUnit() != null && Boolean.TRUE.equals(item.getProductUnit().getIsActive())) {
            return item.getProductUnit();
        }
        return null;
    }

    private <T> T inTransaction(SessionWork<T> work) {
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            T result = work.run(session);
            transaction.commit();
            return result;
        } catch (RuntimeException e) {
            if (transaction != null) transaction.rollback();
            throw e;
        }
    }

    @FunctionalInterface
    private interface SessionWork<T> { T run(Session session); }
}
