package com.pharmax.util;

import com.pharmax.model.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central access control for dashboard tiles and tab screens.
 */
public final class DashboardAccessService {
    private static final Logger logger = LoggerFactory.getLogger(DashboardAccessService.class);

    private static final Map<String, TilePermission> TILE_PERMISSIONS = new HashMap<>();
    private static final Map<String, String> FXML_TO_TILE = new HashMap<>();

    static {
        registerTile("pos", false, false, false, "/views/SaleForm.fxml");
        registerTile("view-sales", false, false, false, "/views/SaleList.fxml");
        registerTile("view-inventory", false, false, false, "/views/InventoryList.fxml");
        registerTile("new-product", false, false, false, "/views/ProductForm.fxml");
        registerTile("product-return", false, false, false, "/views/ReturnList.fxml");
        registerTile("low-stock", false, false, false, "/views/LowStockList.fxml");
        registerTile("expiry-alerts", false, false, false, "/views/ExpiryAlerts.fxml");
        registerTile("purchase", false, false, false, "/views/Purchase.fxml");
        registerTile("purchase-return", false, false, false, "/views/PurchaseReturn.fxml");
        registerTile("payment-voucher", false, false, false, "/views/PaymentVoucher.fxml");
        registerTile("cashbox", false, false, false, "/views/Cashbox.fxml");
        registerTile("barcode-print", false, false, false, "/views/BarcodePrint.fxml");
        registerTile("user-management", true, false, false, "/views/UserManagement.fxml");
        registerTile("pharmacy-reports", false, true, false, "/views/PharmacyReports.fxml");
        registerTile("settings", false, false, true, "/views/Settings.fxml");
        registerTile("about", false, false, false, "/views/About.fxml");
        registerTile("receipt-voucher", false, false, false, "/views/ReceiptVoucher.fxml");
        registerTile("sales-report", false, true, false, "/views/SalesReport.fxml");
        registerTile("accounts", false, false, false, "/views/Accounts.fxml");
        registerTile("categories", false, false, false, "/views/CategoryManager.fxml");
        registerTile("add-stock", false, false, false, "/views/AddStockDialog.fxml");
        registerTile("voucher-list", false, false, false, "/views/VoucherList.fxml");
    }

    private DashboardAccessService() {
    }

    private static void registerTile(String tileId, boolean adminOnly, boolean reportOnly, boolean settingsOnly, String fxmlPath) {
        TILE_PERMISSIONS.put(tileId, new TilePermission(adminOnly, reportOnly, settingsOnly));
        if (fxmlPath != null) {
            FXML_TO_TILE.put(fxmlPath, tileId);
        }
    }

    public static boolean canAccessTile(String tileId) {
        if (tileId == null || tileId.isBlank()) {
            return true;
        }

        SessionManager session = SessionManager.getInstance();
        if (!session.isLoggedIn()) {
            return false;
        }

        String username = session.getCurrentUsername();
        Set<String> hiddenForAll = DashboardLayoutService.loadHiddenTiles(username);
        if (hiddenForAll.contains(tileId)) {
            logger.debug("Tile {} hidden for user {}", tileId, username);
            return false;
        }

        if (session.getCurrentRole() == UserRole.SELLER
                && DashboardLayoutService.loadSellerHiddenTiles().contains(tileId)) {
            logger.debug("Tile {} hidden for seller {}", tileId, username);
            return false;
        }

        TilePermission permission = TILE_PERMISSIONS.get(tileId);
        if (permission == null) {
            return true;
        }
        if (permission.adminOnly && !session.canManageUsers()) {
            return false;
        }
        if (permission.reportOnly && !session.canAccessReports()) {
            return false;
        }
        if (permission.settingsOnly && !session.canAccessSettings()) {
            return false;
        }
        return true;
    }

    public static boolean canOpenFxml(String fxmlPath) {
        if (fxmlPath == null || fxmlPath.isBlank()) {
            return true;
        }
        String tileId = FXML_TO_TILE.get(fxmlPath);
        if (tileId == null) {
            return true;
        }
        return canAccessTile(tileId);
    }

    public static String tileForFxml(String fxmlPath) {
        return FXML_TO_TILE.get(fxmlPath);
    }

    public static Map<String, String> fxmlMappings() {
        return Collections.unmodifiableMap(FXML_TO_TILE);
    }

    private record TilePermission(boolean adminOnly, boolean reportOnly, boolean settingsOnly) {
    }
}
