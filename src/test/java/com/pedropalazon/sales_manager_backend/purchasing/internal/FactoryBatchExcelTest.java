package com.pedropalazon.sales_manager_backend.purchasing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.sales.LineStatus;
import com.pedropalazon.sales_manager_backend.sales.OrderLineSnapshot;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FactoryBatchExcelTest {

    // Disposición que se espera: 3 bloques por fila en las columnas 0, 2 y 4; cada bloque ocupa
    // 12 filas de marco + 4 de datos + 1 de separación, empezando en la fila 2
    private static final int FIRST_BLOCK_ROW = 2;
    private static final int FRAME_ROWS = 12;
    private static final int BLOCK_HEIGHT = 17;

    private Sheet sheet;

    @BeforeEach
    void generateExcelWithFourShirts() throws IOException {
        List<OrderLineSnapshot> lines = List.of(
                line("Selección Española blanca 2 estrellas", "Camiseta Player", ClothingSize.L,
                        new OrderLineSnapshot.Customization("Nombre + número", "WILLIAMS JR 17"),
                        new OrderLineSnapshot.Customization("Parche", null)),
                line("Barcelona local", "Camiseta Fan", ClothingSize.M),
                line("Selección Española blanca 2 estrellas", "Camiseta Player", ClothingSize.M),
                line("Selección España niño", "Camiseta Fan", ClothingSize.KIDS_24));

        byte[] excel = FactoryBatchExcel.generate("FB-2026-0001", LocalDate.of(2026, 10, 16), lines);

        Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel));
        sheet = workbook.getSheetAt(0);
    }

    @Test
    void givenBatch_whenGenerated_thenHeaderShowsBatchNumberDateAndShirtCount() {
        assertThat(text(0, 0)).contains("FB-2026-0001").contains("16/10/2026").contains("4 camisetas");
    }

    @Test
    void givenShirts_whenGenerated_thenEachBlockHasKitTypeSizeAndCustomizationInOrder() {
        assertBlock(0, "Barcelona local", "Camiseta Fan", "M", "Sin personalizar");
        assertBlock(1, "Selección España niño", "Camiseta Fan", "24 (niño)", "Sin personalizar");
        assertBlock(2, "Selección Española blanca 2 estrellas", "Camiseta Player", "M", "Sin personalizar");
        assertBlock(3, "Selección Española blanca 2 estrellas", "Camiseta Player", "L",
                "Nombre + número: WILLIAMS JR 17 + Parche");
    }

    @Test
    void givenShirts_whenGenerated_thenEveryBlockHasABorderedFrameRightAboveItsData() {
        for (int block = 0; block < 4; block++) {
            int column = columnOf(block);
            int frameTop = topOf(block);
            CellRangeAddress frame = new CellRangeAddress(frameTop, frameTop + FRAME_ROWS - 1, column, column);

            assertThat(sheet.getMergedRegions()).contains(frame);
            Cell frameCell = sheet.getRow(frameTop).getCell(column);
            assertThat(frameCell.getStringCellValue()).isEqualTo("Pega aquí la foto");
            assertThat(frameCell.getCellStyle().getBorderTop()).isEqualTo(BorderStyle.THICK);
            assertThat(frameCell.getCellStyle().getBorderLeft()).isEqualTo(BorderStyle.THICK);
            assertThat(text(frameTop + FRAME_ROWS, column)).startsWith("Equipación: ");
        }
    }

    @Test
    void givenFourShirts_whenGenerated_thenTheFourthGoesToTheSecondRowOfBlocks() {
        assertThat(columnOf(3)).isZero();
        assertThat(topOf(3)).isEqualTo(FIRST_BLOCK_ROW + BLOCK_HEIGHT);
        assertThat(text(topOf(3) + FRAME_ROWS, 0)).isEqualTo("Equipación: Selección Española blanca 2 estrellas");
    }

    @Test
    void givenShirts_whenGenerated_thenNoCellContainsPricesOrNumbers() {
        List<Cell> cells = new ArrayList<>();
        for (Row row : sheet) {
            row.forEach(cells::add);
        }

        assertThat(cells).isNotEmpty();
        assertThat(cells).noneMatch(cell -> cell.getCellType() == CellType.NUMERIC);
        assertThat(cells).noneMatch(cell -> cell.getCellType() == CellType.STRING && cell.getStringCellValue().contains("€"));
    }

    @Test
    void givenSheet_whenGenerated_thenIsLandscapeAndFitsOnePageWide() {
        assertThat(sheet.getPrintSetup().getLandscape()).isTrue();
        assertThat(sheet.getFitToPage()).isTrue();
        assertThat(sheet.getPrintSetup().getFitWidth()).isEqualTo((short) 1);
        assertThat(sheet.getPrintSetup().getFitHeight()).isEqualTo((short) 0);
    }

    private void assertBlock(int block, String kit, String product, String size, String customization) {
        int column = columnOf(block);
        int dataRow = topOf(block) + FRAME_ROWS;
        assertThat(text(dataRow, column)).isEqualTo("Equipación: " + kit);
        assertThat(text(dataRow + 1, column)).isEqualTo("Tipo: " + product);
        assertThat(text(dataRow + 2, column)).isEqualTo("Talla: " + size);
        assertThat(text(dataRow + 3, column)).isEqualTo("Personalización: " + customization);
    }

    private static int columnOf(int block) {
        return (block % 3) * 2;
    }

    private static int topOf(int block) {
        return FIRST_BLOCK_ROW + (block / 3) * BLOCK_HEIGHT;
    }

    private String text(int row, int column) {
        return sheet.getRow(row).getCell(column).getStringCellValue();
    }

    private static OrderLineSnapshot line(String kit, String product, ClothingSize size,
            OrderLineSnapshot.Customization... customizations) {
        return new OrderLineSnapshot(1L, 1L, "SO-2026-000001", LocalDate.of(2026, 10, 1), 1L, "Ana Pérez", 1L, product,
                kit, size, LineStatus.ORDERED, List.of(customizations), new BigDecimal("17.00"), new BigDecimal("7.70"));
    }
}
