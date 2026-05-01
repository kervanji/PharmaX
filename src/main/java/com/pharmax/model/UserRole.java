package com.pharmax.model;

public enum UserRole {
    ADMIN("مدير", true, true, true, true, true, true, true, true),
    SELLER("بائع", true, false, false, false, false, false, false, false);

    private final String displayName;
    private final boolean canCreateSale;
    private final boolean canSeeCost;
    private final boolean canSeeProfit;
    private final boolean canEditProducts;
    private final boolean canDeleteInvoices;
    private final boolean canManageUsers;
    private final boolean canAccessReports;
    private final boolean canAccessSettings;

    UserRole(String displayName, boolean canCreateSale, boolean canSeeCost, boolean canSeeProfit,
             boolean canEditProducts, boolean canDeleteInvoices, boolean canManageUsers,
             boolean canAccessReports, boolean canAccessSettings) {
        this.displayName = displayName;
        this.canCreateSale = canCreateSale;
        this.canSeeCost = canSeeCost;
        this.canSeeProfit = canSeeProfit;
        this.canEditProducts = canEditProducts;
        this.canDeleteInvoices = canDeleteInvoices;
        this.canManageUsers = canManageUsers;
        this.canAccessReports = canAccessReports;
        this.canAccessSettings = canAccessSettings;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean canCreateSale() {
        return canCreateSale;
    }

    public boolean canSeeCost() {
        return canSeeCost;
    }

    public boolean canSeeProfit() {
        return canSeeProfit;
    }

    public boolean canEditProducts() {
        return canEditProducts;
    }

    public boolean canDeleteInvoices() {
        return canDeleteInvoices;
    }

    public boolean canManageUsers() {
        return canManageUsers;
    }

    public boolean canAccessReports() {
        return canAccessReports;
    }

    public boolean canAccessSettings() {
        return canAccessSettings;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
