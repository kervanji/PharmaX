package com.pharmax.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.Arrays;
import java.util.List;

public class BarcodeLabelPrintService {
    private static final double POINTS_PER_MM = 72.0 / 25.4;

    private final BarcodeService barcodeService;

    public BarcodeLabelPrintService() {
        this.barcodeService = new BarcodeService();
    }

    public List<String> getPrinterNames() {
        return Arrays.stream(PrinterJob.lookupPrintServices())
                .map(javax.print.PrintService::getName)
                .sorted()
                .toList();
    }

    public void printLabels(BarcodeLabelRequest request) {
        validateRequest(request);
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            if (request.getPrinterName() != null && !request.getPrinterName().isBlank()) {
                for (javax.print.PrintService service : PrinterJob.lookupPrintServices()) {
                    if (service.getName().equals(request.getPrinterName())) {
                        job.setPrintService(service);
                        break;
                    }
                }
            }

            PageFormat pageFormat = job.defaultPage();
            Paper paper = new Paper();
            double widthPoints = request.getOptions().getLabelWidthMm() * POINTS_PER_MM;
            double heightPoints = request.getOptions().getLabelHeightMm() * POINTS_PER_MM;
            paper.setSize(widthPoints, heightPoints);
            paper.setImageableArea(0, 0, widthPoints, heightPoints);
            pageFormat.setPaper(paper);
            pageFormat.setOrientation(PageFormat.PORTRAIT);

            Book book = new Book();
            for (int i = 0; i < request.getCopies(); i++) {
                book.append(new LabelPrintable(request), pageFormat);
            }
            job.setPageable(book);
            job.print();
        } catch (PrinterException e) {
            throw new RuntimeException("فشل إرسال الملصقات إلى الطابعة: " + e.getMessage(), e);
        }
    }

    private void validateRequest(BarcodeLabelRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("بيانات الطباعة مطلوبة");
        }
        if (request.getBarcodeText() == null || request.getBarcodeText().trim().isEmpty()) {
            throw new IllegalArgumentException("الباركود مطلوب قبل الطباعة");
        }
        if (request.getCopies() <= 0) {
            throw new IllegalArgumentException("عدد الملصقات يجب أن يكون أكبر من صفر");
        }
        if (request.getOptions() == null) {
            request.setOptions(new BarcodeService.LabelOptions());
        }
        if (request.getOptions().getLabelWidthMm() <= 0 || request.getOptions().getLabelHeightMm() <= 0) {
            throw new IllegalArgumentException("حجم الملصق غير صحيح");
        }
    }

    private class LabelPrintable implements Printable {
        private final BarcodeLabelRequest request;

        private LabelPrintable(BarcodeLabelRequest request) {
            this.request = request;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex >= 1) {
                return NO_SUCH_PAGE;
            }
            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            BufferedImage label = barcodeService.renderLabel(
                    request.getBarcodeText(),
                    request.getProductName(),
                    request.getPriceText(),
                    request.getOptions()
            );
            g.drawImage(
                    label,
                    (int) pageFormat.getImageableX(),
                    (int) pageFormat.getImageableY(),
                    (int) pageFormat.getImageableWidth(),
                    (int) pageFormat.getImageableHeight(),
                    null
            );
            return PAGE_EXISTS;
        }
    }

    public static class BarcodeLabelRequest {
        private String barcodeText;
        private String productName;
        private String priceText;
        private int copies = 1;
        private String printerName;
        private BarcodeService.LabelOptions options = new BarcodeService.LabelOptions();

        public String getBarcodeText() {
            return barcodeText;
        }

        public void setBarcodeText(String barcodeText) {
            this.barcodeText = barcodeText;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getPriceText() {
            return priceText;
        }

        public void setPriceText(String priceText) {
            this.priceText = priceText;
        }

        public int getCopies() {
            return copies;
        }

        public void setCopies(int copies) {
            this.copies = copies;
        }

        public String getPrinterName() {
            return printerName;
        }

        public void setPrinterName(String printerName) {
            this.printerName = printerName;
        }

        public BarcodeService.LabelOptions getOptions() {
            return options;
        }

        public void setOptions(BarcodeService.LabelOptions options) {
            this.options = options;
        }
    }
}
