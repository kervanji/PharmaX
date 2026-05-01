package com.pharmax.service;

import com.pharmax.database.Repository.InventoryMovementRepository;
import com.pharmax.model.InventoryMovement;
import com.pharmax.model.Product;
import com.pharmax.model.ProductBatch;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class InventoryMovementService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryMovementService.class);
    private final InventoryMovementRepository inventoryMovementRepository;

    public InventoryMovementService() {
        this.inventoryMovementRepository = new InventoryMovementRepository();
    }

    public InventoryMovement save(InventoryMovement movement) {
        if (movement == null) {
            throw new IllegalArgumentException("حركة المخزون غير صالحة");
        }
        if (movement.getProduct() == null || movement.getProduct().getId() == null) {
            throw new IllegalArgumentException("المنتج مطلوب لحركة المخزون");
        }
        if (movement.getMovementType() == null || movement.getMovementType().trim().isEmpty()) {
            throw new IllegalArgumentException("نوع حركة المخزون مطلوب");
        }
        return inventoryMovementRepository.save(movement);
    }

    public InventoryMovement recordMovement(Product product,
                                            ProductBatch batch,
                                            String movementType,
                                            String referenceType,
                                            Long referenceId,
                                            Long referenceItemId,
                                            Double quantityDelta,
                                            Double unitCostSnapshot,
                                            String note,
                                            String actor) {
        if (quantityDelta == null || Math.abs(quantityDelta) < 1e-9) {
            throw new IllegalArgumentException("كمية حركة المخزون يجب ألا تكون صفراً");
        }

        InventoryMovement movement = new InventoryMovement();
        movement.setProduct(product);
        movement.setBatch(batch);
        movement.setMovementType(movementType);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setReferenceItemId(referenceItemId);
        movement.setQuantityDelta(quantityDelta);
        movement.setUnitCostSnapshot(unitCostSnapshot);
        movement.setNote(note);
        movement.setActor(actor);

        InventoryMovement saved = save(movement);
        logger.info("Recorded inventory movement {} for product {}", movementType,
                product != null ? product.getName() : "-");
        return saved;
    }

    public List<InventoryMovement> getByProductId(Long productId) {
        return inventoryMovementRepository.findByProductId(productId);
    }

    public List<InventoryMovement> getByBatchId(Long batchId) {
        return inventoryMovementRepository.findByBatchId(batchId);
    }

    public boolean existsByReference(String movementType, String referenceType, Long referenceId, Long referenceItemId) {
        return inventoryMovementRepository.existsByReference(movementType, referenceType, referenceId, referenceItemId, null);
    }

    public boolean existsByReference(Session session,
                                     String movementType,
                                     String referenceType,
                                     Long referenceId,
                                     Long referenceItemId,
                                     Long batchId) {
        Query<Long> query = session.createQuery(
                "SELECT COUNT(m.id) FROM InventoryMovement m " +
                        "WHERE m.movementType = :movementType " +
                        "AND m.referenceType = :referenceType " +
                        "AND m.referenceId = :referenceId " +
                        "AND m.referenceItemId = :referenceItemId " +
                        "AND ((:batchId IS NULL AND m.batch IS NULL) OR m.batch.id = :batchId)",
                Long.class);
        query.setParameter("movementType", movementType);
        query.setParameter("referenceType", referenceType);
        query.setParameter("referenceId", referenceId);
        query.setParameter("referenceItemId", referenceItemId);
        query.setParameter("batchId", batchId);
        Long count = query.uniqueResult();
        return count != null && count > 0;
    }

    public InventoryMovement recordMovement(Session session,
                                            Product product,
                                            ProductBatch batch,
                                            String movementType,
                                            String referenceType,
                                            Long referenceId,
                                            Long referenceItemId,
                                            Double quantityDelta,
                                            Double quantityBefore,
                                            Double quantityAfter,
                                            Double unitCostSnapshot,
                                            String note,
                                            String actor) {
        if (quantityDelta == null || Math.abs(quantityDelta) < 1e-9) {
            throw new IllegalArgumentException("كمية حركة المخزون يجب ألا تكون صفراً");
        }
        Long batchId = batch != null ? batch.getId() : null;
        if (existsByReference(session, movementType, referenceType, referenceId, referenceItemId, batchId)) {
            logger.info("Skipping duplicate inventory movement {} for reference {} / {}", movementType, referenceId,
                    referenceItemId);
            return null;
        }

        InventoryMovement movement = new InventoryMovement();
        movement.setProduct(product);
        movement.setBatch(batch);
        movement.setMovementType(movementType);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setReferenceItemId(referenceItemId);
        movement.setQuantityDelta(quantityDelta);
        movement.setQuantityBefore(quantityBefore);
        movement.setQuantityAfter(quantityAfter);
        movement.setUnitCostSnapshot(unitCostSnapshot);
        movement.setNote(note);
        movement.setActor(actor);
        session.save(movement);
        return movement;
    }
}
