package com.pedropalazon.sales_manager_backend.purchasing.internal;

import com.pedropalazon.sales_manager_backend.sales.OrderLineSnapshot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// Excel que se manda al fabricante: un bloque por camiseta, con un marco vacío arriba para pegar la
// captura de la camiseta y debajo equipación, tipo, talla y personalización. Sin precios ni datos de
// clientes: solo lo que el fabricante necesita para hacerla.
final class FactoryBatchExcel {

    private static final int BLOCKS_PER_ROW = 3;
    private static final int FIRST_BLOCK_ROW = 2;
    private static final int FRAME_ROWS = 12;
    private static final int DATA_ROWS = 4;
    // Marco + datos + una fila vacía de separación con la siguiente fila de bloques
    private static final int BLOCK_HEIGHT = FRAME_ROWS + DATA_ROWS + 1;
    private static final int BLOCK_COLUMN_WIDTH = 40 * 256;
    private static final int SPACER_COLUMN_WIDTH = 3 * 256;
    private static final float FRAME_ROW_HEIGHT_POINTS = 20;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private FactoryBatchExcel() {
    }

    static byte[] generate(String batchNumber, LocalDate date, List<OrderLineSnapshot> lines) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName("Pedido " + batchNumber));
            Styles styles = new Styles(workbook);

            writeHeader(sheet, styles, batchNumber, date, lines.size());
            List<OrderLineSnapshot> sorted = lines.stream().sorted(sameShirtsTogether()).toList();
            for (int i = 0; i < sorted.size(); i++) {
                writeBlock(sheet, styles, i, sorted.get(i));
            }
            configureColumnsAndPrinting(sheet);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel del lote " + batchNumber, e);
        }
    }

    // Camisetas iguales seguidas: así se pega la misma captura en bloques contiguos
    private static Comparator<OrderLineSnapshot> sameShirtsTogether() {
        Collator spanish = Collator.getInstance(Locale.forLanguageTag("es-ES"));
        return Comparator.comparing(OrderLineSnapshot::kitName, spanish)
                .thenComparing(OrderLineSnapshot::productName, spanish)
                .thenComparing(OrderLineSnapshot::size);
    }

    private static void writeHeader(Sheet sheet, Styles styles, String batchNumber, LocalDate date, int shirtCount) {
        Cell header = sheet.createRow(0).createCell(0);
        header.setCellValue("Pedido al fabricante %s · %s · %d %s".formatted(batchNumber, date.format(DATE_FORMAT),
                shirtCount, shirtCount == 1 ? "camiseta" : "camisetas"));
        header.setCellStyle(styles.header);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, (BLOCKS_PER_ROW - 1) * 2));
    }

    private static void writeBlock(Sheet sheet, Styles styles, int index, OrderLineSnapshot line) {
        int column = (index % BLOCKS_PER_ROW) * 2;
        int top = FIRST_BLOCK_ROW + (index / BLOCKS_PER_ROW) * BLOCK_HEIGHT;

        for (int r = top; r < top + FRAME_ROWS; r++) {
            Row row = rowAt(sheet, r);
            row.setHeightInPoints(FRAME_ROW_HEIGHT_POINTS);
            row.createCell(column).setCellStyle(styles.frame);
        }
        rowAt(sheet, top).getCell(column).setCellValue("Pega aquí la foto");
        CellRangeAddress frame = new CellRangeAddress(top, top + FRAME_ROWS - 1, column, column);
        sheet.addMergedRegion(frame);
        RegionUtil.setBorderTop(BorderStyle.THICK, frame, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THICK, frame, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THICK, frame, sheet);
        RegionUtil.setBorderRight(BorderStyle.THICK, frame, sheet);

        int dataRow = top + FRAME_ROWS;
        writeData(sheet, styles.kit, dataRow, column, "Equipación: " + line.kitName());
        writeData(sheet, styles.data, dataRow + 1, column, "Tipo: " + line.productName());
        writeData(sheet, styles.data, dataRow + 2, column, "Talla: " + line.size().getLabel());
        writeData(sheet, styles.data, dataRow + 3, column, "Personalización: " + customizationText(line));
    }

    private static String customizationText(OrderLineSnapshot line) {
        if (line.customizations().isEmpty()) {
            return "Sin personalizar";
        }
        return line.customizations().stream()
                .map(customization -> customization.customText() == null
                        ? customization.name()
                        : customization.name() + ": " + customization.customText())
                .collect(Collectors.joining(" + "));
    }

    private static void writeData(Sheet sheet, CellStyle style, int rowIndex, int column, String text) {
        Cell cell = rowAt(sheet, rowIndex).createCell(column);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    private static Row rowAt(Sheet sheet, int index) {
        Row row = sheet.getRow(index);
        return row != null ? row : sheet.createRow(index);
    }

    private static void configureColumnsAndPrinting(Sheet sheet) {
        for (int column = 0; column < BLOCKS_PER_ROW * 2 - 1; column++) {
            sheet.setColumnWidth(column, column % 2 == 0 ? BLOCK_COLUMN_WIDTH : SPACER_COLUMN_WIDTH);
        }
        PrintSetup print = sheet.getPrintSetup();
        print.setLandscape(true);
        print.setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.setFitToPage(true);
        print.setFitWidth((short) 1);
        print.setFitHeight((short) 0);
        sheet.setMargin(Sheet.LeftMargin, 0.3);
        sheet.setMargin(Sheet.RightMargin, 0.3);
        sheet.setMargin(Sheet.TopMargin, 0.4);
        sheet.setMargin(Sheet.BottomMargin, 0.4);
        sheet.setHorizontallyCenter(true);
    }

    private static final class Styles {

        private final CellStyle header;
        private final CellStyle frame;
        private final CellStyle kit;
        private final CellStyle data;

        private Styles(Workbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 14);
            Font hint = workbook.createFont();
            hint.setItalic(true);
            hint.setColor(IndexedColors.GREY_40_PERCENT.getIndex());

            header = workbook.createCellStyle();
            header.setFont(headerFont);

            frame = workbook.createCellStyle();
            frame.setFillForegroundColor(IndexedColors.WHITE.getIndex());
            frame.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            frame.setAlignment(HorizontalAlignment.CENTER);
            frame.setVerticalAlignment(VerticalAlignment.CENTER);
            frame.setFont(hint);

            kit = workbook.createCellStyle();
            kit.setFont(bold);
            kit.setWrapText(true);

            data = workbook.createCellStyle();
            data.setWrapText(true);
        }
    }
}
