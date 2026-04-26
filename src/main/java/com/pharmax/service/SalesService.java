package com.pharmax.service;

import com.pharmax.database.Repository.SaleRepository;
import com.pharmax.database.Repository.CustomerRepository;
import com.pharmax.database.Repository.ProductRepository;
import com.pharmax.database.Repository.SaleItemRepository;
import com.pharmax.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SalesService {
    private static final Logger logger = LoggerFactory.getLogger(SalesService.class);
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    
    public SalesService() {
        this.saleRepository = new SaleRepository();
        this.saleItemRepository = new SaleItemRepository();
        this.customerRepository = new CustomerRepository();
        this.productRepository = new ProductRepository();
        this.inventoryService = new InventoryService();
        this.customerService = new CustomerService();
    }
    
    public Sale createSale(SaleRequest saleRequest) {
        logger.info("Creating new sale for customer: {}", saleRequest.getCustomerId());
        
        // Validate customer exists
        Optional<Customer> customerOpt = customerRepository.findById(saleRequest.getCustomerId());
        if (customerOpt.isEmpty()) {
            throw new IllegalArgumentException("العميل غير موجود");
        }
        
        Customer customer = customerOpt.get();
        
        // Create sale
        Sale sale = new Sale();
        sale.setSaleCode(generateSaleCode());
        sale.setCustomer(customer);
        sale.setProjectLocation(saleRequest.getProjectLocation());
        sale.setPaymentMethod(saleRequest.getPaymentMethod());
        sale.setCurrency(saleRequest.getCurrency() != null ? saleRequest.getCurrency() : "دينار");
        sale.setNotes(saleRequest.getNotes());
        sale.setCreatedBy(saleRequest.getCreatedBy());
        
        List<SaleItem> saleItems = new ArrayList<>();
        double totalAmount = 0.0;
        
        // Process sale items
        for (SaleItemRequest itemRequest : saleRequest.getItems()) {
            Optional<Product> productOpt = productRepository.findById(itemRequest.getProductId());
            if (productOpt.isEmpty()) {
                throw new IllegalArgumentException("المنتج غير موجود: " + itemRequest.getProductId());
            }
            
            Product product = productOpt.get();

            if (!inventoryService.isStockAvailable(product.getId(), itemRequest.getQuantity())) {
                throw new IllegalArgumentException("الكمية غير متوفرة للمنتج: " + product.getName());
            }
            
            // Create sale item
            SaleItem saleItem = new SaleItem();
            saleItem.setSale(sale);
            saleItem.setProduct(product);
            saleItem.setQuantity(itemRequest.getQuantity());
            saleItem.setPriceType(itemRequest.getPriceType() != null ? itemRequest.getPriceType() : "مفرد");
            
            // Use price from request; fallback respects the sale currency so USD-only
            // products do not become zero/blank in USD receipts.
            double unitPrice = itemRequest.getUnitPrice() != null
                    ? itemRequest.getUnitPrice()
                    : getProductPriceForCurrency(product, sale.getCurrency());
            saleItem.setUnitPrice(unitPrice);
            
            saleItem.setDiscountPercentage(itemRequest.getDiscountPercentage());
            
            // Calculate total price with discount
            double itemTotal = unitPrice * itemRequest.getQuantity();
            double discountAmount = itemTotal * (itemRequest.getDiscountPercentage() / 100.0);
            saleItem.setDiscountAmount(discountAmount);
            saleItem.setTotalPrice(itemTotal - discountAmount);
            
            saleItems.add(saleItem);
            totalAmount += saleItem.getTotalPrice();
            
            // Update inventory
            inventoryService.removeStock(product.getId(), itemRequest.getQuantity());
        }
        
        // Set sale totals (no tax)
        sale.setTotalAmount(totalAmount);
        sale.setDiscountAmount(saleRequest.getAdditionalDiscount() != null ? saleRequest.getAdditionalDiscount() : 0.0);
        sale.setTaxAmount(0.0);
        sale.setFinalAmount(totalAmount - sale.getDiscountAmount());

        double paidAmount = saleRequest.getPaidAmount() != null ? saleRequest.getPaidAmount() : 0.0;
        sale.setPaidAmount(paidAmount);
        sale.setPaymentStatus(paidAmount + 1e-9 >= sale.getFinalAmount() ? "PAID" : "PENDING");
        
        // Save sale and items
        Sale savedSale = saleRepository.save(sale);
        saleItems.forEach(item -> {
            item.setSale(savedSale);
            saleItemRepository.save(item);
        });

        // Update customer balance by the difference (credit/debt)
        // current_balance > 0 => credit for customer (we owe), < 0 => debt on customer
        customerService.updateCustomerBalanceByCurrency(
            customer.getId(),
            paidAmount - sale.getFinalAmount(),
            sale.getCurrency()
        );
        
        logger.info("Sale created successfully: {}", savedSale.getSaleCode());
        return savedSale;
    }
    
    public Optional<Sale> getSaleById(Long id) {
        return saleRepository.findById(id);
    }
    
    public Optional<Sale> getSaleByCode(String saleCode) {
        return saleRepository.findBySaleCode(saleCode);
    }
    
    public List<Sale> getAllSales() {
        new ReceiptService().ensureSingleReceiptPerSale();
        return saleRepository.findAllWithCustomer();
    }
    
    public List<Sale> getSalesByCustomerId(Long customerId) {
        return saleRepository.findByCustomerId(customerId);
    }

    public List<Sale> getSalesForAccountStatement(Long customerId,
                                                  String projectLocation,
                                                  LocalDateTime from,
                                                  LocalDateTime to,
                                                  boolean includeItems) {
        if (customerId == null) {
            throw new IllegalArgumentException("العميل غير موجود");
        }
        return saleRepository.findForAccountStatement(customerId, projectLocation, from, to, includeItems);
    }
    
    public void deleteSale(Long id) {
        Optional<Sale> saleOpt = saleRepository.findById(id);
        if (saleOpt.isPresent()) {
            Sale sale = saleOpt.get();
            
            // Restore inventory
            for (SaleItem item : sale.getSaleItems()) {
                inventoryService.addStock(item.getProduct().getId(), item.getQuantity());
            }
            
            // Revert customer balance effect of this sale
            double paid = sale.getPaidAmount() != null ? sale.getPaidAmount() : 0.0;
            customerService.updateCustomerBalanceByCurrency(
                sale.getCustomer().getId(),
                sale.getFinalAmount() - paid,
                sale.getCurrency()
            );
            
            saleRepository.delete(sale);
            logger.info("Sale deleted: {}", id);
        }
    }
    
    public Sale updatePaymentStatus(Long saleId, String newStatus) {
        Optional<Sale> saleOpt = saleRepository.findById(saleId);
        if (saleOpt.isPresent()) {
            Sale sale = saleOpt.get();
            sale.setPaymentStatus(newStatus);
            
            // If payment is completed, update customer balance
            if ("PAID".equals(newStatus)) {
                double currentPaid = sale.getPaidAmount() != null ? sale.getPaidAmount() : 0.0;
                double remaining = sale.getFinalAmount() - currentPaid;
                if (Math.abs(remaining) > 1e-9) {
                    customerService.updateCustomerBalance(sale.getCustomer().getId(), remaining);
                }
                sale.setPaidAmount(sale.getFinalAmount());
            }
            
            return saleRepository.save(sale);
        }
        throw new IllegalArgumentException("البيع غير موجود");
    }
    
    private String generateSaleCode() {
        return String.valueOf(saleRepository.getNextSaleCodeNumeric());
    }

    private double getProductPriceForCurrency(Product product, String currency) {
        Double price = "دولار".equals(currency) ? product.getUnitPriceUsd() : product.getUnitPrice();
        if (price == null || price <= 0) {
            throw new IllegalArgumentException("لا يوجد سعر صالح للمنتج: " + product.getName());
        }
        return price;
    }
    
    
    public double getTotalSalesAmount() {
        return saleRepository.findAll().stream()
                .mapToDouble(Sale::getFinalAmount)
                .sum();
    }
    
    public double getSalesAmountByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return saleRepository.findAll().stream()
                .filter(sale -> !sale.getSaleDate().isBefore(startDate) && 
                               !sale.getSaleDate().isAfter(endDate))
                .mapToDouble(Sale::getFinalAmount)
                .sum();
    }
    
    public List<Sale> getPendingPayments() {
        return saleRepository.findAll().stream()
                .filter(sale -> "PENDING".equals(sale.getPaymentStatus()))
                .toList();
    }
    
    // Request DTOs
    public static class SaleRequest {
        private Long customerId;
        private String projectLocation;
        private String paymentMethod;
        private String currency;
        private String notes;
        private String createdBy;
        private Double additionalDiscount;
        private Double paidAmount;
        private List<SaleItemRequest> items;
        
        // Getters and Setters
        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }

        public String getProjectLocation() { return projectLocation; }
        public void setProjectLocation(String projectLocation) { this.projectLocation = projectLocation; }
        
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        
        public Double getAdditionalDiscount() { return additionalDiscount; }
        public void setAdditionalDiscount(Double additionalDiscount) { this.additionalDiscount = additionalDiscount; }

        public Double getPaidAmount() { return paidAmount; }
        public void setPaidAmount(Double paidAmount) { this.paidAmount = paidAmount; }
        
        public List<SaleItemRequest> getItems() { return items; }
        public void setItems(List<SaleItemRequest> items) { this.items = items; }
    }
    
    public static class SaleItemRequest {
        private Long productId;
        private Double quantity;
        private Double unitPrice;
        private Double discountPercentage;
        private String priceType;
        
        // Getters and Setters
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        
        public Double getQuantity() { return quantity; }
        public void setQuantity(Double quantity) { this.quantity = quantity; }
        
        public Double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
        
        public Double getDiscountPercentage() { return discountPercentage; }
        public void setDiscountPercentage(Double discountPercentage) { this.discountPercentage = discountPercentage; }
        
        public String getPriceType() { return priceType; }
        public void setPriceType(String priceType) { this.priceType = priceType; }
    }
}
