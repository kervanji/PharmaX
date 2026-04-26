package com.pharmax.controller;

import com.pharmax.model.Category;
import com.pharmax.service.CategoryService;
import com.pharmax.service.InventoryService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.util.List;

public class CategoryController {
    @FXML private TextField categoryNameField;
    @FXML private TextField categoryDescField;
    @FXML private Button addButton;
    @FXML private TableView<Category> categoriesTable;
    @FXML private TableColumn<Category, Long> idColumn;
    @FXML private TableColumn<Category, String> nameColumn;
    @FXML private TableColumn<Category, String> descriptionColumn;
    @FXML private TableColumn<Category, Integer> productCountColumn;
    @FXML private TableColumn<Category, String> statusColumn;
    @FXML private TableColumn<Category, Void> actionsColumn;
    @FXML private Label totalCategoriesLabel;
    
    private Stage dialogStage;
    private final CategoryService categoryService = new CategoryService();
    private final InventoryService inventoryService = new InventoryService();
    private Category editingCategory = null;
    private boolean tabMode = false;
    
    @FXML
    private void initialize() {
        setupTableColumns();
        loadCategories();
    }
    
    private void setupTableColumns() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        
        productCountColumn.setCellValueFactory(cellData -> {
            Category cat = cellData.getValue();
            long count = inventoryService.getProductsByCategory(cat.getName()).size();
            return new SimpleIntegerProperty((int) count).asObject();
        });
        
        statusColumn.setCellValueFactory(cellData -> {
            Category cat = cellData.getValue();
            return new SimpleStringProperty(cat.getIsActive() ? "نشطة" : "غير نشطة");
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
                    if (item.equals("نشطة")) {
                        setStyle("-fx-text-fill: -fx-success-text; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: -fx-text-muted;");
                    }
                }
            }
        });
        
        setupActionsColumn();
    }
    
    private void setupActionsColumn() {
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("تعديل");
            private final Button deleteBtn = new Button("حذف");
            
            {
                editBtn.getStyleClass().add("action-btn-edit");
                deleteBtn.getStyleClass().add("action-btn-delete");
                
                editBtn.setOnAction(e -> handleEditCategory(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDeleteCategory(getTableView().getItems().get(getIndex())));
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(4, editBtn, deleteBtn);
                    setGraphic(box);
                }
            }
        });
    }
    
    private void loadCategories() {
        List<Category> categories = categoryService.getAllCategories();
        ObservableList<Category> categoriesList = FXCollections.observableArrayList(categories);
        categoriesTable.setItems(categoriesList);
        totalCategoriesLabel.setText("إجمالي الفئات: " + categories.size());
    }
    
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }
    
    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }
    
    @FXML
    private void handleAddCategory() {
        String name = categoryNameField.getText().trim();
        String desc = categoryDescField.getText().trim();
        
        if (name.isEmpty()) {
            showError("خطأ", "اسم الفئة مطلوب");
            categoryNameField.requestFocus();
            return;
        }
        
        try {
            if (editingCategory != null) {
                editingCategory.setName(name);
                editingCategory.setDescription(desc);
                categoryService.updateCategory(editingCategory);
                showInfo("تم", "تم تحديث الفئة بنجاح");
                editingCategory = null;
                addButton.setText("إضافة");
            } else {
                Category category = new Category(name, desc);
                categoryService.createCategory(category);
                showInfo("تم", "تم إضافة الفئة بنجاح");
            }
            
            categoryNameField.clear();
            categoryDescField.clear();
            loadCategories();
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }
    
    private void handleEditCategory(Category category) {
        editingCategory = category;
        categoryNameField.setText(category.getName());
        categoryDescField.setText(category.getDescription());
        addButton.setText("تحديث");
        categoryNameField.requestFocus();
    }
    
    private void handleDeleteCategory(Category category) {
        long productCount = inventoryService.getProductsByCategory(category.getName()).size();
        
        String message = productCount > 0 
            ? "هذه الفئة تحتوي على " + productCount + " منتج. هل أنت متأكد من الحذف؟"
            : "هل أنت متأكد من حذف هذه الفئة؟";
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الحذف");
        confirm.setHeaderText(null);
        confirm.setContentText(message);
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    categoryService.deleteCategory(category);
                    showInfo("تم", "تم حذف الفئة بنجاح");
                    loadCategories();
                } catch (Exception e) {
                    showError("خطأ", "فشل في حذف الفئة: " + e.getMessage());
                }
            }
        });
    }
    
    @FXML
    private void handleClose() {
        if (tabMode) {
            com.pharmax.util.TabManager.getInstance().closeTab("categories");
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
    
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
