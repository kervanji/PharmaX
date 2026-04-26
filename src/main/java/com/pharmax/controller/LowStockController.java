package com.pharmax.controller;

import com.pharmax.model.Product;
import com.pharmax.service.InventoryService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class LowStockController {
    @FXML private TableView<Product> lowStockTable;
    @FXML private TableColumn<Product, String> codeColumn;
    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, String> categoryColumn;
    @FXML private TableColumn<Product, Double> currentStockColumn;
    @FXML private TableColumn<Product, Double> minimumStockColumn;
    @FXML private TableColumn<Product, Double> neededColumn;
    @FXML private TableColumn<Product, String> costColumn;
    @FXML private TableColumn<Product, Void> actionsColumn;
    @FXML private Label lowStockCountLabel;
    @FXML private Label outOfStockLabel;
    @FXML private Label restockCostLabel;
    @FXML private Label lastUpdateLabel;
    
    private final InventoryService inventoryService = new InventoryService();
    private final DecimalFormat numberFormat;
    
    public LowStockController() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        numberFormat = new DecimalFormat("#,##0.00", symbols);
    }
    
    @FXML
    private void initialize() {
        setupTableColumns();
        loadLowStockProducts();
    }
    
    private void setupTableColumns() {
        codeColumn.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        currentStockColumn.setCellValueFactory(new PropertyValueFactory<>("quantityInStock"));
        minimumStockColumn.setCellValueFactory(new PropertyValueFactory<>("minimumStock"));
        
        neededColumn.setCellValueFactory(cellData -> {
            Product p = cellData.getValue();
            double min = p.getMinimumStock() != null ? p.getMinimumStock() : 0.0;
            double curr = p.getQuantityInStock() != null ? p.getQuantityInStock() : 0.0;
            double needed = Math.max(0, min - curr);
            return new SimpleDoubleProperty(needed).asObject();
        });
        
        costColumn.setCellValueFactory(cellData -> {
            Product p = cellData.getValue();
            double min = p.getMinimumStock() != null ? p.getMinimumStock() : 0.0;
            double curr = p.getQuantityInStock() != null ? p.getQuantityInStock() : 0.0;
            double needed = Math.max(0, min - curr);
            double cost = needed * (p.getCostPrice() != null ? p.getCostPrice() : 0);
            return new SimpleStringProperty(String.format("%s د.ع", numberFormat.format(cost)));
        });
        
        currentStockColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.valueOf(item));
                    if (item == 0) {
                        setStyle("-fx-text-fill: -fx-danger-text; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: -fx-warning-text; -fx-font-weight: bold;");
                    }
                }
            }
        });
        
        setupActionsColumn();
    }
    
    private void setupActionsColumn() {
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button addStockBtn = new Button("إضافة مخزون");
            
            {
                addStockBtn.setStyle("-fx-background-color: -fx-success-text; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 12; -fx-background-radius: 4;");
                addStockBtn.setOnAction(e -> handleAddStock(getTableView().getItems().get(getIndex())));
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(addStockBtn);
                }
            }
        });
    }
    
    private void loadLowStockProducts() {
        List<Product> lowStockProducts = inventoryService.getLowStockProducts();
        ObservableList<Product> productsList = FXCollections.observableArrayList(lowStockProducts);
        lowStockTable.setItems(productsList);
        
        updateStatistics(lowStockProducts);
        updateLastUpdateTime();
    }
    
    private void updateStatistics(List<Product> products) {
        int lowStock = 0;
        int outOfStock = 0;
        double totalRestockCost = 0;
        
        for (Product p : products) {
            double qty = p.getQuantityInStock() != null ? p.getQuantityInStock() : 0.0;
            if (qty == 0) {
                outOfStock++;
            } else {
                lowStock++;
            }
            
            double min = p.getMinimumStock() != null ? p.getMinimumStock() : 0.0;
            double needed = Math.max(0, min - qty);
            totalRestockCost += needed * (p.getCostPrice() != null ? p.getCostPrice() : 0);
        }
        
        lowStockCountLabel.setText(String.valueOf(lowStock));
        outOfStockLabel.setText(String.valueOf(outOfStock));
        restockCostLabel.setText(String.format("%s د.ع", numberFormat.format(totalRestockCost)));
    }
    
    private void updateLastUpdateTime() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
        lastUpdateLabel.setText("آخر تحديث: " + time);
    }
    
    private void handleAddStock(Product product) {
        double min = product.getMinimumStock() != null ? product.getMinimumStock() : 0.0;
        double curr = product.getQuantityInStock() != null ? product.getQuantityInStock() : 0.0;
        double needed = Math.max(1, min - curr);
        
        TextInputDialog dialog = new TextInputDialog(String.valueOf(needed));
        dialog.setTitle("إضافة مخزون");
        dialog.setHeaderText("إضافة مخزون للمنتج: " + product.getName());
        dialog.setContentText("الكمية:");
        
        dialog.showAndWait().ifPresent(quantity -> {
            try {
                double qty = Double.parseDouble(quantity);
                if (qty > 0) {
                    inventoryService.addStock(product.getId(), qty);
                    loadLowStockProducts();
                    showInfo("تم", "تمت إضافة " + qty + " وحدة إلى مخزون " + product.getName());
                }
            } catch (NumberFormatException e) {
                showError("خطأ", "الكمية يجب أن تكون رقماً");
            } catch (Exception e) {
                showError("خطأ", e.getMessage());
            }
        });
    }
    
    @FXML
    private void handleRefresh() {
        loadLowStockProducts();
    }
    
    @FXML
    private void handleExport() {
        showInfo("قريباً", "ميزة تصدير التقرير قيد التطوير");
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
