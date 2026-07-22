package com.pharmax.controller;

import com.pharmax.database.Repository.ProductRepository;
import com.pharmax.model.Product;
import com.pharmax.model.ProductUnit;
import com.pharmax.model.QuickSaleGroup;
import com.pharmax.model.QuickSaleItem;
import com.pharmax.service.ProductUnitService;
import com.pharmax.service.QuickSaleService;
import com.pharmax.util.FxUtil;
import com.pharmax.util.MedicalEmojiIcons;
import com.pharmax.util.SessionManager;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Comparator;
import java.util.List;

public class QuickSaleManagerController {
    @FXML private ListView<QuickSaleGroup> groupsList;
    @FXML private TableView<QuickSaleItem> itemsTable;
    @FXML private TableColumn<QuickSaleItem, String> productColumn;
    @FXML private TableColumn<QuickSaleItem, String> unitColumn;
    @FXML private TableColumn<QuickSaleItem, String> imageColumn;
    @FXML private TableColumn<QuickSaleItem, Number> orderColumn;
    @FXML private ComboBox<Product> productCombo;
    @FXML private ComboBox<ProductUnit> unitCombo;
    @FXML private ComboBox<String> accentCombo;
    @FXML private ImageView imagePreview;
    @FXML private Button saveItemButton;
    @FXML private Label itemsTitle;
    @FXML private Label statusLabel;

    private final QuickSaleService quickSaleService = new QuickSaleService();
    private final ProductRepository productRepository = new ProductRepository();
    private final ProductUnitService productUnitService = new ProductUnitService();
    private QuickSaleItem editingItem;
    private byte[] selectedImageData;
    private Runnable onChanged;

