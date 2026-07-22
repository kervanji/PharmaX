package com.pharmax;

import com.pharmax.database.DatabaseManager;
import com.pharmax.database.Repository.CustomerRepository;
import com.pharmax.database.Repository.ProductRepository;
import com.pharmax.model.Customer;
import com.pharmax.model.InventoryMovement;
import com.pharmax.model.Product;
import com.pharmax.model.Sale;
import com.pharmax.service.SalesService;
import org.hibernate.Session;

import java.util.List;

public final class UnlimitedStockSaleSmokeHarness {
    private UnlimitedStockSaleSmokeHarness() {}

    public static void main(String[] args) {
        DatabaseManager.initialize();
        ProductRepository products = new ProductRepository();
        CustomerRepository customers = new CustomerRepository();

        Product product = products.findByProductCode("UNLIMITED-SMOKE").orElseGet(() -> {
            Product created = new Product();
            created.setProductCode("UNLIMITED-SMOKE");
            created.setName("Unlimited smoke product");
            created.setUnitPrice(2500.0);
            created.setCostPrice(1000.0);
            created.setQuantityInStock(0.0);
            created.setMinimumStock(0.0);
            created.setBaseUnit("قطعة");
            created.setUnitOfMeasure("قطعة");
            created.setIsActive(true);
            created.setIsUnlimitedStock(true);
            return products.save(created);
        });

        Customer customer = customers.findByCustomerCode("UNLIMITED-SMOKE-CUSTOMER").orElseGet(() -> {
            Customer created = new Customer();
            created.setCustomerCode("UNLIMITED-SMOKE-CUSTOMER");
            created.setName("Unlimited smoke customer");
            return customers.save(created);
        });

        SalesService.SaleItemRequest item = new SalesService.SaleItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(7.0);
        item.setUnitPrice(2500.0);
        item.setDiscountPercentage(0.0);
        item.setPriceType("مفرد");
        item.setSoldUnit("قطعة");
        item.setConversionFactor(1.0);
        item.setBaseQuantity(7.0);

        SalesService.SaleRequest request = new SalesService.SaleRequest();
        request.setCustomerId(customer.getId());
        request.setPaymentMethod("CREDIT");
        request.setCurrency("دينار");
        request.setCreatedBy("smoke");
        request.setPaidAmount(0.0);
        request.setAdditionalDiscount(0.0);
        request.setItems(List.of(item));

        Sale sale = new SalesService().createSale(request);
        Product after = products.findById(product.getId()).orElseThrow();
        require(after.getQuantityInStock() != null && Math.abs(after.getQuantityInStock()) < 1e-9,
                "Unlimited product quantity changed after sale");

        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Long allocations = session.createQuery(
                            "SELECT COUNT(b) FROM SaleItemBatch b WHERE b.saleItem.sale.id = :saleId", Long.class)
                    .setParameter("saleId", sale.getId()).uniqueResult();
            Long movements = session.createQuery(
                            "SELECT COUNT(m) FROM InventoryMovement m WHERE m.referenceType = :type AND m.referenceId = :saleId",
                            Long.class)
                    .setParameter("type", "sale").setParameter("saleId", sale.getId()).uniqueResult();
            require(allocations != null && allocations == 0, "Unlimited sale created batch allocations");
            require(movements != null && movements == 1, "Unlimited sale did not create exactly one inventory movement");

            InventoryMovement movement = session.createQuery(
                            "FROM InventoryMovement m WHERE m.referenceType = :type AND m.referenceId = :saleId",
                            InventoryMovement.class)
                    .setParameter("type", "sale").setParameter("saleId", sale.getId()).uniqueResult();
            require(movement != null && Math.abs(movement.getQuantityDelta() + 7.0) < 1e-9,
                    "Unlimited sale movement has the wrong quantity");
            require(movement.getQuantityBefore() == null && movement.getQuantityAfter() == null && movement.getBatch() == null,
                    "Unlimited sale movement should not claim a physical stock balance or batch");
        }

        System.out.println("UNLIMITED_STOCK_SALE_SMOKE_OK");
        DatabaseManager.shutdown();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
