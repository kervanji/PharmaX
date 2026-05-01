package com.pharmax.service;

import com.pharmax.database.Repository.CustomerRepository;
import com.pharmax.database.Repository.CustomerPaymentRepository;
import com.pharmax.model.Customer;
import com.pharmax.model.CustomerPayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class CustomerService {
    private static final Logger logger = LoggerFactory.getLogger(CustomerService.class);
    private final CustomerRepository customerRepository;
    private final CustomerPaymentRepository paymentRepository;
    
    public CustomerService() {
        this.customerRepository = new CustomerRepository();
        this.paymentRepository = new CustomerPaymentRepository();
    }
    
    public Customer createCustomer(Customer customer) {
        logger.info("Creating new customer: {}", customer.getName());
        
        // Always generate sequential customer code on create (defensive)
        customer.setCustomerCode(generateCustomerCode());
        
        // Validate customer data
        validateCustomer(customer);
        
        return customerRepository.save(customer);
    }
    
    public Customer updateCustomer(Customer customer) {
        logger.info("Updating customer: {}", customer.getId());
        
        // Validate customer data
        validateCustomer(customer);
        
        return customerRepository.save(customer);
    }
    
    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }
    
    public Optional<Customer> getCustomerByCode(String customerCode) {
        return customerRepository.findByCustomerCode(customerCode);
    }
    
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }
    
    public List<Customer> searchCustomersByName(String name) {
        return customerRepository.findByNameContaining(name);
    }
    
    public void deleteCustomer(Long id) {
        logger.info("Deleting customer: {}", id);
        customerRepository.deleteById(id);
    }
    
    public void deleteCustomer(Customer customer) {
        logger.info("Deleting customer: {}", customer.getId());
        customerRepository.delete(customer);
    }
    
    private String generateCustomerCode() {
        return customerRepository.getNextCustomerCode();
    }

    public String previewNextCustomerCode() {
        return customerRepository.getNextCustomerCode();
    }
    
    private void validateCustomer(Customer customer) {
        if (customer.getName() == null || customer.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("اسم العميل مطلوب");
        }

        if (customer.getPhoneNumber() == null || customer.getPhoneNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("رقم الهاتف مطلوب");
        }

        if (!isValidPhoneNumber(customer.getPhoneNumber().trim())) {
            throw new IllegalArgumentException("رقم الهاتف غير صالح (يجب أن يبدأ بـ 07)");
        }

        if (customer.getCustomerCode() == null || customer.getCustomerCode().trim().isEmpty()) {
            throw new IllegalArgumentException("كود العميل مطلوب");
        }

        if (customer.getProjectLocation() == null || customer.getProjectLocation().trim().isEmpty()) {
            throw new IllegalArgumentException("مواقع المشاريع مطلوبة (أدخل موقعاً واحداً على الأقل)");
        }

        Optional<Customer> byCode = customerRepository.findByCustomerCode(customer.getCustomerCode());
        if (byCode.isPresent() && (customer.getId() == null || !byCode.get().getId().equals(customer.getId()))) {
            throw new IllegalArgumentException("كود العميل مستخدم بالفعل");
        }
    }
    
    private boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber.matches("^07\\d{9}$");
    }
    
    public Customer updateCustomerBalance(Long customerId, Double amount) {
        Optional<Customer> customerOpt = customerRepository.findById(customerId);
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            customer.setCurrentBalance(customer.getCurrentBalance() + amount);
            // Also update IQD balance for backward compatibility (sales are in IQD)
            customer.setBalanceIqd(customer.getBalanceIqd() + amount);
            return customerRepository.save(customer);
        }
        throw new IllegalArgumentException("العميل غير موجود");
    }
    
    public Customer updateCustomerBalanceByCurrency(Long customerId, Double amount, String currency) {
        Optional<Customer> customerOpt = customerRepository.findById(customerId);
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            if ("دولار".equals(currency) || "USD".equalsIgnoreCase(currency)) {
                customer.setBalanceUsd(customer.getBalanceUsd() + amount);
            } else {
                customer.setBalanceIqd(customer.getBalanceIqd() + amount);
                // Also update legacy current_balance for IQD
                customer.setCurrentBalance(customer.getCurrentBalance() + amount);
            }
            return customerRepository.save(customer);
        }
        throw new IllegalArgumentException("العميل غير موجود");
    }
    
    public List<Customer> getCustomersWithDebt() {
        return customerRepository.findAll().stream()
                .filter(customer -> customer.getBalanceIqd() < 0 || customer.getBalanceUsd() < 0)
                .toList();
    }
    
    public List<Customer> getCustomersWithCredit() {
        return customerRepository.findAll().stream()
                .filter(customer -> customer.getBalanceIqd() > 0 || customer.getBalanceUsd() > 0)
                .toList();
    }
    
    public List<Customer> getCustomersWithDebtByCurrency(String currency) {
        return customerRepository.findAll().stream()
                .filter(customer -> {
                    if ("دولار".equals(currency) || "USD".equalsIgnoreCase(currency)) {
                        return customer.getBalanceUsd() < 0;
                    } else {
                        return customer.getBalanceIqd() < 0;
                    }
                })
                .toList();
    }
    
    public List<Customer> getCustomersWithCreditByCurrency(String currency) {
        return customerRepository.findAll().stream()
                .filter(customer -> {
                    if ("دولار".equals(currency) || "USD".equalsIgnoreCase(currency)) {
                        return customer.getBalanceUsd() > 0;
                    } else {
                        return customer.getBalanceIqd() > 0;
                    }
                })
                .toList();
    }
    
    public CustomerPayment payToCustomer(Long customerId, Double amount, String paymentMethod, String notes, String processedBy) {
        logger.info("Processing payment to customer: {}", customerId);
        
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("المبلغ يجب أن يكون أكبر من صفر");
        }
        
        Optional<Customer> customerOpt = customerRepository.findById(customerId);
        if (customerOpt.isEmpty()) {
            throw new IllegalArgumentException("العميل غير موجود");
        }
        
        Customer customer = customerOpt.get();
        
        if (customer.getCurrentBalance() <= 0) {
            throw new IllegalArgumentException("العميل ليس لديه رصيد دائن (نحن لسنا مدينين له)");
        }
        
        if (amount > customer.getCurrentBalance()) {
            throw new IllegalArgumentException("المبلغ المدخل أكبر من الرصيد الدائن للعميل");
        }
        
        CustomerPayment payment = new CustomerPayment();
        payment.setPaymentCode(paymentRepository.generatePaymentCode());
        payment.setCustomer(customer);
        payment.setAmount(amount);
        payment.setPaymentMethod(paymentMethod);
        payment.setNotes(notes);
        payment.setProcessedBy(processedBy);
        
        CustomerPayment savedPayment = paymentRepository.save(payment);
        
        updateCustomerBalance(customerId, -amount);
        
        logger.info("Payment to customer completed: {}", savedPayment.getPaymentCode());
        return savedPayment;
    }
    
    public List<CustomerPayment> getCustomerPayments(Long customerId) {
        return paymentRepository.findByCustomerId(customerId);
    }
}