    @FXML
    private void initialize() {
        if (!SessionManager.getInstance().isAdmin()) {
            throw new SecurityException("إدارة المنتجات السريعة متاحة للأدمن فقط");
        }
        productColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProduct().getName()));
        unitColumn.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getProductUnit() == null ? "الافتراضية" : d.getValue().getProductUnit().getUnitName()));
        imageColumn.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getImageData() == null ? "—" : "✓"));
        orderColumn.setCellValueFactory(d -> new SimpleIntegerProperty(
                d.getValue().getSortOrder() == null ? 0 : d.getValue().getSortOrder()));

        productCombo.setConverter(productConverter());
        unitCombo.setConverter(unitConverter());
        groupsList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(QuickSaleGroup group, boolean empty) {
                super.updateItem(group, empty);
                if (empty || group == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                ImageView coloredIcon = MedicalEmojiIcons.createView(group.getIconKey(), 24);
                setGraphic(coloredIcon);
                String label = coloredIcon == null ? group.toString() : group.getName();
                setText(label + (Boolean.TRUE.equals(group.getIsActive()) ? "" : "  (متوقفة)"));
            }
        });
        accentCombo.setItems(FXCollections.observableArrayList("افتراضي", "أزرق", "أخضر", "برتقالي", "وردي", "بنفسجي"));
        accentCombo.setValue("افتراضي");
        productCombo.valueProperty().addListener((obs, old, product) -> loadUnits(product));
        groupsList.getSelectionModel().selectedItemProperty().addListener((obs, old, group) -> loadItems(group));
        itemsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> editItem(item));
        reloadAll();
    }

    public void setOnChanged(Runnable onChanged) { this.onChanged = onChanged; }

    private void reloadAll() {
        Long selectedId = groupsList.getSelectionModel().getSelectedItem() == null ? null
                : groupsList.getSelectionModel().getSelectedItem().getId();
        List<QuickSaleGroup> groups = quickSaleService.getGroups(false);
        groupsList.setItems(FXCollections.observableArrayList(groups));
        productCombo.setItems(FXCollections.observableArrayList(productRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .sorted(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER)).toList()));
        QuickSaleGroup selection = groups.stream().filter(g -> g.getId().equals(selectedId)).findFirst()
                .orElse(groups.isEmpty() ? null : groups.get(0));
        groupsList.getSelectionModel().select(selection);
        loadItems(selection);
    }

    private void loadItems(QuickSaleGroup group) {
        clearItemForm();
        itemsTitle.setText(group == null ? "منتجات المجموعة" : "منتجات: " + group.getName());
        itemsTable.setItems(FXCollections.observableArrayList(
                group == null ? List.of() : quickSaleService.getItems(group.getId())));
    }

    private void loadUnits(Product product) {
        unitCombo.setItems(FXCollections.observableArrayList(
                product == null ? List.of() : productUnitService.getUnitsForProductOrDefault(product)));
        unitCombo.setValue(null);
    }

    @FXML private void handleAddGroup() { showGroupDialog(null); }
    @FXML private void handleEditGroup() { showGroupDialog(groupsList.getSelectionModel().getSelectedItem()); }

    private void showGroupDialog(QuickSaleGroup existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "إضافة مجموعة" : "تعديل المجموعة");
        TextField name = new TextField(existing == null ? "" : existing.getName());
        var iconChoices = FXCollections.observableArrayList("");
        iconChoices.addAll(MedicalEmojiIcons.supportedIcons());
        ComboBox<String> icon = new ComboBox<>(iconChoices);
        icon.setEditable(false);
        icon.setMaxWidth(Double.MAX_VALUE);
        icon.setVisibleRowCount(10);
        icon.setPromptText("اختر أيقونة");
        icon.getStyleClass().add("quick-sale-emoji-picker");
        String currentIcon = existing == null ? "" : existing.getIconKey();
        TextField customIcon = new TextField();
        customIcon.setPromptText("أو اكتب رمزًا مخصصًا");
        if (MedicalEmojiIcons.supports(currentIcon)) icon.setValue(currentIcon);
        else if (currentIcon != null && !currentIcon.isBlank()) customIcon.setText(currentIcon);
        else icon.setValue("");
        icon.setCellFactory(list -> createMedicalEmojiCell());
        icon.setButtonCell(createMedicalEmojiCell());
        icon.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.isBlank()) customIcon.clear();
        });
        Label iconHint = new Label("اختر صورة ملونة جاهزة، أو اكتب رمزًا آخر في الحقل المخصص");
        iconHint.setWrapText(true);
        iconHint.getStyleClass().addAll("quick-sale-muted", "quick-sale-icon-hint");
        VBox iconBox = new VBox(6, icon, customIcon, iconHint);
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.addRow(0, new Label("اسم المجموعة"), name);
        grid.addRow(1, new Label("الرمز الاختياري"), iconBox);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {
            if (name.getText() == null || name.getText().trim().isEmpty()) {
                showError("اسم المجموعة مطلوب"); return;
            }
            QuickSaleGroup group = existing == null ? new QuickSaleGroup() : existing;
            group.setName(name.getText().trim());
            String customValue = customIcon.getText();
            String selectedIcon = customValue != null && !customValue.isBlank() ? customValue : icon.getValue();
            group.setIconKey(selectedIcon == null || selectedIcon.isBlank() ? null : selectedIcon.trim());
            if (existing == null) group.setSortOrder(groupsList.getItems().size());
            quickSaleService.saveGroup(group);
            changed(); reloadAll(); groupsList.getSelectionModel().select(group);
        });
    }

    private ListCell<String> createMedicalEmojiCell() {
        return new ListCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setGraphic(MedicalEmojiIcons.createView(value, 30));
                setText(value.isBlank() ? "بدون رمز" : null);
                if (!getStyleClass().contains("quick-sale-emoji-cell")) {
                    getStyleClass().add("quick-sale-emoji-cell");
                }
            }
        };
    }

    @FXML private void handleDeleteGroup() {
        QuickSaleGroup group = groupsList.getSelectionModel().getSelectedItem();
        if (group == null) return;
        if (groupsList.getItems().size() <= 1) { showError("يجب إبقاء مجموعة واحدة على الأقل"); return; }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "حذف المجموعة «" + group.getName() + "» وعناصرها؟", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().filter(ButtonType.YES::equals).ifPresent(button -> {
            quickSaleService.deleteGroup(group.getId()); changed(); reloadAll();
        });
    }

    @FXML private void handleToggleGroup() {
        QuickSaleGroup group = groupsList.getSelectionModel().getSelectedItem();
        if (group == null) return;
        group.setIsActive(!Boolean.TRUE.equals(group.getIsActive()));
        quickSaleService.saveGroup(group); changed(); reloadAll();
    }

    @FXML private void handleGroupUp() { moveGroup(-1); }
    @FXML private void handleGroupDown() { moveGroup(1); }
    private void moveGroup(int delta) {
        int index = groupsList.getSelectionModel().getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= groupsList.getItems().size()) return;
        QuickSaleGroup a = groupsList.getItems().get(index), b = groupsList.getItems().get(target);
        int aOrder = a.getSortOrder() == null ? index : a.getSortOrder();
        a.setSortOrder(b.getSortOrder() == null ? target : b.getSortOrder()); b.setSortOrder(aOrder);
        quickSaleService.saveGroup(a); quickSaleService.saveGroup(b); changed(); reloadAll();
        groupsList.getSelectionModel().select(a);
    }

    @FXML private void handleSaveItem() {
        QuickSaleGroup group = groupsList.getSelectionModel().getSelectedItem();
        Product product = productCombo.getValue();
        if (group == null || product == null) { showError("اختر المجموعة والمنتج أولًا"); return; }
        boolean changedProduct = editingItem == null || editingItem.getProduct() == null
                || !product.getId().equals(editingItem.getProduct().getId());
        if (changedProduct && quickSaleService.containsProduct(group.getId(), product.getId())) {
            showError("المنتج موجود بالفعل داخل هذه المجموعة"); return;
        }
        QuickSaleItem item = editingItem == null ? new QuickSaleItem() : editingItem;
        item.setGroup(group); item.setProduct(product); item.setProductUnit(unitCombo.getValue());
        item.setImageData(selectedImageData);
        item.setImageMimeType(selectedImageData == null ? null : "image/png");
        item.setAccentColor(accentKey(accentCombo.getValue()));
        if (editingItem == null) item.setSortOrder(itemsTable.getItems().size());
        quickSaleService.saveItem(item);
        statusLabel.setText("تم حفظ المنتج في المجموعة");
        changed(); loadItems(group);
    }

    @FXML private void handleDeleteItem() {
        QuickSaleItem item = itemsTable.getSelectionModel().getSelectedItem();
        if (item == null) return;
        quickSaleService.deleteItem(item.getId()); changed(); loadItems(groupsList.getSelectionModel().getSelectedItem());
    }

    @FXML private void handleItemUp() { moveItem(-1); }
    @FXML private void handleItemDown() { moveItem(1); }
    private void moveItem(int delta) {
        int index = itemsTable.getSelectionModel().getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= itemsTable.getItems().size()) return;
        QuickSaleItem a = itemsTable.getItems().get(index), b = itemsTable.getItems().get(target);
        int aOrder = a.getSortOrder() == null ? index : a.getSortOrder();
        a.setSortOrder(b.getSortOrder() == null ? target : b.getSortOrder()); b.setSortOrder(aOrder);
        quickSaleService.saveItem(a); quickSaleService.saveItem(b); changed(); loadItems(groupsList.getSelectionModel().getSelectedItem());
        itemsTable.getSelectionModel().select(a);
    }

    private void editItem(QuickSaleItem item) {
        if (item == null) return;
        editingItem = item; productCombo.setValue(item.getProduct()); loadUnits(item.getProduct());
        unitCombo.setValue(item.getProductUnit()); selectedImageData = item.getImageData();
        accentCombo.setValue(accentLabel(item.getAccentColor())); updateImagePreview();
        saveItemButton.setText("حفظ التعديلات");
    }

    @FXML private void handleChooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("اختيار صورة المنتج السريع");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("الصور", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(groupsList.getScene().getWindow());
        if (file == null) return;
        try { selectedImageData = resizeToPng(file, 512); updateImagePreview(); }
        catch (Exception e) { showError("تعذر قراءة الصورة: " + e.getMessage()); }
    }

    @FXML private void handleRemoveImage() { selectedImageData = null; updateImagePreview(); }
    @FXML private void handleClearItemForm() { clearItemForm(); itemsTable.getSelectionModel().clearSelection(); }
    private void clearItemForm() {
        editingItem = null; selectedImageData = null; productCombo.setValue(null); unitCombo.getItems().clear();
        accentCombo.setValue("افتراضي"); updateImagePreview(); saveItemButton.setText("إضافة إلى المجموعة");
    }

    private void updateImagePreview() {
        imagePreview.setImage(selectedImageData == null ? null : new Image(new ByteArrayInputStream(selectedImageData)));
    }

    private byte[] resizeToPng(File file, int maxSize) throws Exception {
        if (file.length() > 8L * 1024 * 1024) throw new IllegalArgumentException("حجم الملف أكبر من 8 MB");
        BufferedImage source = ImageIO.read(file);
        if (source == null) throw new IllegalArgumentException("صيغة الصورة غير مدعومة");
        double scale = Math.min(1.0, Math.min((double) maxSize / source.getWidth(), (double) maxSize / source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, width, height, null); g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(target, "png", out); return out.toByteArray();
    }

    private String accentKey(String label) {
        return switch (label == null ? "" : label) {
            case "أزرق" -> "blue"; case "أخضر" -> "green"; case "برتقالي" -> "orange";
            case "وردي" -> "pink"; case "بنفسجي" -> "purple"; default -> null;
        };
    }
    private String accentLabel(String key) {
        return switch (key == null ? "" : key) {
            case "blue" -> "أزرق"; case "green" -> "أخضر"; case "orange" -> "برتقالي";
            case "pink" -> "وردي"; case "purple" -> "بنفسجي"; default -> "افتراضي";
        };
    }

    private javafx.util.StringConverter<Product> productConverter() {
        return new javafx.util.StringConverter<>() {
            public String toString(Product p) { return p == null ? "" : p.getName(); }
            public Product fromString(String s) { return null; }
        };
    }
    private javafx.util.StringConverter<ProductUnit> unitConverter() {
        return new javafx.util.StringConverter<>() {
            public String toString(ProductUnit u) { return u == null ? "الوحدة الافتراضية" : u.getUnitName(); }
            public ProductUnit fromString(String s) { return null; }
        };
    }

    private void changed() { if (onChanged != null) onChanged.run(); }
    private void showError(String message) { new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait(); }
    @FXML private void handleClose() { FxUtil.closeWindow(groupsList); }
}
