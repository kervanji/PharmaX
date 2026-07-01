package com.pharmax.controller;

import com.pharmax.model.CashboxLedger;
import com.pharmax.model.CashboxManualOpening;
import com.pharmax.model.Customer;
import com.pharmax.model.DailyClosing;
import com.pharmax.service.CashboxService;
import com.pharmax.service.PharmacyReportExportService;
import com.pharmax.util.SessionManager;
import com.pharmax.util.TabManager;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CashboxController {
    @FXML private DatePicker ledgerDatePicker;
    @FXML private TableView<CashboxLedger> ledgerTable;
    @FXML private TableColumn<CashboxLedger, String> dateColumn;
    @FXML private TableColumn<CashboxLedger, String> entryTypeColumn;
    @FXML private TableColumn<CashboxLedger, String> directionColumn;
    @FXML private TableColumn<CashboxLedger, Double> amountColumn;
    @FXML private TableColumn<CashboxLedger, String> currencyColumn;
    @FXML private TableColumn<CashboxLedger, String> partyColumn;
    @FXML private TableColumn<CashboxLedger, String> accountColumn;
    @FXML private TableColumn<CashboxLedger, String> performerColumn;
    @FXML private TableColumn<CashboxLedger, String> creditGiverColumn;
    @FXML private TableColumn<CashboxLedger, String> sourceTypeColumn;
    @FXML private TableColumn<CashboxLedger, String> sourceIdColumn;
    @FXML private TableColumn<CashboxLedger, String> descriptionColumn;
    @FXML private Label totalInLabel;
    @FXML private Label totalOutLabel;
    @FXML private TextField openingCashField;
    @FXML private Button saveOpeningCashBtn;
    @FXML private Label openingCashHintLabel;
    @FXML private Label expectedCashLabel;
    @FXML private TextField actualCashField;
    @FXML private TextArea closingNotesArea;
    @FXML private Label closingStatusLabel;

    private final CashboxService cashboxService = new CashboxService();
    private final PharmacyReportExportService exportService = new PharmacyReportExportService();
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
        entryTypeColumn.setCellValueFactory(data -> new SimpleStringProperty(
                entryTypeLabel(data.getValue().getEntryType())));
        directionColumn.setCellValueFactory(data -> new SimpleStringProperty(
                directionLabel(data.getValue().getDirection())));
        amountColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getAmount()));
        currencyColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCurrency()));
        partyColumn.setCellValueFactory(data -> new SimpleStringProperty(resolvePartyName(data.getValue())));
        accountColumn.setCellValueFactory(data -> new SimpleStringProperty(resolveAccountName(data.getValue())));
        performerColumn.setCellValueFactory(data -> new SimpleStringProperty(
                safeText(data.getValue().getCreatedBy())));
        creditGiverColumn.setCellValueFactory(data -> new SimpleStringProperty(
                safeText(data.getValue().getRelatedCreatedBy())));
        sourceTypeColumn.setCellValueFactory(data -> new SimpleStringProperty(
                sourceTypeLabel(data.getValue().getSourceType())));
        sourceIdColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSourceId() != null ? String.valueOf(data.getValue().getSourceId()) : "-"
        ));
        descriptionColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescription()));
    }

    @FXML
    private void handleRefresh() {
        refreshView();
    }

    @FXML
    private void handleSaveOpeningCash() {
        try {
            LocalDate selectedDate = ledgerDatePicker.getValue() != null ? ledgerDatePicker.getValue() : LocalDate.now();
            Double openingCash = parseOptionalAmount(openingCashField.getText());
            if (openingCash == null) {
                throw new IllegalArgumentException("يرجى إدخال رصيد الافتتاح");
            }
            CashboxManualOpening opening = cashboxService.setManualOpeningCash(
                    selectedDate,
                    openingCash,
                    SessionManager.getInstance().getCurrentDisplayName()
            );
            showInfo("تم", "تم حفظ رصيد الافتتاح: " + format(opening.getOpeningCash()));
            refreshView();
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    private void refreshView() {
        LocalDate selectedDate = ledgerDatePicker.getValue() != null ? ledgerDatePicker.getValue() : LocalDate.now();
        List<CashboxLedger> entries = cashboxService.getLedgerForDate(selectedDate);
        ledgerTable.setItems(FXCollections.observableArrayList(entries));

        CashboxService.CashTotals totals = cashboxService.calculateTotals(selectedDate);
        openingCashField.setText(numberFormat.format(totals.openingCash()));
        totalInLabel.setText(format(totals.totalIn()));
        totalOutLabel.setText(format(totals.totalOut()));
        expectedCashLabel.setText(format(totals.expectedCash()));

        boolean dayClosed = cashboxService.isDayClosed(selectedDate);
        Optional<CashboxManualOpening> manualOpening = cashboxService.getManualOpening(selectedDate);
        boolean admin = SessionManager.getInstance().isAdmin();
        boolean openingLockedForUser = dayClosed || (manualOpening.isPresent() && !admin);

        openingCashField.setDisable(openingLockedForUser);
        if (saveOpeningCashBtn != null) {
            saveOpeningCashBtn.setDisable(openingLockedForUser);
        }
        if (openingCashHintLabel != null) {
            if (dayClosed) {
                openingCashHintLabel.setText("تم إقفال اليوم - لا يمكن تعديل رصيد الافتتاح");
            } else if (manualOpening.isPresent() && !admin) {
                openingCashHintLabel.setText("تم إدخال رصيد الافتتاح — التعديل متاح للمدير فقط");
            } else if (manualOpening.isPresent() && manualOpening.get().getSetBy() != null) {
                openingCashHintLabel.setText("آخر تعديل بواسطة: " + manualOpening.get().getSetBy());
            } else {
                openingCashHintLabel.setText("أدخل رصيد الافتتاح يدوياً ثم اضغط حفظ");
            }
        }

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
    private void handleExportExcel() {
        exportCashbox("xlsx");
    }

    @FXML
    private void handleExportPdf() {
        exportCashbox("pdf");
    }

    private void exportCashbox(String extension) {
        try {
            LocalDate selectedDate = ledgerDatePicker.getValue() != null ? ledgerDatePicker.getValue() : LocalDate.now();
            File selected = showSaveDialog(extension, selectedDate);
            if (selected == null) {
                return;
            }

            List<CashboxExportRow> rows = buildExportRows(selectedDate);
            List<PharmacyReportExportService.ReportColumn<CashboxExportRow>> columns = List.of(
                    new PharmacyReportExportService.ReportColumn<>("التاريخ", CashboxExportRow::date),
                    new PharmacyReportExportService.ReportColumn<>("الرصيد الافتتاحي", CashboxExportRow::openingCash),
                    new PharmacyReportExportService.ReportColumn<>("إجمالي الداخل", CashboxExportRow::totalIn),
                    new PharmacyReportExportService.ReportColumn<>("إجمالي الخارج", CashboxExportRow::totalOut),
                    new PharmacyReportExportService.ReportColumn<>("الرصيد المتوقع", CashboxExportRow::expectedCash),
                    new PharmacyReportExportService.ReportColumn<>("الرصيد الفعلي", CashboxExportRow::actualCash),
                    new PharmacyReportExportService.ReportColumn<>("الفرق", CashboxExportRow::difference),
                    new PharmacyReportExportService.ReportColumn<>("حالة الإقفال", CashboxExportRow::closingStatus),
                    new PharmacyReportExportService.ReportColumn<>("وقت الحركة", CashboxExportRow::transactionDate),
                    new PharmacyReportExportService.ReportColumn<>("نوع الحركة", CashboxExportRow::entryType),
                    new PharmacyReportExportService.ReportColumn<>("الاتجاه", CashboxExportRow::direction),
                    new PharmacyReportExportService.ReportColumn<>("المبلغ", CashboxExportRow::amount),
                    new PharmacyReportExportService.ReportColumn<>("العميل/المذخر", CashboxExportRow::partyName),
                    new PharmacyReportExportService.ReportColumn<>("الحساب", CashboxExportRow::accountName),
                    new PharmacyReportExportService.ReportColumn<>("منفّذ العملية", CashboxExportRow::performer),
                    new PharmacyReportExportService.ReportColumn<>("منح الدين", CashboxExportRow::creditGiver),
                    new PharmacyReportExportService.ReportColumn<>("المصدر", CashboxExportRow::sourceType),
                    new PharmacyReportExportService.ReportColumn<>("رقم المصدر", CashboxExportRow::sourceId),
                    new PharmacyReportExportService.ReportColumn<>("الوصف", CashboxExportRow::description)
            );

            String title = "تقرير الصندوق اليومي " + selectedDate;
            if ("pdf".equalsIgnoreCase(extension)) {
                exportService.exportPdf(selected, title, columns, rows);
            } else {
                exportService.exportExcel(selected, title, columns, rows);
            }
            showInfo("تم", "تم تصدير تقرير الصندوق:\n" + selected.getAbsolutePath());
        } catch (Exception e) {
            showError("خطأ", "فشل تصدير تقرير الصندوق: " + e.getMessage());
        }
    }

    private List<CashboxExportRow> buildExportRows(LocalDate selectedDate) {
        List<CashboxLedger> entries = cashboxService.getLedgerForDate(selectedDate);
        CashboxService.CashTotals totals = cashboxService.calculateTotals(selectedDate);
        Optional<DailyClosing> closing = cashboxService.getClosingByDate(selectedDate);
        String status = closing.map(DailyClosing::getStatus).orElse("OPEN");
        Double actualCash = closing.map(DailyClosing::getActualCash).orElse(null);
        Double difference = closing.map(DailyClosing::getDifferenceAmount).orElse(null);

        List<CashboxExportRow> rows = new ArrayList<>();
        if (entries.isEmpty()) {
            rows.add(new CashboxExportRow(selectedDate, totals.openingCash(), totals.totalIn(), totals.totalOut(),
                    totals.expectedCash(), actualCash, difference, status, null, "-", "-", null, "-", "-", "-", "-", "-", null, "-"));
            return rows;
        }

        for (CashboxLedger entry : entries) {
            rows.add(new CashboxExportRow(
                    selectedDate,
                    totals.openingCash(),
                    totals.totalIn(),
                    totals.totalOut(),
                    totals.expectedCash(),
                    actualCash,
                    difference,
                    status,
                    entry.getTransactionDate(),
                    entryTypeLabel(entry.getEntryType()),
                    directionLabel(entry.getDirection()),
                    entry.getAmount(),
                    resolvePartyName(entry),
                    resolveAccountName(entry),
                    safeText(entry.getCreatedBy()),
                    safeText(entry.getRelatedCreatedBy()),
                    sourceTypeLabel(entry.getSourceType()),
                    entry.getSourceId(),
                    entry.getDescription()
            ));
        }
        return rows;
    }

    private File showSaveDialog(String extension, LocalDate selectedDate) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ تقرير الصندوق");
        fileChooser.setInitialFileName("cashbox_" + selectedDate + "." + extension);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "pdf".equalsIgnoreCase(extension) ? "PDF" : "Excel",
                "*." + extension
        ));
        return fileChooser.showSaveDialog((Stage) ledgerTable.getScene().getWindow());
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

    private String safeText(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }

    private String customerName(Customer customer) {
        return customer != null && customer.getName() != null ? customer.getName() : null;
    }

    private String resolvePartyName(CashboxLedger entry) {
        if (entry == null) {
            return "-";
        }
        if ("supplier_payment".equals(entry.getEntryType())) {
            return safeText(customerName(entry.getSupplier()));
        }
        String customer = customerName(entry.getCustomer());
        if (customer != null) {
            return customer;
        }
        return safeText(customerName(entry.getSupplier()));
    }

    private String resolveAccountName(CashboxLedger entry) {
        if (entry == null || entry.getAccount() == null) {
            return "-";
        }
        String accountName = customerName(entry.getAccount());
        String partyName = resolvePartyName(entry);
        if (accountName != null && !accountName.equals(partyName)) {
            return accountName;
        }
        return accountName != null ? accountName : "-";
    }

    private String entryTypeLabel(String entryType) {
        if (entryType == null) {
            return "-";
        }
        return switch (entryType) {
            case "cash_sale" -> "بيع نقدي";
            case "sale_debt_payment" -> "تحصيل دين بيع";
            case "customer_payment" -> "سند قبض";
            case "supplier_payment" -> "سند دفع مذخر";
            default -> entryType;
        };
    }

    private String directionLabel(String direction) {
        if (direction == null) {
            return "-";
        }
        if ("IN".equalsIgnoreCase(direction)) {
            return "داخل";
        }
        if ("OUT".equalsIgnoreCase(direction)) {
            return "خارج";
        }
        return direction;
    }

    private String sourceTypeLabel(String sourceType) {
        if (sourceType == null) {
            return "-";
        }
        return switch (sourceType) {
            case "sale" -> "فاتورة بيع";
            case "voucher_receipt" -> "سند قبض";
            case "voucher_payment" -> "سند دفع";
            default -> sourceType;
        };
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

    private record CashboxExportRow(LocalDate date,
                                    Double openingCash,
                                    Double totalIn,
                                    Double totalOut,
                                    Double expectedCash,
                                    Double actualCash,
                                    Double difference,
                                    String closingStatus,
                                    LocalDateTime transactionDate,
                                    String entryType,
                                    String direction,
                                    Double amount,
                                    String partyName,
                                    String accountName,
                                    String performer,
                                    String creditGiver,
                                    String sourceType,
                                    Long sourceId,
                                    String description) {}
}
