package com.pharmax.controller;

import com.pharmax.MainApp;
import com.pharmax.model.Category;
import com.pharmax.model.InventoryMovement;
import com.pharmax.model.Product;
import com.pharmax.model.ProductBatch;
import com.pharmax.model.ProductUnit;
import com.pharmax.service.CategoryService;
import com.pharmax.service.InventoryService;
import com.pharmax.service.InventoryMovementService;
import com.pharmax.service.PrintService;
import com.pharmax.service.ProductBatchService;
import com.pharmax.service.ProductUnitService;
import com.pharmax.util.SessionManager;
import com.pharmax.util.TabManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InventoryListController {
    private static final Logger logger = LoggerFactory.getLogger(InventoryListController.class);

    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> categoryFilter;
    @FXML
    private ComboBox<String> statusFilter;
    @FXML
    private TableView<Product> productsTable;
    @FXML
    private TableColumn<Product, String> codeColumn;
    @FXML
    private TableColumn<Product, String> nameColumn;
    @FXML
    private TableColumn<Product, String> categoryColumn;
    @FXML
    private TableColumn<Product, String> barcodeColumn;
    @FXML
    private TableColumn<Product, String> costPriceColumn;
    @FXML
    private TableColumn<Product, String> unitPriceColumn;
    @FXML
    private TableColumn<Product, Double> quantityColumn;
    @FXML
    private TableColumn<Product, String> statusColumn;
    @FXML
    private TableColumn<Product, Void> actionsColumn;
    @FXML
    private Label totalProductsLabel;
    @FXML
    private Label totalStockLabel;
    @FXML
    private Label inventoryValueLabel;
    @FXML
    private Label lowStockLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Button batchDetailsButton;
    @FXML
    private Button movementHistoryButton;
    @FXML
    private Button printReportButton;

    private final InventoryService inventoryService = new InventoryService();
    private final CategoryService categoryService = new CategoryService();
    private final ProductBatchService productBatchService = new ProductBatchService();
    private final InventoryMovementService inventoryMovementService = new InventoryMovementService();
    private final ProductUnitService productUnitService = new ProductUnitService();
    private ObservableList<Product> productsList;
    private FilteredList<Product> filteredProducts;
    private final DecimalFormat numberFormat;
    private static final double QTY_EPSILON = 1e-6;

    public InventoryListController() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        numberFormat = new DecimalFormat("#,##0.00", symbols);
    }

    @FXML
    private void initialize() {
        setupTableColumns();
        setupFilters();
        setupSearch();
        setupSelectionActions();
        applyVisibilityRestrictions();
        loadProducts();
    }

    private void applyVisibilityRestrictions() {
        boolean canSeeCost = SessionManager.getInstance().canSeeCost();
        if (costPriceColumn != null) {
            costPriceColumn.setVisible(canSeeCost);
        }
        if (inventoryValueLabel != null && inventoryValueLabel.getParent() != null) {
            inventoryValueLabel.getParent().setVisible(canSeeCost);
            inventoryValueLabel.getParent().setManaged(canSeeCost);
        }
        if (printReportButton != null) {
            printReportButton.setVisible(canSeeCost);
            printReportButton.setManaged(canSeeCost);
        }
    }

    private void setupTableColumns() {
        codeColumn.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        barcodeColumn.setCellValueFactory(new PropertyValueFactory<>("barcode"));

        costPriceColumn.setCellValueFactory(cellData -> {
            Double cost = cellData.getValue().getCostPrice();
            return new SimpleStringProperty(cost != null ? numberFormat.format(cost) : "-");
        });

        unitPriceColumn.setCellValueFactory(cellData -> {
            Double price = cellData.getValue().getUnitPrice();
            return new SimpleStringProperty(price != null ? numberFormat.format(price) : "-");
        });

        quantityColumn.setCellValueFactory(cellData -> {
            Double quantity = cellData.getValue().getQuantityInStock();
            return new ReadOnlyObjectWrapper<>(quantity != null ? quantity : 0.0);
        });
        quantityColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Product product = getTableRow() != null ? getTableRow().getItem() : null;
                setText(formatInventoryQuantity(product, item != null ? item : 0.0));
                setGraphic(null);
            }
        });

        statusColumn.setCellValueFactory(cellData -> {
            Product p = cellData.getValue();
            boolean active = Boolean.TRUE.equals(p.getIsActive());
            if (active && Boolean.TRUE.equals(p.getIsUnlimitedStock())) {
                return new SimpleStringProperty("غير محدودة");
            }
            double qty = p.getQuantityInStock() == null ? 0 : p.getQuantityInStock();
            double min = p.getMinimumStock() == null ? 0 : p.getMinimumStock();
            if (!active)
                return new SimpleStringProperty("غير نشط");
            if (qty == 0)
                return new SimpleStringProperty("نفد");
            if (qty <= min)
                return new SimpleStringProperty("منخفض");
            return new SimpleStringProperty("متوفر");
        });

        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "نفد" -> setStyle("-fx-text-fill: -fx-danger-text; -fx-font-weight: bold;");
                        case "منخفض" -> setStyle("-fx-text-fill: -fx-warning-text; -fx-font-weight: bold;");
                        case "غير نشط" -> setStyle("-fx-text-fill: -fx-text-muted;");
                        default -> setStyle("-fx-text-fill: -fx-success-text; -fx-font-weight: bold;");
                    }
                }
            }
        });

        setupActionsColumn();
    }

    private void setupActionsColumn() {
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("تعديل");
            private final Button stockBtn = new Button("+");

            {
                editBtn.setStyle(
                        "-fx-background-color: -fx-accent-light; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4;");
                stockBtn.setStyle(
                        "-fx-background-color: -fx-success-text; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4;");

                editBtn.setOnAction(e -> handleEditProduct(getTableView().getItems().get(getIndex())));
                stockBtn.setOnAction(e -> handleAddStockToProduct(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Product product = getTableView().getItems().get(getIndex());
                    stockBtn.setDisable(Boolean.TRUE.equals(product.getIsUnlimitedStock()));
                    stockBtn.setTooltip(Boolean.TRUE.equals(product.getIsUnlimitedStock())
                            ? new Tooltip("الكمية غير محدودة ولا تحتاج إلى إضافة مخزون") : null);
                    javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(4, editBtn, stockBtn);
                    setGraphic(box);
                }
            }
        });
    }

    private String formatInventoryQuantity(Product product, double quantity) {
        if (product != null && Boolean.TRUE.equals(product.getIsUnlimitedStock())) {
            return "غير محدودة";
        }
        String baseUnit = productUnitService.resolveBaseUnit(product);
        String baseDisplay = numberFormat.format(quantity) + " " + baseUnit;
        if (product == null || product.getId() == null) {
            return baseDisplay;
        }

        ProductUnit largestUnit = productUnitService.getUnitsForProductOrDefault(product).stream()
                .filter(unit -> Boolean.TRUE.equals(unit.getIsActive()))
                .filter(unit -> unit.getUnitName() != null)
                .filter(unit -> unit.getEffectiveConversionFactor() > 1.0)
                .max(Comparator.comparingDouble(ProductUnit::getEffectiveConversionFactor))
                .orElse(null);
        if (largestUnit == null) {
            return baseDisplay;
        }

        double convertedQuantity = quantity / largestUnit.getEffectiveConversionFactor();
        return baseDisplay + " (" + numberFormat.format(convertedQuantity) + " " + largestUnit.getUnitName() + ")";
    }

    private void setupFilters() {
        statusFilter.setItems(FXCollections.observableArrayList("الكل", "متوفر", "منخفض", "نفد", "غير نشط"));
        statusFilter.setValue("الكل");
        statusFilter.setOnAction(e -> applyFilters());

        List<String> categories = new ArrayList<>(categoryService.getActiveCategories().stream()
                .map(Category::getName)
                .distinct()
                .sorted()
                .toList());
        categories.add(0, "الكل");
        categoryFilter.setItems(FXCollections.observableArrayList(categories));
        categoryFilter.setValue("الكل");
        categoryFilter.setOnAction(e -> applyFilters());
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void setupSelectionActions() {
        if (batchDetailsButton != null) {
            batchDetailsButton.setDisable(true);
        }
        if (movementHistoryButton != null) {
            movementHistoryButton.setDisable(true);
        }

        productsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldProduct, product) -> {
            boolean noSelection = product == null;
            if (batchDetailsButton != null) {
                batchDetailsButton.setDisable(noSelection);
            }
            if (movementHistoryButton != null) {
                movementHistoryButton.setDisable(noSelection);
            }
        });
    }

    private void loadProducts() {
        List<Product> products = inventoryService.getAllProducts();
        productsList = FXCollections.observableArrayList(products);
        filteredProducts = new FilteredList<>(productsList, p -> true);
        productsTable.setItems(filteredProducts);
        updateStatistics();
        statusLabel.setText("تم تحميل " + products.size() + " منتج");
    }

    private void applyFilters() {
        String searchText = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        String category = categoryFilter.getValue();
        String status = statusFilter.getValue();

        filteredProducts.setPredicate(product -> {
            boolean matchesSearch = searchText.isEmpty() ||
                    (product.getName() != null && product.getName().toLowerCase().contains(searchText)) ||
                    (product.getProductCode() != null && product.getProductCode().toLowerCase().contains(searchText)) ||
                    (product.getBarcode() != null && product.getBarcode().toLowerCase().contains(searchText));

            boolean matchesCategory = category == null || category.equals("الكل") ||
                    (product.getCategory() != null && product.getCategory().equals(category));

            boolean matchesStatus = status == null || status.equals("الكل") ||
                    matchesProductStatus(product, status);

            return matchesSearch && matchesCategory && matchesStatus;
        });
    }

    private boolean matchesProductStatus(Product p, String status) {
        boolean active = Boolean.TRUE.equals(p.getIsActive());
        double qty = p.getQuantityInStock() == null ? 0 : p.getQuantityInStock();
        double min = p.getMinimumStock() == null ? 0 : p.getMinimumStock();
        return switch (status) {
            case "متوفر" -> active && qty > min;
            case "منخفض" -> active && qty > 0 && qty <= min;
            case "نفد" -> active && qty == 0;
            case "غير نشط" -> !active;
            default -> true;
        };
    }

    private void updateStatistics() {
        List<Product> allProducts = inventoryService.getAllProducts();

        totalProductsLabel.setText(String.valueOf(allProducts.size()));
        totalStockLabel.setText(String.valueOf(inventoryService.getTotalStockCount()));
        if (SessionManager.getInstance().canSeeCost()) {
            inventoryValueLabel
                    .setText(String.format("%s د.ع", numberFormat.format(inventoryService.getTotalInventoryValue())));
        }
        lowStockLabel.setText(String.valueOf(inventoryService.getLowStockProducts().size()));
    }

    @FXML
    private void handleAddProduct() {
        ProductController controller = TabManager.getInstance().openTab(
                "new-product",
                "منتج جديد",
                "add_product.svg",
                "/views/ProductForm.fxml",
                (ProductController productController) -> {
                    productController.setTabMode(true);
                    productController.setTabId("new-product");
                    productController.setAfterSaveCallback(this::handleRefresh);
                });
        if (controller == null && !TabManager.getInstance().isTabOpen("new-product")) {
            showError("خطأ", "فشل في فتح تاب إضافة منتج");
        }
    }

    private void handleEditProduct(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        String tabId = "product-edit-" + product.getId();
        ProductController controller = TabManager.getInstance().openTab(
                tabId,
                "تعديل منتج - " + product.getName(),
                "add_product.svg",
                "/views/ProductForm.fxml",
                (ProductController productController) -> {
                    productController.setTabMode(true);
                    productController.setTabId(tabId);
                    productController.setAfterSaveCallback(this::handleRefresh);
                    productController.setProduct(product);
                });
        if (controller == null && !TabManager.getInstance().isTabOpen(tabId)) {
            showError("خطأ", "فشل في فتح تاب تعديل المنتج");
        }
    }

    private void handleAddStockToProduct(Product product) {
        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("إضافة مخزون");
        dialog.setHeaderText("إضافة مخزون للمنتج: " + product.getName());
        dialog.setContentText("الكمية:");

        dialog.showAndWait().ifPresent(quantity -> {
            try {
                double qty = Double.parseDouble(quantity);
                if (qty > 0) {
                    inventoryService.addStock(product.getId(), qty);
                    loadProducts();
                    showInfo("تم", "تمت إضافة " + qty + " وحدة إلى المخزون");
                }
            } catch (NumberFormatException e) {
                showError("خطأ", "الكمية يجب أن تكون رقماً");
            } catch (Exception e) {
                showError("خطأ", e.getMessage());
            }
        });
    }

    @FXML
    private void handleManageCategories() {
        CategoryController controller = TabManager.getInstance().openTab(
                "categories",
                "إدارة الفئات",
                "categories_management.svg",
                "/views/CategoryManager.fxml",
                (CategoryController categoryController) -> {
                    categoryController.setTabMode(true);
                    categoryController.setAfterChangeCallback(this::setupFilters);
                });
        if (controller == null && !TabManager.getInstance().isTabOpen("categories")) {
            showError("خطأ", "فشل في فتح تاب إدارة الفئات");
        }
    }

    @FXML
    private void handleRefresh() {
        loadProducts();
        setupFilters();
    }

    @FXML
    private void handleBatchDetails() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("خطأ", "الرجاء اختيار منتج أولاً");
            return;
        }
        showBatchDetailsDialog(selected);
    }

    @FXML
    private void handleMovementHistory() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("خطأ", "الرجاء اختيار منتج أولاً");
            return;
        }
        showMovementHistoryDialog(selected);
    }

    @FXML
    private void handlePrint() {
        if (!SessionManager.getInstance().canSeeCost()) {
            showError("غير متاح", "طباعة تقرير المخزون غير متاحة للبائع.");
            return;
        }
        try {
            PrintService printService = new PrintService();

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("حفظ تقرير المخزون");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            fileChooser.setInitialFileName("inventory_report.pdf");

            Stage owner = (Stage) productsTable.getScene().getWindow();
            File selectedFile = fileChooser.showSaveDialog(owner);
            if (selectedFile == null) {
                return;
            }

            File pdfFile = printService.generateInventoryListPdf(productsList, selectedFile);

            if (pdfFile.exists()) {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(pdfFile);
                }
                showInfo("تم", "تم إنشاء تقرير المخزون:\n" + pdfFile.getAbsolutePath());
            }
        } catch (Exception e) {
            logger.error("Failed to print inventory list", e);
            showError("خطأ", "فشل في طباعة قائمة المخزون: " + e.getMessage());
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

    private void showBatchDetailsDialog(Product product) {
        List<ProductBatch> batches = productBatchService.getAllBatches(product.getId());
        List<InventoryMovement> movements = inventoryMovementService.getByProductId(product.getId());
        Map<Long, String> sourceByBatchId = buildSourceReferenceMap(movements);

        double summaryQuantity = product.getQuantityInStock() != null ? product.getQuantityInStock() : 0.0;
        double batchTotal = productBatchService.getTotalBatchQuantity(product.getId());
        boolean mismatch = Math.abs(summaryQuantity - batchTotal) > QTY_EPSILON;
        if (mismatch) {
            logger.warn("Inventory quantity mismatch for product {}: summary={} batchTotal={}",
                    product.getName(), summaryQuantity, batchTotal);
        }

        TableView<BatchRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("لا توجد دفعات مسجلة لهذا المنتج"));

        TableColumn<BatchRow, String> batchNumberCol = new TableColumn<>("رقم التشغيلة");
        batchNumberCol.setCellValueFactory(new PropertyValueFactory<>("batchNumber"));
        TableColumn<BatchRow, String> expiryCol = new TableColumn<>("الصلاحية");
        expiryCol.setCellValueFactory(new PropertyValueFactory<>("expiryDate"));
        TableColumn<BatchRow, String> qtyCol = new TableColumn<>("الكمية");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        TableColumn<BatchRow, String> costCol = new TableColumn<>("التكلفة");
        costCol.setCellValueFactory(new PropertyValueFactory<>("costPrice"));
        TableColumn<BatchRow, String> saleCol = new TableColumn<>("سعر البيع");
        saleCol.setCellValueFactory(new PropertyValueFactory<>("salePrice"));
        TableColumn<BatchRow, String> statusCol = new TableColumn<>("الحالة");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        TableColumn<BatchRow, String> sourceCol = new TableColumn<>("المصدر");
        sourceCol.setCellValueFactory(new PropertyValueFactory<>("sourceReference"));
        TableColumn<BatchRow, String> createdCol = new TableColumn<>("تاريخ الإنشاء");
        createdCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        boolean canSeeCost = SessionManager.getInstance().canSeeCost();
        @SuppressWarnings("unchecked")
        var batchColumns = canSeeCost
                ? new TableColumn[]{batchNumberCol, expiryCol, qtyCol, costCol, saleCol, statusCol, sourceCol, createdCol}
                : new TableColumn[]{batchNumberCol, expiryCol, qtyCol, saleCol, statusCol, sourceCol, createdCol};
        table.getColumns().addAll(batchColumns);

        List<BatchRow> rows = batches.stream()
                .sorted(Comparator
                        .comparing((ProductBatch batch) -> batch.getExpiryDate() == null ? java.time.LocalDate.MAX : batch.getExpiryDate())
                        .thenComparing(batch -> batch.getCreatedAt() != null ? batch.getCreatedAt() : LocalDateTime.MIN))
                .map(batch -> BatchRow.from(batch, product, sourceByBatchId.get(batch.getId()), numberFormat))
                .toList();
        table.setItems(FXCollections.observableArrayList(rows));
        table.setPrefHeight(Math.max(180, Math.min(420, 120 + rows.size() * 28.0)));

        Label title = new Label("المنتج: " + safeText(product.getName()));
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label totalInfo = new Label("الكمية الظاهرة: " + numberFormat.format(summaryQuantity)
                + " | إجمالي الدفعات: " + numberFormat.format(batchTotal));
        Label warning = new Label(mismatch
                ? "تحذير: مجموع كميات الدفعات لا يطابق quantity_in_stock"
                : "لا يوجد تعارض بين ملخص الكمية وكميات الدفعات");
        warning.setStyle(mismatch
                ? "-fx-text-fill: -fx-danger-text; -fx-font-weight: bold;"
                : "-fx-text-fill: -fx-success-text;");

        VBox content = new VBox(10, title, totalInfo, warning, table);
        content.setPadding(new Insets(14));

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("تفاصيل الدفعات");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(980);
        dialog.showAndWait();
    }

    private void showMovementHistoryDialog(Product product) {
        List<InventoryMovement> movements = new ArrayList<>(inventoryMovementService.getByProductId(product.getId()));
        movements.sort(Comparator
                .comparing((InventoryMovement movement) -> movement.getCreatedAt() != null ? movement.getCreatedAt() : LocalDateTime.MIN)
                .reversed()
                .thenComparing((InventoryMovement movement) -> movement.getId() != null ? movement.getId() : 0L, Comparator.reverseOrder()));

        TableView<MovementRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("لا توجد حركات مخزون لهذا المنتج"));

        TableColumn<MovementRow, String> dateCol = new TableColumn<>("التاريخ");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        TableColumn<MovementRow, String> typeCol = new TableColumn<>("الحركة");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("movementType"));
        TableColumn<MovementRow, String> qtyCol = new TableColumn<>("التغير");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantityChanged"));
        TableColumn<MovementRow, String> beforeCol = new TableColumn<>("قبل");
        beforeCol.setCellValueFactory(new PropertyValueFactory<>("quantityBefore"));
        TableColumn<MovementRow, String> afterCol = new TableColumn<>("بعد");
        afterCol.setCellValueFactory(new PropertyValueFactory<>("quantityAfter"));
        TableColumn<MovementRow, String> batchCol = new TableColumn<>("التشغيلة");
        batchCol.setCellValueFactory(new PropertyValueFactory<>("batchNumber"));
        TableColumn<MovementRow, String> refTypeCol = new TableColumn<>("نوع المرجع");
        refTypeCol.setCellValueFactory(new PropertyValueFactory<>("referenceType"));
        TableColumn<MovementRow, String> refIdCol = new TableColumn<>("رقم المرجع");
        refIdCol.setCellValueFactory(new PropertyValueFactory<>("referenceId"));
        TableColumn<MovementRow, String> noteCol = new TableColumn<>("ملاحظات");
        noteCol.setCellValueFactory(new PropertyValueFactory<>("note"));

        @SuppressWarnings("unchecked")
        var movementColumns = new TableColumn[]{dateCol, typeCol, qtyCol, beforeCol, afterCol, batchCol, refTypeCol, refIdCol, noteCol};
        table.getColumns().addAll(movementColumns);
        table.setItems(FXCollections.observableArrayList(
                movements.stream().map(movement -> MovementRow.from(movement, numberFormat)).toList()));
        table.setPrefHeight(Math.max(180, Math.min(460, 120 + movements.size() * 28.0)));

        Label title = new Label("حركة المخزون للمنتج: " + safeText(product.getName()));
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        VBox content = new VBox(10, title, table);
        content.setPadding(new Insets(14));

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("حركة المخزون");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(1080);
        dialog.showAndWait();
    }

    private Map<Long, String> buildSourceReferenceMap(List<InventoryMovement> movements) {
        Map<Long, String> sourceByBatchId = new HashMap<>();
        for (InventoryMovement movement : movements) {
            if (movement.getBatch() == null || movement.getBatch().getId() == null) {
                continue;
            }
            sourceByBatchId.computeIfAbsent(movement.getBatch().getId(), batchId -> {
                if ("voucher_purchase".equals(movement.getReferenceType()) && movement.getReferenceId() != null) {
                    return "سند شراء #" + movement.getReferenceId();
                }
                if (movement.getBatch().getIsOpeningBatch() != null && movement.getBatch().getIsOpeningBatch()) {
                    return "رصيد افتتاحي";
                }
                if (movement.getReferenceType() != null && movement.getReferenceId() != null) {
                    return movement.getReferenceType() + " #" + movement.getReferenceId();
                }
                return "-";
            });
        }
        return sourceByBatchId;
    }

    private String safeText(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }

    public static class BatchRow {
        private final String batchNumber;
        private final String expiryDate;
        private final String quantity;
        private final String costPrice;
        private final String salePrice;
        private final String status;
        private final String sourceReference;
        private final String createdAt;

        private BatchRow(String batchNumber,
                         String expiryDate,
                         String quantity,
                         String costPrice,
                         String salePrice,
                         String status,
                         String sourceReference,
                         String createdAt) {
            this.batchNumber = batchNumber;
            this.expiryDate = expiryDate;
            this.quantity = quantity;
            this.costPrice = costPrice;
            this.salePrice = salePrice;
            this.status = status;
            this.sourceReference = sourceReference;
            this.createdAt = createdAt;
        }

        public static BatchRow from(ProductBatch batch, Product product, String sourceReference, DecimalFormat format) {
            return new BatchRow(
                    batch.getBatchNumber() != null ? batch.getBatchNumber() : "-",
                    batch.getExpiryDate() != null ? batch.getExpiryDate().toString() : "-",
                    format.format(batch.getQuantity() != null ? batch.getQuantity() : 0.0),
                    batch.getUnitCost() != null ? format.format(batch.getUnitCost()) : "-",
                    product.getUnitPrice() != null ? format.format(product.getUnitPrice()) : "-",
                    batch.getStatus() != null ? batch.getStatus() : "-",
                    sourceReference != null ? sourceReference : "-",
                    batch.getCreatedAt() != null ? batch.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "-");
        }

        public String getBatchNumber() { return batchNumber; }
        public String getExpiryDate() { return expiryDate; }
        public String getQuantity() { return quantity; }
        public String getCostPrice() { return costPrice; }
        public String getSalePrice() { return salePrice; }
        public String getStatus() { return status; }
        public String getSourceReference() { return sourceReference; }
        public String getCreatedAt() { return createdAt; }
    }

    public static class MovementRow {
        private final String createdAt;
        private final String movementType;
        private final String quantityChanged;
        private final String quantityBefore;
        private final String quantityAfter;
        private final String batchNumber;
        private final String referenceType;
        private final String referenceId;
        private final String note;

        private MovementRow(String createdAt,
                            String movementType,
                            String quantityChanged,
                            String quantityBefore,
                            String quantityAfter,
                            String batchNumber,
                            String referenceType,
                            String referenceId,
                            String note) {
            this.createdAt = createdAt;
            this.movementType = movementType;
            this.quantityChanged = quantityChanged;
            this.quantityBefore = quantityBefore;
            this.quantityAfter = quantityAfter;
            this.batchNumber = batchNumber;
            this.referenceType = referenceType;
            this.referenceId = referenceId;
            this.note = note;
        }

        public static MovementRow from(InventoryMovement movement, DecimalFormat format) {
            return new MovementRow(
                    movement.getCreatedAt() != null ? movement.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "-",
                    movement.getMovementType() != null ? movement.getMovementType() : "-",
                    format.format(movement.getQuantityDelta() != null ? movement.getQuantityDelta() : 0.0),
                    movement.getQuantityBefore() != null ? format.format(movement.getQuantityBefore()) : "-",
                    movement.getQuantityAfter() != null ? format.format(movement.getQuantityAfter()) : "-",
                    movement.getBatch() != null && movement.getBatch().getBatchNumber() != null ? movement.getBatch().getBatchNumber() : "-",
                    movement.getReferenceType() != null ? movement.getReferenceType() : "-",
                    movement.getReferenceId() != null ? String.valueOf(movement.getReferenceId()) : "-",
                    movement.getNote() != null ? movement.getNote() : "-");
        }

        public String getCreatedAt() { return createdAt; }
        public String getMovementType() { return movementType; }
        public String getQuantityChanged() { return quantityChanged; }
        public String getQuantityBefore() { return quantityBefore; }
        public String getQuantityAfter() { return quantityAfter; }
        public String getBatchNumber() { return batchNumber; }
        public String getReferenceType() { return referenceType; }
        public String getReferenceId() { return referenceId; }
        public String getNote() { return note; }
    }
}
