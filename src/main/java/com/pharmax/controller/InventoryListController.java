package com.pharmax.controller;

import com.pharmax.MainApp;
import com.pharmax.model.Category;
import com.pharmax.model.Product;
import com.pharmax.service.CategoryService;
import com.pharmax.service.InventoryService;
import com.pharmax.service.PrintService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private final InventoryService inventoryService = new InventoryService();
    private final CategoryService categoryService = new CategoryService();
    private ObservableList<Product> productsList;
    private FilteredList<Product> filteredProducts;
    private final DecimalFormat numberFormat;

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
        loadProducts();
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

        statusColumn.setCellValueFactory(cellData -> {
            Product p = cellData.getValue();
            boolean active = Boolean.TRUE.equals(p.getIsActive());
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
                    javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(4, editBtn, stockBtn);
                    setGraphic(box);
                }
            }
        });
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
        inventoryValueLabel
                .setText(String.format("%s د.ع", numberFormat.format(inventoryService.getTotalInventoryValue())));
        lowStockLabel.setText(String.valueOf(inventoryService.getLowStockProducts().size()));
    }

    @FXML
    private void handleAddProduct() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/ProductForm.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("منتج جديد");
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);
            stage.setMaximized(true);

            ProductController controller = loader.getController();
            controller.setDialogStage(stage);

            stage.showAndWait();
            loadProducts();
        } catch (IOException e) {
            showError("خطأ", "فشل في فتح نافذة إضافة منتج");
        }
    }

    private void handleEditProduct(Product product) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/ProductForm.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("تعديل منتج");
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);
            stage.setMaximized(true);

            ProductController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setProduct(product);

            stage.showAndWait();
            loadProducts();
        } catch (IOException e) {
            showError("خطأ", "فشل في فتح نافذة تعديل المنتج");
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
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/CategoryManager.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("إدارة الفئات");
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);
            stage.setMaximized(true);

            CategoryController controller = loader.getController();
            controller.setDialogStage(stage);

            stage.showAndWait();
            setupFilters();
        } catch (IOException e) {
            showError("خطأ", "فشل في فتح نافذة إدارة الفئات");
        }
    }

    @FXML
    private void handleRefresh() {
        loadProducts();
        setupFilters();
    }

    @FXML
    private void handlePrint() {
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
}
