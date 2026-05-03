package com.pharmax.service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

public class PharmacyReportExportService {
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.##");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public <T> void exportExcel(File outputFile, String title, List<ReportColumn<T>> columns, List<T> rows) throws Exception {
        validate(outputFile, columns, rows);
        ensureParent(outputFile);

        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet(safeSheetName(title));

            XSSFFont titleFont = (XSSFFont) workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            XSSFCellStyle titleStyle = (XSSFCellStyle) workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            XSSFFont headerFont = (XSSFFont) workbook.createFont();
            headerFont.setBold(true);
            XSSFCellStyle headerStyle = (XSSFCellStyle) workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int rowIdx = 0;
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.createCell(0).setCellValue(title);
            titleRow.getCell(0).setCellStyle(titleStyle);

            Row dateRow = sheet.createRow(rowIdx++);
            dateRow.createCell(0).setCellValue("تاريخ التصدير: " + LocalDateTime.now().format(DATE_TIME_FORMAT));
            rowIdx++;

            Row headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < columns.size(); i++) {
                headerRow.createCell(i).setCellValue(columns.get(i).header());
                headerRow.getCell(i).setCellStyle(headerStyle);
            }

            for (T row : rows) {
                Row excelRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < columns.size(); i++) {
                    Object value = columns.get(i).valueExtractor().apply(row);
                    if (value instanceof Number number) {
                        excelRow.createCell(i).setCellValue(number.doubleValue());
                    } else {
                        excelRow.createCell(i).setCellValue(formatValue(value));
                    }
                }
            }

            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                workbook.write(out);
            }
        }
    }

    public <T> void exportPdf(File outputFile, String title, List<ReportColumn<T>> columns, List<T> rows) throws Exception {
        validate(outputFile, columns, rows);
        ensureParent(outputFile);

        Document document = new Document(columns.size() > 6 ? PageSize.A4.rotate() : PageSize.A4, 24, 24, 28, 28);
        PdfWriter.getInstance(document, new FileOutputStream(outputFile));
        document.open();

        BaseFont baseFont = loadArabicBaseFont();
        Font titleFont = new Font(baseFont, 16, Font.BOLD);
        Font headerFont = new Font(baseFont, 10, Font.BOLD);
        Font bodyFont = new Font(baseFont, 9, Font.NORMAL);
        Font smallFont = new Font(baseFont, 8, Font.NORMAL);

        addCenteredRtlText(document, title, titleFont, 0, 8);
        addCenteredRtlText(document, "تاريخ التصدير: " + LocalDateTime.now().format(DATE_TIME_FORMAT), smallFont, 0, 12);

        PdfPTable table = new PdfPTable(columns.size());
        table.setWidthPercentage(100);
        table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        for (ReportColumn<T> column : columns) {
            addHeaderCell(table, column.header(), headerFont);
        }

        for (T row : rows) {
            for (ReportColumn<T> column : columns) {
                addBodyCell(table, formatValue(column.valueExtractor().apply(row)), bodyFont);
            }
        }

        document.add(table);
        document.close();
    }

    private void validate(File outputFile, List<?> columns, List<?> rows) {
        if (outputFile == null) {
            throw new IllegalArgumentException("مسار الملف غير صحيح");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("أعمدة التقرير غير محددة");
        }
        if (rows == null) {
            throw new IllegalArgumentException("بيانات التقرير غير محددة");
        }
    }

    private void ensureParent(File outputFile) {
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    private String safeSheetName(String title) {
        String value = title == null || title.isBlank() ? "Report" : title.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return value.length() > 31 ? value.substring(0, 31) : value;
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof LocalDate date) {
            return date.format(DATE_FORMAT);
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME_FORMAT);
        }
        if (value instanceof Number number) {
            return NUMBER_FORMAT.format(number.doubleValue());
        }
        String text = String.valueOf(value);
        return text.isBlank() ? "-" : text;
    }

    private BaseFont loadArabicBaseFont() throws Exception {
        String[] fontCandidates = new String[]{
                "C:\\Windows\\Fonts\\arial.ttf",
                "C:\\Windows\\Fonts\\tahoma.ttf",
                "C:\\Windows\\Fonts\\arialuni.ttf",
                "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                "/System/Library/Fonts/Supplemental/Damascus.ttc,0",
                "/System/Library/Fonts/Supplemental/KufiStandardGK.ttc,0",
                "/Library/Fonts/Arial Unicode.ttf",
                "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Regular.ttf",
                "/usr/share/fonts/truetype/noto/NotoSansArabic-Regular.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        };
        for (String fontPath : fontCandidates) {
            try {
                return BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ignored) {
            }
        }
        return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
    }

    private void addCenteredRtlText(Document document, String text, Font font, float before, float after) throws Exception {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingBefore(before);
        paragraph.setSpacingAfter(after);

        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        wrapper.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.addElement(paragraph);
        wrapper.addCell(cell);
        document.add(wrapper);
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        cell.setPadding(5f);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setPadding(4f);
        table.addCell(cell);
    }

    public record ReportColumn<T>(String header, Function<T, Object> valueExtractor) {}
}
