package com.pharmax.controller;

import com.pharmax.model.CashboxLedger;
import com.pharmax.model.DailyClosing;
import com.pharmax.service.CashboxService;
import com.pharmax.util.SessionManager;
import com.pharmax.util.TabManager;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CashboxController {
    @FXML private DatePicker ledgerDatePicker;
    @FXML private TableView<CashboxLedger> ledgerTable;
    @FXML private TableColumn<CashboxLedger, String> dateColumn;
    @FXML private TableColumn<CashboxLedger, String> entryTypeColumn;
    @FXML private TableColumn<CashboxLedger, String> directionColumn;
    @FXML private TableColumn<CashboxLedger, Double> amountColumn;
    @FXML private TableColumn<CashboxLedger, String> currencyColumn;
    @FXML private TableColumn<CashboxLedger, String> sourceTypeColumn;
    @FXML private TableColumn<CashboxLedger, String> sourceIdColumn;
    @FXML private TableColumn<CashboxLedger, String> descriptionColumn;
    @FXML private Label totalInLabel;
    @FXML private Label totalOutLabel;
    @FXML private Label openingCashLabel;
    @FXML private Label expectedCashLabel;
    @FXML private TextField actualCashField;
    @FXML private TextArea closingNotesArea;
    @FXML private Label closingStatusLabel;

    private final CashboxService cashboxService = new CashboxService();
    private final DecimalFormat numberFormat = new DecimalFormat("#,##0.##");
    private boolean tabMode = false;
    private String tabId;

    public void setTabMode(boolean tabMode) { this.tabMode = tabMode; }
    public void setTabId(String tabId) { this.tabId = tabId; }

    @FXML
    private void initialize() {
        setupTable();
        ledgerDatePicker.setValue(LocalDate.now());
        refreshView();
    }

    private void setupTable() {
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getTransactionDate() != null
                        ? data.getValue().getTransactionDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : "-"
        ));
        entryTypeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEntryType()));
        directionColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDirection()));
        amountColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getAmount()));
        currencyColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCurrency()));
        sourceTypeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSourceType()));
        sourceIdColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSourceId() != null ? String.valueOf(data.getValue().getSourceId()) : "-"
        ));
        descriptionColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescription()));
    }

    @FXML
    private void handleRefresh() {
        refreshView();
    }

    private void refreshView() {
        LocalDate selectedDate = ledgerDatePicker.getValue() != null ? ledgerDatePicker.getValue() : LocalDate.now();
        List<CashboxLedger> entries = cashboxService.getLedgerForDate(selectedDate);
        ledgerTable.setItems(FXCollections.observableArrayList(entries));

        CashboxService.CashTotals totals = cashboxService.calculateTotals(selectedDate);
        openingCashLabel.setText(format(totals.openingCash()));
        totalInLabel.setText(format(totals.totalIn()));
        totalOutLabel.setText(format(totals.totalOut()));
        expectedCashLabel.setText(format(totals.expectedCash()));

        cashboxService.getClosingByDate(selectedDate).ifPresentOrElse(
                closing -> {
                    actualCashField.setText(String.valueOf(closing.getActualCash()));
                    if (closingNotesArea != null) {
                        closingNotesArea.setText(closing.getNotes() != null ? closing.getNotes() : "");
                    }
                    closingStatusLabel.setText("تم إقفال اليوم بواسطة " + (closing.getClosedBy() != null ? closing.getClosedBy() : "-"));
                },
                () -> {
                    actualCashField.setText("");
                    if (closingNotesArea != null) {
                        closingNotesArea.clear();
                    }
                    closingStatusLabel.setText("لم يتم إقفال هذا اليوم بعد");
                }
        );
    }

    @FXML
    private void handleCloseDay() {
        try {
            LocalDate selectedDate = ledgerDatePicker.getValue() != null ? ledgerDatePicker.getValue() : LocalDate.now();
            Double actualCash = parseOptionalAmount(actualCashField.getText());
            DailyClosing closing = cashboxService.closeDay(
                    selectedDate,
                    actualCash,
                    closingNotesArea != null ? closingNotesArea.getText() : null,
                    SessionManager.getInstance().getCurrentDisplayName()
            );
            showInfo("تم", "تم إقفال يوم " + closing.getClosingDate() + " بنجاح");
            refreshView();
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    @FXML
    private void handleClose() {
        if (tabMode && tabId != null && !tabId.isBlank()) {
            TabManager.getInstance().closeTab(tabId);
            return;
        }
        Stage stage = (Stage) ledgerTable.getScene().getWindow();
        stage.close();
    }

    private Double parseOptionalAmount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("يرجى إدخال قيمة نقدية صحيحة");
        }
    }

    private String format(double amount) {
        return numberFormat.format(amount) + " د.ع";
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
