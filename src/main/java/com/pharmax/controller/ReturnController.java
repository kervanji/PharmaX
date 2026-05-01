package com.pharmax.controller;

import com.pharmax.model.*;
import com.pharmax.service.CustomerService;
import com.pharmax.service.ReturnService;
import com.pharmax.service.SalesService;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.prefs.Preferences;

public class ReturnController {
    private static final Logger logger = LoggerFactory.getLogger(ReturnController.class);
    private static final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");

    @FXML
    private ComboBox<Customer> customerComboBox;
    @FXML
    private ComboBox<String> saleComboBox;
    @FXML
    private ComboBox<String> currencyComboBox;
    @FXML
    private ComboBox<String> projectComboBox;
    @FXML
    private TableView<ReturnableItem> saleItemsTable;
    @FXML
    private TableColumn<ReturnableItem, Boolean> selectColumn;
    @FXML
    private TableColumn<ReturnableItem, String> productColumn;
    @FXML
    private TableColumn<ReturnableItem, Double> soldQtyColumn;
    @FXML
    private TableColumn<ReturnableItem, Double> returnedQtyColumn;
    @FXML
    private TableColumn<ReturnableItem, Double> availableQtyColumn;
    @FXML
    private TableColumn<ReturnableItem, Double> returnQtyColumn;
    @FXML
    private TableColumn<ReturnableItem, String> currencyColumn;
    @FXML
    private TableColumn<ReturnableItem, Double> unitPriceColumn;
    @FXML
    private TableColumn<ReturnableItem, Double> totalColumn;
    @FXML
    private TextArea notesArea;
    @FXML
    private Label totalReturnLabel;

    private final CustomerService customerService = new CustomerService();
    private final SalesService salesService = new SalesService();
    private final ReturnService returnService = new ReturnService();

