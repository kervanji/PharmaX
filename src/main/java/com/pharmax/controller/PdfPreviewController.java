package com.pharmax.controller;

import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import com.pharmax.util.FxUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.print.PrinterJob;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PdfPreviewController {
    private static final Logger logger = LoggerFactory.getLogger(PdfPreviewController.class);

    @FXML private VBox pagesContainer;
    @FXML private Button printButton;
    @FXML private Button saveButton;

    private Stage dialogStage;
    private File pdfFile;
    private boolean savedByUser = false;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        if (dialogStage != null) {
            dialogStage.setOnCloseRequest(this::handleWindowCloseRequest);
        }
    }

    public void setPdfFile(File pdfFile) {
        this.pdfFile = pdfFile;
        this.savedByUser = false;
        loadPdfPreview();
    }

    private void loadPdfPreview() {
        if (pdfFile == null || !pdfFile.exists()) return;

        Task<List<WritableImage>> loadTask = new Task<>() {
            @Override
            protected List<WritableImage> call() throws Exception {
                List<WritableImage> images = new ArrayList<>();
                try (PDDocument document = PDDocument.load(pdfFile)) {
                    PDFRenderer pdfRenderer = new PDFRenderer(document);
                    for (int page = 0; page < document.getNumberOfPages(); page++) {
                        BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 150, ImageType.RGB);
                        images.add(SwingFXUtils.toFXImage(bim, null));
                    }
                }
                return images;
            }
        };

        loadTask.setOnSucceeded(e -> {
            pagesContainer.getChildren().clear();
            for (WritableImage image : loadTask.getValue()) {
                ImageView imageView = new ImageView(image);
                imageView.setFitWidth(595); // A4 width at roughly 72 DPI (adjust as needed)
                imageView.setPreserveRatio(true);
                imageView.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 10, 0, 0, 0);");
                pagesContainer.getChildren().add(imageView);
            }
        });

        loadTask.setOnFailed(e -> {
            logger.error("Failed to load PDF preview", loadTask.getException());
            // Show error placeholder or alert
        });

        new Thread(loadTask).start();
    }

    @FXML
    private void handlePrint() {
        if (pdfFile == null || !pdfFile.exists()) return;

        Task<Void> printTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (PDDocument document = PDDocument.load(pdfFile)) {
                    PrinterJob job = PrinterJob.getPrinterJob();
                    job.setPageable(new PDFPageable(document));
                    if (job.printDialog()) {
                        job.print();
                    }
                }
                return null;
            }
        };

        printTask.setOnFailed(e -> logger.error("Failed to print PDF", printTask.getException()));

        new Thread(printTask).start();
    }

    @FXML
    private void handleSave() {
        savePdfCopy();
    }

    private boolean savePdfCopy() {
        if (pdfFile == null || !pdfFile.exists()) {
            return false;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ الفاتورة PDF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName(pdfFile.getName());

        File targetFile = dialogStage != null ? fileChooser.showSaveDialog(dialogStage) : fileChooser.showSaveDialog(null);
        if (targetFile == null) {
            return false;
        }

        try {
            Files.copy(pdfFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            savedByUser = true;
            return true;
        } catch (Exception e) {
            logger.error("Failed to save PDF copy", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("خطأ");
            alert.setHeaderText(null);
            alert.setContentText("فشل في حفظ ملف الفاتورة");
            alert.showAndWait();
            return false;
        }
    }

    private void handleWindowCloseRequest(WindowEvent event) {
        if (savedByUser || pdfFile == null || !pdfFile.exists()) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("حفظ الفاتورة");
        alert.setHeaderText(null);
        alert.setContentText("هل تريد حفظ نسخة من ملف الفاتورة قبل الإغلاق؟");
        ButtonType save = new ButtonType("حفظ");
        ButtonType closeWithoutSaving = new ButtonType("إغلاق بدون حفظ");
        ButtonType cancel = ButtonType.CANCEL;
        alert.getButtonTypes().setAll(save, closeWithoutSaving, cancel);

        Optional<ButtonType> response = alert.showAndWait();
        if (response.isEmpty() || response.get() == cancel) {
            event.consume();
            return;
        }

        if (response.get() == save && !savePdfCopy()) {
            event.consume();
        }
    }

    @FXML
    private void handleClose() {
        if (dialogStage != null) {
            WindowEvent closeRequest = new WindowEvent(dialogStage, WindowEvent.WINDOW_CLOSE_REQUEST);
            dialogStage.fireEvent(closeRequest);
            if (!closeRequest.isConsumed()) {
                dialogStage.close();
            }
        } else {
            FxUtil.closeWindow(printButton);
        }
    }
}
