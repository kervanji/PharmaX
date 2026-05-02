package com.pharmax.controller;

import com.pharmax.model.Product;
import com.pharmax.service.InventoryService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
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
    @FXML private Label dataQualityCountLabel;
    @FXML private Button dataQualityAlertsButton;
    
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
        updateDataQualitySummary();
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

    @FXML
    private void handleDataQualityAlerts() {
        List<InventoryService.DataQualityAlert> alerts = inventoryService.getDataQualityAlerts();
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("تنبيهات جودة البيانات");

        VBox root = new VBox(16);
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color: -fx-bg-gradient;");

        Label titleLabel = new Label("تنبيهات جودة البيانات");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: -fx-form-label;");

        Label subtitleLabel = new Label("تنبيهات للبيانات التي قد تحتاج مراجعة بدون تغيير أي كميات أو حركات.");
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -fx-form-sublabel;");

        ComboBox<String> filterBox = new ComboBox<>();
        filterBox.setItems(FXCollections.observableArrayList(
                "الكل",
                "بدون باركود",
                "بدون سعر بيع",
                "بدون سعر تكلفة",
                "بدون دفعات",
                "كمية سالبة",
                "عدم تطابق الكمية"
        ));
        filterBox.setValue("الكل");

        Label summaryLabel = new Label();
        summaryLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -fx-form-sublabel;");

        TableView<InventoryService.DataQualityAlert> alertsTable = new TableView<>();
        alertsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<InventoryService.DataQualityAlert, String> typeColumn = new TableColumn<>("نوع التنبيه");
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTypeLabel()));

        TableColumn<InventoryService.DataQualityAlert, String> codeColumn = new TableColumn<>("الكود");
        codeColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getProduct().getProductCode() != null ? cell.getValue().getProduct().getProductCode() : "-"
        ));

        TableColumn<InventoryService.DataQualityAlert, String> nameColumn = new TableColumn<>("اسم المنتج");
        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getProduct().getName()));

        TableColumn<InventoryService.DataQualityAlert, String> stockColumn = new TableColumn<>("المخزون");
        stockColumn.setCellValueFactory(cell -> new SimpleStringProperty(numberFormat.format(cell.getValue().getQuantityInStock())));

        TableColumn<InventoryService.DataQualityAlert, String> batchTotalColumn = new TableColumn<>("مجموع الدفعات");
        batchTotalColumn.setCellValueFactory(cell -> new SimpleStringProperty(numberFormat.format(cell.getValue().getBatchTotalQuantity())));

        TableColumn<InventoryService.DataQualityAlert, String> batchCountColumn = new TableColumn<>("عدد الدفعات");
        batchCountColumn.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getBatchRecordCount())));

        TableColumn<InventoryService.DataQualityAlert, String> noteColumn = new TableColumn<>("ملاحظة");
        noteColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMessage()));

        alertsTable.getColumns().addAll(typeColumn, codeColumn, nameColumn, stockColumn, batchTotalColumn, batchCountColumn, noteColumn);

        ObservableList<InventoryService.DataQualityAlert> source = FXCollections.observableArrayList(alerts);
        FilteredList<InventoryService.DataQualityAlert> filteredAlerts = new FilteredList<>(source, alert -> true);
        alertsTable.setItems(filteredAlerts);

        Runnable refreshSummary = () -> summaryLabel.setText("عدد التنبيهات: " + filteredAlerts.size());
        filterBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            filteredAlerts.setPredicate(alert -> matchesAlertFilter(alert, newValue));
            refreshSummary.run();
        });
        refreshSummary.run();

        Button closeButton = new Button("إغلاق");
        closeButton.setStyle("-fx-background-color: -fx-success-text; -fx-text-fill: white; -fx-padding: 8 18; -fx-background-radius: 8;");
        closeButton.setOnAction(e -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(12, new Label("التصفية:"), filterBox, spacer, summaryLabel);
        controls.setStyle("-fx-alignment: center-left;");

        HBox footer = new HBox(closeButton);
        footer.setStyle("-fx-alignment: center-right;");

        VBox.setVgrow(alertsTable, Priority.ALWAYS);
        root.getChildren().addAll(titleLabel, subtitleLabel, controls, alertsTable, footer);

        stage.setScene(new Scene(root, 980, 520));
        stage.showAndWait();
    }

    private boolean matchesAlertFilter(InventoryService.DataQualityAlert alert, String filter) {
        if (filter == null || filter.equals("الكل")) {
            return true;
        }
        return filter.equals(alert.getTypeLabel());
    }

    private void updateDataQualitySummary() {
        int count = inventoryService.getDataQualityAlerts().size();
        if (dataQualityCountLabel != null) {
            if (count == 0) {
                dataQualityCountLabel.setText("لا توجد تنبيهات جودة بيانات");
                dataQualityCountLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -fx-success-text;");
            } else {
                dataQualityCountLabel.setText("تنبيهات جودة البيانات: " + count);
                dataQualityCountLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -fx-warning-text; -fx-font-weight: bold;");
            }
        }
        if (dataQualityAlertsButton != null) {
            dataQualityAlertsButton.setText(count > 0 ? "تنبيهات الجودة (" + count + ")" : "تنبيهات الجودة");
        }
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
