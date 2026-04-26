package com.pharmax.service;

import com.pharmax.model.*;
import com.pharmax.model.dto.StatementItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class StatementService {
    private static final Logger logger = LoggerFactory.getLogger(StatementService.class);

    // Dependencies
    private final SalesService salesService;
    private final VoucherService voucherService;
    private final ReturnService returnService;
    private final CustomerService customerService;

    public StatementService() {
        this.salesService = new SalesService();
        this.voucherService = new VoucherService();
        this.returnService = new ReturnService();
        this.customerService = new CustomerService();
    }

    /**
     * Generate statement of account for a specific customer.
     * 
     * @param customerId      Customer ID
     * @param projectLocation Optional filter by project location
     * @param from            Start date (inclusive)
     * @param to              End date (inclusive)
     * @return List of sorted statement items with calculated balance
     */
    /**
     * Generate statement of account for a specific customer.
     * 
     * @param customerId      Customer ID
     * @param projectLocation Optional filter by project location
     * @param currency        Filter by currency (required)
     * @param from            Start date (inclusive)
     * @param to              End date (inclusive)
     * @return List of sorted statement items with calculated balance
     */
    public List<StatementItem> getStatement(Long customerId, String projectLocation, String currency,
            LocalDateTime from, LocalDateTime to) {
        List<StatementItem> items = new ArrayList<>();

        if (currency == null || currency.isEmpty()) {
            throw new IllegalArgumentException("العملة مطلوبة");
        }

        // Fetch Sales
        List<Sale> sales = salesService.getSalesByCustomerId(customerId);
        for (Sale sale : sales) {
            if (projectLocation != null && !projectLocation.isEmpty()
                    && !projectLocation.equals(sale.getProjectLocation())) {
                continue;
            }
            if (!"الكل".equals(currency) && !currency.equals(sale.getCurrency())) {
                continue;
            }

            if (sale.getFinalAmount() > 0) {
                items.add(new StatementItem(
                        sale.getSaleDate(),
                        "فاتورة مبيع",
                        sale.getSaleCode(),
                        sale.getNotes(),
                        sale.getFinalAmount(), // Debit (We sold -> they owe us)
                        0.0,
                        sale.getCurrency(),
                        sale));
            }

            if (sale.getPaidAmount() != null && sale.getPaidAmount() > 0) {
                items.add(new StatementItem(
                        sale.getSaleDate().plusSeconds(1),
                        "تسديد فاتورة",
                        sale.getSaleCode(),
                        "دفعة لفاتورة " + sale.getSaleCode(),
                        0.0,
                        sale.getPaidAmount(), // Credit
                        sale.getCurrency(),
                        sale));
            }
        }

        // Fetch Vouchers (Receipts & Payments)
        List<Voucher> vouchers = voucherService.getVouchersByCustomer(customerId);
        for (Voucher voucher : vouchers) {
            if (projectLocation != null && !projectLocation.isEmpty()
                    && !projectLocation.equals(voucher.getProjectName())) {
                continue;
            }
            if (!"الكل".equals(currency) && !currency.equals(voucher.getCurrency())) {
                continue;
            }

            if (!Boolean.TRUE.equals(voucher.getIsCancelled())) {
                if (voucher.getVoucherType() == VoucherType.RECEIPT) {
                    items.add(new StatementItem(
                            voucher.getVoucherDate(),
                            "سند قبض",
                            voucher.getVoucherNumber(),
                            voucher.getDescription(),
                            0.0,
                            voucher.getAmount(), // Credit
                            voucher.getCurrency(),
                            voucher));
                } else if (voucher.getVoucherType() == VoucherType.PAYMENT) {
                    items.add(new StatementItem(
                            voucher.getVoucherDate(),
                            "سند الدفع",
                            voucher.getVoucherNumber(),
                            voucher.getDescription(),
                            voucher.getAmount(), // Debit
                            0.0,
                            voucher.getCurrency(),
                            voucher));
                } else if (voucher.getVoucherType() == VoucherType.PURCHASE) {
                    items.add(new StatementItem(
                            voucher.getVoucherDate(),
                            "مشتريات",
                            voucher.getVoucherNumber(),
                            voucher.getDescription(),
                            voucher.getAmount(), // Debit
                            0.0,
                            voucher.getCurrency(),
                            voucher));
                }
            }
        }

        // Fetch Returns
        List<SaleReturn> returns = returnService.getReturnsByCustomer(customerId);
        for (SaleReturn ret : returns) {
            if (projectLocation != null && !projectLocation.isEmpty()) {
                if (ret.getSale() == null || !projectLocation.equals(ret.getSale().getProjectLocation())) {
                    continue;
                }
            }

            String retCurrency = ret.getSale() != null && ret.getSale().getCurrency() != null
                    ? ret.getSale().getCurrency()
                    : "دينار";
            if (!"الكل".equals(currency) && !currency.equals(retCurrency)) {
                continue;
            }

            items.add(new StatementItem(
                    ret.getReturnDate(),
                    "مرتجع مبيعات",
                    ret.getReturnCode(),
                    ret.getReturnReason(),
                    0.0,
                    ret.getTotalReturnAmount(), // Credit
                    retCurrency,
                    ret));
        }

        // Sort by Date
        items.sort(Comparator.comparing(StatementItem::getDate));

        // Calculate Running Balance
        double balanceIqd = 0.0;
        double balanceUsd = 0.0;
        List<StatementItem> allItemsWithBalance = new ArrayList<>();

        for (StatementItem item : items) {
            double debit = item.getDebit() != null ? item.getDebit() : 0.0;
            double credit = item.getCredit() != null ? item.getCredit() : 0.0;
            String curr = item.getCurrency();

            // Debit (They owe us) -> Increases Debt (positive balance)
            // Credit (They paid) -> Decreases Debt
            if ("دولار".equals(curr)) {
                balanceUsd = balanceUsd + debit - credit;
                item.setBalance(balanceUsd);
            } else {
                balanceIqd = balanceIqd + debit - credit;
                item.setBalance(balanceIqd);
            }
            allItemsWithBalance.add(item);
        }

        // Filter by Date Range
        List<StatementItem> result = new ArrayList<>();

        // If start date is provided, add Opening Balance row
        if (from != null) {
            double openingBalIqd = 0.0;
            double openingBalUsd = 0.0;

            // Find the balance right before 'from' date for each currency
            for (StatementItem item : allItemsWithBalance) {
                if (item.getDate().isBefore(from)) {
                    if ("دولار".equals(item.getCurrency())) {
                        openingBalUsd = item.getBalance();
                    } else {
                        openingBalIqd = item.getBalance();
                    }
                } else {
                    break;
                }
            }

            if ("دولار".equals(currency) || "الكل".equals(currency)) {
                StatementItem openingUsd = new StatementItem(
                        from.minusSeconds(1),
                        "رصيد سابق",
                        "-",
                        "رصيد افتتاحي",
                        0.0,
                        0.0,
                        "دولار",
                        null);
                openingUsd.setBalance(openingBalUsd);
                if (openingBalUsd != 0 || "دولار".equals(currency)) {
                    result.add(openingUsd);
                }
            }
            if (!"دولار".equals(currency)) {
                StatementItem openingIqd = new StatementItem(
                        from.minusSeconds(1),
                        "رصيد سابق",
                        "-",
                        "رصيد افتتاحي",
                        0.0,
                        0.0,
                        "دينار",
                        null);
                openingIqd.setBalance(openingBalIqd);
                // Only add IQD opening if there's a balance or if the filter is specifically
                // IQD or All
                if (openingBalIqd != 0 || "دينار".equals(currency) || "الكل".equals(currency)) {
                    result.add(openingIqd);
                }
            }
        }

        for (StatementItem item : allItemsWithBalance) {
            boolean isBefore = from != null && item.getDate().isBefore(from);
            boolean isAfter = to != null && item.getDate().isAfter(to);

            if (!isBefore && !isAfter) {
                result.add(item);
            }
        }

        return result;
    }

    /**
     * Generate statement with item-level detail sub-rows.
     * For each Sale/SaleReturn row, inserts sub-rows showing the individual
     * products sold/returned.
     */
    public List<StatementItem> getStatementWithDetails(Long customerId, String projectLocation, String currency,
            LocalDateTime from, LocalDateTime to) {
        List<StatementItem> baseItems = getStatement(customerId, projectLocation, currency, from, to);
        List<StatementItem> result = new ArrayList<>();

        for (StatementItem item : baseItems) {
            result.add(item);
            Object src = item.getSourceObject();

            if (src instanceof Sale sale && "فاتورة مبيع".equals(item.getType())) {
                // Add detail rows for each sale item
                if (sale.getSaleItems() != null && !sale.getSaleItems().isEmpty()) {
                    for (SaleItem si : sale.getSaleItems()) {
                        String productName = si.getProduct() != null && si.getProduct().getName() != null
                                ? si.getProduct().getName()
                                : "منتج";
                        double qty = si.getQuantity() != null ? si.getQuantity() : 0;
                        double unitPrice = si.getUnitPrice() != null ? si.getUnitPrice() : 0;
                        double total = si.getTotalPrice() != null ? si.getTotalPrice() : 0;

                        StatementItem detailRow = new StatementItem(
                                item.getDate(), "مادة مبيعة", "", productName,
                                null, null, item.getCurrency(), si);
                        detailRow.setDetailRow(true);
                        detailRow.setProductName(productName);
                        detailRow.setItemQty(qty);
                        detailRow.setItemUnitPrice(unitPrice);
                        detailRow.setItemTotal(total);
                        result.add(detailRow);
                    }
                }
            } else if (src instanceof SaleReturn ret) {
                // Add detail rows for each return item
                List<ReturnItem> returnItems = ret.getReturnItems();
                if (returnItems != null && !returnItems.isEmpty()) {
                    for (ReturnItem ri : returnItems) {
                        String productName = ri.getProduct() != null && ri.getProduct().getName() != null
                                ? ri.getProduct().getName()
                                : "منتج";
                        double qty = ri.getQuantity() != null ? ri.getQuantity() : 0;
                        double unitPrice = ri.getUnitPrice() != null ? ri.getUnitPrice() : 0;
                        double total = ri.getTotalPrice() != null ? ri.getTotalPrice() : 0;

                        StatementItem detailRow = new StatementItem(
                                item.getDate(), "مادة مرتجعة", "", productName,
                                null, null, item.getCurrency(), ri);
                        detailRow.setDetailRow(true);
                        detailRow.setProductName(productName);
                        detailRow.setItemQty(qty);
                        detailRow.setItemUnitPrice(unitPrice);
                        detailRow.setItemTotal(total);
                        result.add(detailRow);
                    }
                }
            }
        }

        return result;
    }
}
