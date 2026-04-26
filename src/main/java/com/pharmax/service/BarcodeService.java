package com.pharmax.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.pharmax.model.Product;
import com.pharmax.model.ProductUnit;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;

public class BarcodeService {
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.##");

    public BufferedImage generateBarcodeImage(String barcodeText, int width, int height) {
        String normalized = normalizeBarcodeText(barcodeText);
        try {
            BitMatrix bitMatrix = new Code128Writer().encode(
                    normalized,
                    BarcodeFormat.CODE_128,
                    Math.max(160, width),
                    Math.max(50, height)
            );
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("تعذر توليد الباركود: " + e.getMessage(), e);
        }
    }

    public BufferedImage renderLabel(String barcodeText, String productName, String priceText, LabelOptions options) {
        LabelOptions labelOptions = options != null ? options : new LabelOptions();
        int width = Math.max(200, labelOptions.getPreviewWidthPixels());
        int height = Math.max(120, labelOptions.getPreviewHeightPixels());

        BufferedImage label = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = label.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            boolean showText = !labelOptions.isBarcodeOnly()
                    && (labelOptions.isShowProductName() || labelOptions.isShowPrice());
            int textAreaHeight = showText ? Math.max(34, height / 4) : 10;
            int barcodeHeight = Math.max(55, height - textAreaHeight - 8);
            BufferedImage barcode = generateBarcodeImage(barcodeText, width - 24, barcodeHeight);
            g.drawImage(barcode, 12, 6, width - 24, barcodeHeight, null);

            if (showText) {
                g.setColor(Color.BLACK);
                g.setFont(new Font("Arial", Font.PLAIN, Math.max(12, height / 13)));
                FontMetrics fm = g.getFontMetrics();
                int y = barcodeHeight + 22;

                if (labelOptions.isShowProductName() && productName != null && !productName.isBlank()) {
                    drawCenteredText(g, trimToFit(productName, fm, width - 18), width, y);
                    y += fm.getHeight();
                }

                if (labelOptions.isShowPrice() && priceText != null && !priceText.isBlank()) {
                    g.setFont(g.getFont().deriveFont(Font.BOLD));
                    drawCenteredText(g, priceText, width, Math.min(y, height - 8));
                }
            }
        } finally {
            g.dispose();
        }
        return label;
    }

    public String createBarcodeValue(Product product, ProductUnit unit) {
        if (unit != null && unit.getId() != null) {
            return "PXU" + unit.getId();
        }
        if (product != null && product.getId() != null) {
            return "PXP" + product.getId();
        }
        if (product != null && product.getProductCode() != null && !product.getProductCode().isBlank()) {
            return product.getProductCode().trim();
        }
        return "PX" + System.currentTimeMillis();
    }

    public String formatPrice(Double price, String currency) {
        if (price == null || price <= 0) {
            return "";
        }
        String suffix = "دولار".equals(currency) ? "$" : "د.ع";
        return PRICE_FORMAT.format(price) + " " + suffix;
    }

    private void drawCenteredText(Graphics2D g, String text, int width, int y) {
        FontMetrics fm = g.getFontMetrics();
        int x = Math.max(0, (width - fm.stringWidth(text)) / 2);
        g.drawString(text, x, y);
    }

    private String trimToFit(String text, FontMetrics fm, int maxWidth) {
        if (text == null || fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        String value = text;
        while (!value.isEmpty() && fm.stringWidth(value + ellipsis) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ellipsis;
    }

    private String normalizeBarcodeText(String barcodeText) {
        if (barcodeText == null || barcodeText.trim().isEmpty()) {
            throw new IllegalArgumentException("نص الباركود مطلوب");
        }
        return barcodeText.trim();
    }

    public static class LabelOptions {
        private boolean barcodeOnly;
        private boolean showProductName = true;
        private boolean showPrice = true;
        private double labelWidthMm = 50.0;
        private double labelHeightMm = 30.0;

        public boolean isBarcodeOnly() {
            return barcodeOnly;
        }

        public void setBarcodeOnly(boolean barcodeOnly) {
            this.barcodeOnly = barcodeOnly;
        }

        public boolean isShowProductName() {
            return showProductName;
        }

        public void setShowProductName(boolean showProductName) {
            this.showProductName = showProductName;
        }

        public boolean isShowPrice() {
            return showPrice;
        }

        public void setShowPrice(boolean showPrice) {
            this.showPrice = showPrice;
        }

        public double getLabelWidthMm() {
            return labelWidthMm;
        }

        public void setLabelWidthMm(double labelWidthMm) {
            this.labelWidthMm = labelWidthMm;
        }

        public double getLabelHeightMm() {
            return labelHeightMm;
        }

        public void setLabelHeightMm(double labelHeightMm) {
            this.labelHeightMm = labelHeightMm;
        }

        public int getPreviewWidthPixels() {
            return (int) Math.round(labelWidthMm * 8);
        }

        public int getPreviewHeightPixels() {
            return (int) Math.round(labelHeightMm * 8);
        }
    }
}