    private Stage dialogStage;
    private boolean tabMode = false;
    private ObservableList<ReturnableItem> returnableItems = FXCollections.observableArrayList();
    private ObservableList<Sale> customerSalesList = FXCollections.observableArrayList();
    private Map<Long, Double> previousReturns = new HashMap<>();

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }

    @FXML
    private void initialize() {
        setupCustomerComboBox();
        setupFilters();
        setupTable();
        setupDefaults();
    }

    private void setupCustomerComboBox() {
        customerComboBox.setItems(FXCollections.observableArrayList(customerService.getAllCustomers()));
        customerComboBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Customer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        customerComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Customer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
    }

    private void setupFilters() {
        currencyComboBox.setItems(FXCollections.observableArrayList("الكل", "دينار", "دولار"));
        currencyComboBox.setValue("الكل");
        projectComboBox.setItems(FXCollections.observableArrayList("الكل"));
        projectComboBox.setValue("الكل");
        saleComboBox.setItems(FXCollections.observableArrayList("الكل"));
        saleComboBox.setValue("الكل");
    }

    private void setupTable() {
        selectColumn.setCellValueFactory(data -> data.getValue().selectedProperty());
        selectColumn.setCellFactory(col -> new CheckBoxTableCell<>());

        productColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        soldQtyColumn
                .setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getSoldQuantity()).asObject());
        returnedQtyColumn.setCellValueFactory(
                data -> new SimpleDoubleProperty(data.getValue().getReturnedQuantity()).asObject());
        availableQtyColumn.setCellValueFactory(
                data -> new SimpleDoubleProperty(data.getValue().getAvailableQuantity()).asObject());
        currencyColumn.setCellValueFactory(data -> {
            String curr = data.getValue().getSaleItem().getSale().getCurrency();
            return new SimpleStringProperty(curr != null ? curr : "دينار");
        });
        unitPriceColumn
                .setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getUnitPrice()).asObject());

        // Return quantity column with editable spinner
        returnQtyColumn.setCellValueFactory(data -> data.getValue().returnQuantityProperty().asObject());
        returnQtyColumn.setCellFactory(col -> new TableCell<>() {
            private final Spinner<Double> spinner = new Spinner<>(0.0, 9999.0, 0.0, 0.5);
            {
                spinner.setEditable(true);
                spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                    ReturnableItem item = getTableRow().getItem();
                    if (item != null && newVal != null) {
                        double max = item.getAvailableQuantity();
                        if (newVal > max) {
                            spinner.getValueFactory().setValue(max);
                        } else {
                            item.setReturnQuantity(newVal);
                            updateTotalReturn();
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ReturnableItem returnableItem = getTableRow().getItem();
                    if (returnableItem != null) {
                        spinner.getValueFactory().setValue(returnableItem.getReturnQuantity());
                        ((SpinnerValueFactory.DoubleSpinnerValueFactory) spinner.getValueFactory())
                                .setMax(returnableItem.getAvailableQuantity());
                    }
                    setGraphic(spinner);
                }
            }
        });

        totalColumn.setCellValueFactory(data -> {
            double total = data.getValue().getReturnQuantity() * data.getValue().getUnitPrice();
            return new SimpleDoubleProperty(total).asObject();
        });

        saleItemsTable.setItems(returnableItems);
        saleItemsTable.setEditable(true);
    }

    private void setupDefaults() {
    }

    @FXML
    private void handleCustomerChange() {
        Customer selected = customerComboBox.getValue();
        if (selected != null) {
            List<Sale> customerSales = salesService.getSalesByCustomerId(selected.getId());
            customerSalesList.setAll(customerSales);

            List<String> projects = customerSalesList.stream()
                    .map(Sale::getProjectLocation)
                    .filter(p -> p != null && !p.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
            projects.add(0, "الكل");
            projectComboBox.setItems(FXCollections.observableArrayList(projects));
            projectComboBox.setValue("الكل");

            currencyComboBox.setValue("الكل");
            saleComboBox.setValue("الكل");

            handleFilterChange();
        }
    }

    @FXML
    private void handleFilterChange() {
        if (customerComboBox.getValue() == null)
            return;

        String selectedCurrency = currencyComboBox.getValue();
        String selectedProject = projectComboBox.getValue();

        List<Sale> filteredSales = customerSalesList.stream()
                .filter(sale -> "الكل".equals(selectedCurrency) || selectedCurrency == null
                        || selectedCurrency.equals(sale.getCurrency() != null ? sale.getCurrency() : "دينار"))
                .filter(sale -> "الكل".equals(selectedProject) || selectedProject == null
                        || selectedProject.equals(sale.getProjectLocation()))
                .collect(Collectors.toList());

        List<String> invoices = filteredSales.stream()
                .map(Sale::getSaleCode)
                .distinct()
                .collect(Collectors.toList());
        invoices.add(0, "الكل");

        String currentInvoice = saleComboBox.getValue();
        saleComboBox.setItems(FXCollections.observableArrayList(invoices));
        if (currentInvoice != null && invoices.contains(currentInvoice)) {
            saleComboBox.setValue(currentInvoice);
        } else {
            saleComboBox.setValue("الكل");
        }

        String finalInvoice = saleComboBox.getValue();
        if (!"الكل".equals(finalInvoice) && finalInvoice != null) {
            filteredSales = filteredSales.stream()
                    .filter(s -> finalInvoice.equals(s.getSaleCode()))
                    .collect(Collectors.toList());
        }

        loadSaleItemsForSales(filteredSales);
    }

    private void loadSaleItemsForSales(List<Sale> sales) {
        returnableItems.clear();
        previousReturns.clear();

        if (sales != null) {
            for (Sale sale : sales) {
                List<SaleReturn> existingReturns = returnService.getReturnsBySale(sale.getId());
                for (SaleReturn ret : existingReturns) {
                    if (ret.getReturnItems() != null) {
                        for (ReturnItem ri : ret.getReturnItems()) {
                            if (ri.getOriginalSaleItem() == null || ri.getOriginalSaleItem().getId() == null) {
                                continue;
                            }
                            Long saleItemId = ri.getOriginalSaleItem().getId();
                            double quantity = ri.getQuantity() != null ? ri.getQuantity() : 0.0;
                            previousReturns.merge(saleItemId, quantity, Double::sum);
                        }
                    }
                }

                if (sale.getSaleItems() != null) {
                    for (SaleItem item : sale.getSaleItems()) {
                        double returned = previousReturns.getOrDefault(item.getId(), 0.0);
                        double available = item.getQuantity() - returned;
                        if (available > 0) {
                            ReturnableItem ri = new ReturnableItem(
                                    item,
                                    item.getProduct().getName(),
                                    item.getQuantity(),
                                    returned,
                                    available,
                                    item.getUnitPrice());
                            returnableItems.add(ri);
                        }
                    }
                }
            }
        }

        updateTotalReturn();
    }

    private void updateTotalReturn() {
        double totalIqd = 0.0;
        double totalUsd = 0.0;

        for (ReturnableItem item : returnableItems) {
            if (item.getReturnQuantity() > 0) {
                double amount = item.getReturnQuantity() * item.getUnitPrice();
                String curr = item.getSaleItem().getSale().getCurrency();
                if ("دولار".equals(curr)) {
                    totalUsd += amount;
                } else {
                    totalIqd += amount;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        if (totalIqd > 0 || totalUsd == 0) {
            sb.append(currencyFormat.format(totalIqd)).append(" د.ع");
        }
        if (totalUsd > 0) {
            if (sb.length() > 0)
                sb.append(" | ");
            sb.append(currencyFormat.format(totalUsd)).append(" $");
        }

        totalReturnLabel.setText(sb.toString());
    }

    @FXML
    private void handleConfirmReturn() {
        // We don't demand an explicit invoice if they just want to return items from
        // filtered results.
        // However, they must select some items.
        boolean hasItemsToReturn = returnableItems.stream().anyMatch(ri -> ri.getReturnQuantity() > 0);
        if (!hasItemsToReturn) {
            showError("خطأ", "الرجاء تحديد كمية للإرجاع");
            return;
        }

        Map<Sale, List<ReturnItem>> returnsBySale = new HashMap<>();
        for (ReturnableItem ri : returnableItems) {
            if (ri.getReturnQuantity() > 0) {
                ReturnItem returnItem = new ReturnItem();
                returnItem.setProduct(ri.getSaleItem().getProduct());
                returnItem.setOriginalSaleItem(ri.getSaleItem());
                returnItem.setQuantity(ri.getReturnQuantity());
                returnItem.setUnitPrice(ri.getUnitPrice());
                returnItem.setConditionStatus("GOOD");
                returnItem.setReturnReason(getNotesReason());
                Sale sale = ri.getSaleItem().getSale();
                returnsBySale.computeIfAbsent(sale, key -> new ArrayList<>()).add(returnItem);
            }
        }

        if (returnsBySale.isEmpty()) {
            showError("خطأ", "الرجاء تحديد كمية للإرجاع");
            return;
        }

        try {
            String reason = getNotesReason();

            // double totalReturned = 0.0; // No longer needed as we use
            // totalReturnLabel.getText()
            List<String> returnCodes = new ArrayList<>();
            for (Map.Entry<Sale, List<ReturnItem>> entry : returnsBySale.entrySet()) {
                SaleReturn savedReturn = returnService.createReturn(entry.getKey(), entry.getValue(), reason, "System");
                // totalReturned += savedReturn.getTotalReturnAmount() != null ?
                // savedReturn.getTotalReturnAmount() : 0.0; // No longer needed
                if (savedReturn.getReturnCode() != null) {
                    returnCodes.add(savedReturn.getReturnCode());
                }
            }

            showSuccess("تم بنجاح",
                    "تم إنشاء المرتجع بنجاح\n" +
                            "أرقام المرتجع: " + String.join(", ", returnCodes) + "\n" +
                            "المبلغ المرتجع: " + totalReturnLabel.getText());

            // Reload items to show updated available quantities
            handleFilterChange();

        } catch (Exception e) {
            logger.error("Failed to create return", e);
            showError("خطأ", "فشل في إنشاء المرتجع: " + e.getMessage());
        }
    }

    private String getNotesReason() {
        return notesArea.getText() == null ? "" : notesArea.getText().trim();
    }

    @FXML
    private void handlePrintReturnReceipt() {
        showInfo("معلومة", "يرجى طباعة إيصال المرتجع من شاشة الفواتير لكل فاتورة على حدة.");
    }

    @FXML
    private void handleViewReturns() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader();
            loader.setLocation(getClass().getResource("/views/ReturnList.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            javafx.scene.Parent root = loader.load();

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("سجل المرتجعات");
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            if (dialogStage != null) {
                stage.initOwner(dialogStage);
            }
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);
            stage.show();
        } catch (Exception e) {
            logger.error("Failed to open returns list", e);
            showError("خطأ", "فشل في فتح سجل المرتجعات");
        }
    }

    @FXML
    private void handleClose() {
        if (tabMode) {
            com.pharmax.util.TabManager.getInstance().closeTab("product-return");
        } else if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
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

    // Inner class to represent a returnable item
    public static class ReturnableItem {
        private final SaleItem saleItem;
        private final String productName;
        private final double soldQuantity;
        private final double returnedQuantity;
        private final double availableQuantity;
        private final double unitPrice;
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        private final SimpleDoubleProperty returnQuantity = new SimpleDoubleProperty(0.0);

        public ReturnableItem(SaleItem saleItem, String productName, double soldQuantity,
                double returnedQuantity, double availableQuantity, double unitPrice) {
            this.saleItem = saleItem;
            this.productName = productName;
            this.soldQuantity = soldQuantity;
            this.returnedQuantity = returnedQuantity;
            this.availableQuantity = availableQuantity;
            this.unitPrice = unitPrice;
        }

        public SaleItem getSaleItem() {
            return saleItem;
        }

        public String getProductName() {
            return productName;
        }

        public double getSoldQuantity() {
            return soldQuantity;
        }

        public double getReturnedQuantity() {
            return returnedQuantity;
        }

        public double getAvailableQuantity() {
            return availableQuantity;
        }

        public double getUnitPrice() {
            return unitPrice;
        }

        public boolean isSelected() {
            return selected.get();
        }

        public void setSelected(boolean value) {
            selected.set(value);
        }

        public SimpleBooleanProperty selectedProperty() {
            return selected;
        }

        public double getReturnQuantity() {
            return returnQuantity.get();
        }

        public void setReturnQuantity(double value) {
            returnQuantity.set(value);
        }

        public SimpleDoubleProperty returnQuantityProperty() {
            return returnQuantity;
        }
    }
}
