package com.pedropalazon.sales_manager_backend.purchasing.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FactoryBatchApiTest {

    static final String BATCHES = "/api/purchasing/batches";
    static final String PENDING = "/api/purchasing/pending-lines";

    @Autowired
    MockMvc mockMvc;

    long productId;
    long customizationId;
    long customerId;

    @BeforeEach
    void createCatalogAndCustomer() throws Exception {
        productId = idOf(postJson("/api/catalog/products",
                "{\"name\":\"Camiseta Fan\",\"salePrice\":15.00,\"purchaseCost\":6.50}"));
        customizationId = idOf(postJson("/api/catalog/customizations",
                "{\"name\":\"Nombre + número\",\"salePrice\":2.00,\"purchaseCost\":1.20}"));
        customerId = idOf(postJson("/api/customers", "{\"name\":\"Ana Pérez\"}"));
    }

    @Test
    void givenBatchedLines_whenListingPending_thenOnlyUnbatchedLinesRemain() throws Exception {
        List<Long> lines = createOrderWithLines(3);

        mockMvc.perform(get(PENDING))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].lineId").value(contains(asInts(lines))));

        createBatch(lines.subList(0, 2), "4.00").andExpect(status().isCreated());

        mockMvc.perform(get(PENDING))
                .andExpect(jsonPath("$[*].lineId").value(contains(lines.get(2).intValue())));
    }

    @Test
    void givenThreeLinesAndFourEurosShipping_whenBatchCreated_thenSplitsShippingByCentsAndComputesProfit()
            throws Exception {
        List<Long> lines = createOrderWithLines(3);

        createBatch(lines, "4.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchNumber").value(matchesPattern("FB-\\d{4}-\\d{4,}")))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.lines[*].lineId").value(contains(asInts(lines))))
                .andExpect(jsonPath("$.lines[*].shippingShare").value(contains(1.34, 1.33, 1.33)))
                .andExpect(jsonPath("$.lines[0].customerName").value("Ana Pérez"))
                .andExpect(jsonPath("$.lines[0].customizations[0].customText").value("WILLIAMS JR 17"))
                .andExpect(jsonPath("$.lines[0].profit").value(7.96))
                .andExpect(jsonPath("$.totals.activeLines").value(3))
                .andExpect(jsonPath("$.totals.sale").value(47.0))
                .andExpect(jsonPath("$.totals.cost").value(20.7))
                .andExpect(jsonPath("$.totals.shipping").value(4.0))
                .andExpect(jsonPath("$.totals.profit").value(22.3));
    }

    @Test
    void givenLineAlreadyInABatch_whenCreatingAnotherBatchWithIt_thenReturnsConflict() throws Exception {
        List<Long> lines = createOrderWithLines(2);
        createBatch(lines.subList(0, 1), "4.00").andExpect(status().isCreated());

        createBatch(lines, "4.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La línea " + lines.get(0) + " ya está en otro lote"));
    }

    @Test
    void givenUnknownLine_whenCreatingBatch_thenReturnsNotFound() throws Exception {
        createBatch(List.of(999999L), "4.00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existen las líneas 999999"));
    }

    @Test
    void givenLineOfCancelledOrder_whenCreatingBatch_thenReturnsConflict() throws Exception {
        String order = createOrder(1);
        mockMvc.perform(post("/api/sales/orders/" + idOf(order) + "/cancel")).andExpect(status().isOk());

        createBatch(lineIdsOf(order), "4.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("no está pendiente de pedir")));
    }

    @Test
    void givenInvalidRequest_whenCreatingBatch_thenReturnsBadRequestOnTheField() throws Exception {
        createBatch(List.of(), "4.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lineIds"));
        createBatch(createOrderWithLines(1), "-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("shippingCost"));
    }

    @Test
    void givenBatch_whenShippingUpdated_thenRecalculatesShares() throws Exception {
        long batchId = idOf(createBatch(createOrderWithLines(3), "4.00").andReturn().getResponse().getContentAsString());

        mockMvc.perform(put(BATCHES + "/" + batchId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingCost\":6.00,\"notes\":\"Factura recibida\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Factura recibida"))
                .andExpect(jsonPath("$.lines[*].shippingShare").value(contains(2.0, 2.0, 2.0)));
    }

    @Test
    void givenTwoBatches_whenListing_thenNewestFirstWithLineCount() throws Exception {
        List<Long> lines = createOrderWithLines(3);
        createBatch(lines.subList(0, 1), "4.00");
        long newest = idOf(createBatch(lines.subList(1, 3), "5.00").andReturn().getResponse().getContentAsString());

        mockMvc.perform(get(BATCHES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newest))
                .andExpect(jsonPath("$[0].lineCount").value(2))
                .andExpect(jsonPath("$[0].shippingCost").value(5.0))
                .andExpect(jsonPath("$[1].lineCount").value(1));
    }

    // Ciclo de vida de un lote abierto: añadir/quitar camisetas, enviarlo o descartarlo
    @Nested
    class Lifecycle {

        @Test
        void givenOpenBatch_whenSent_thenIsSentAndItsLinesAreOrderedAndNoLongerEditable() throws Exception {
            String order = createOrder(2);
            long batchId = idOf(createBatch(lineIdsOf(order), "4.00").andReturn().getResponse().getContentAsString());

            mockMvc.perform(post(BATCHES + "/" + batchId + "/send"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SENT"))
                    .andExpect(jsonPath("$.sentAt").isNotEmpty())
                    .andExpect(jsonPath("$.lines[*].status").value(contains("ORDERED", "ORDERED")));
            mockMvc.perform(get("/api/sales/orders/" + idOf(order)))
                    .andExpect(jsonPath("$.lines[*].status").value(contains("ORDERED", "ORDERED")));
            mockMvc.perform(post("/api/sales/orders/" + idOf(order) + "/lines/" + lineIdsOf(order).get(0) + "/cancel"))
                    .andExpect(status().isConflict());
        }

        @Test
        void givenOrderWhoseShirtsWereSentToTheFactory_whenCancellingTheOrder_thenReturnsConflict() throws Exception {
            String order = createOrder(2);
            long batchId = idOf(createBatch(lineIdsOf(order), "4.00").andReturn().getResponse().getContentAsString());
            mockMvc.perform(post(BATCHES + "/" + batchId + "/send")).andExpect(status().isOk());

            mockMvc.perform(post("/api/sales/orders/" + idOf(order) + "/cancel"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("ya pedidas al fabricante")));
            mockMvc.perform(get(BATCHES + "/" + batchId))
                    .andExpect(jsonPath("$.totals.activeLines").value(2))
                    .andExpect(jsonPath("$.totals.shipping").value(4.0));
        }

        @Test
        void givenSentBatch_whenChangingLinesOrCancelling_thenReturnsConflict() throws Exception {
            List<Long> lines = createOrderWithLines(2);
            long batchId = idOf(createBatch(lines.subList(0, 1), "4.00").andReturn().getResponse().getContentAsString());
            mockMvc.perform(post(BATCHES + "/" + batchId + "/send")).andExpect(status().isOk());

            addLines(batchId, lines.subList(1, 2))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("ya se ha enviado y no se puede modificar")));
            mockMvc.perform(post(BATCHES + "/" + batchId + "/lines/" + lines.get(0) + "/remove"))
                    .andExpect(status().isConflict());
            mockMvc.perform(post(BATCHES + "/" + batchId + "/cancel"))
                    .andExpect(status().isConflict());
        }

        @Test
        void givenOpenBatch_whenCancelled_thenItsLinesAreFreeAgain() throws Exception {
            List<Long> lines = createOrderWithLines(2);
            long batchId = idOf(createBatch(lines, "4.00").andReturn().getResponse().getContentAsString());

            mockMvc.perform(post(BATCHES + "/" + batchId + "/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.lines.length()").value(0));
            mockMvc.perform(get(PENDING))
                    .andExpect(jsonPath("$[*].lineId").value(contains(asInts(lines))));
            createBatch(lines, "4.00").andExpect(status().isCreated());
        }

        @Test
        void givenOpenBatch_whenAddingAndRemovingLines_thenUpdatesItsLines() throws Exception {
            List<Long> lines = createOrderWithLines(3);
            long batchId = idOf(createBatch(lines.subList(0, 1), "4.00").andReturn().getResponse().getContentAsString());

            addLines(batchId, lines.subList(1, 3))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lines[*].lineId").value(contains(asInts(lines))));
            mockMvc.perform(post(BATCHES + "/" + batchId + "/lines/" + lines.get(1) + "/remove"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lines[*].lineId").value(contains(lines.get(0).intValue(), lines.get(2).intValue())));
            mockMvc.perform(post(BATCHES + "/" + batchId + "/lines/" + lines.get(1) + "/remove"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void givenLineCancelledInsideOpenBatch_whenViewingAndSending_thenGetsNoShippingAndBlocksSending()
                throws Exception {
            String order = createOrder(3);
            List<Long> lines = lineIdsOf(order);
            long batchId = idOf(createBatch(lines, "4.00").andReturn().getResponse().getContentAsString());
            mockMvc.perform(post("/api/sales/orders/" + idOf(order) + "/lines/" + lines.get(2) + "/cancel"))
                    .andExpect(status().isOk());

            mockMvc.perform(get(BATCHES + "/" + batchId))
                    .andExpect(jsonPath("$.lines[*].shippingShare").value(contains(2.0, 2.0, 0.0)))
                    .andExpect(jsonPath("$.totals.activeLines").value(2));
            mockMvc.perform(post(BATCHES + "/" + batchId + "/send"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail")
                            .value("La línea " + lines.get(2) + " del lote está cancelada: quítala antes de enviar"));

            mockMvc.perform(post(BATCHES + "/" + batchId + "/lines/" + lines.get(2) + "/remove")).andExpect(status().isOk());
            mockMvc.perform(post(BATCHES + "/" + batchId + "/send")).andExpect(status().isOk());
        }

        @Test
        void givenEmptyOpenBatch_whenSent_thenReturnsConflict() throws Exception {
            List<Long> lines = createOrderWithLines(1);
            long batchId = idOf(createBatch(lines, "4.00").andReturn().getResponse().getContentAsString());
            mockMvc.perform(post(BATCHES + "/" + batchId + "/lines/" + lines.get(0) + "/remove")).andExpect(status().isOk());

            mockMvc.perform(post(BATCHES + "/" + batchId + "/send"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("no tiene camisetas")));
        }

        private ResultActions addLines(long batchId, List<Long> lineIds) throws Exception {
            return mockMvc.perform(post(BATCHES + "/" + batchId + "/lines").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"lineIds\":%s}".formatted(lineIds)));
        }
    }

    // Recepción del lote: puede faltar alguna camiseta sin que el fabricante avise
    @Nested
    class Reception {

        @Test
        void givenBatchOfTenShirts_whenOnlyNineArrive_thenNineAreReceivedAndTheMissingOneIsPendingAgain()
                throws Exception {
            String order = createOrder(10);
            List<Long> lines = lineIdsOf(order);
            long missing = lines.get(9);
            long batchId = sentBatch(lines, "4.00");

            receive(batchId, "{\"missingLineIds\":[" + missing + "]}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RECEIVED"))
                    .andExpect(jsonPath("$.receivedAt").isNotEmpty())
                    .andExpect(jsonPath("$.lines.length()").value(9))
                    .andExpect(jsonPath("$.lines[*].status").value(everyItem(is("RECEIVED"))))
                    .andExpect(jsonPath("$.missingLines.length()").value(1))
                    .andExpect(jsonPath("$.missingLines[0].lineId").value(missing))
                    .andExpect(jsonPath("$.missingLines[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.totals.activeLines").value(9))
                    .andExpect(jsonPath("$.totals.shipping").value(4.0));

            mockMvc.perform(get("/api/sales/orders/" + idOf(order)))
                    .andExpect(jsonPath("$.progress").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.lineCounts.received").value(9))
                    .andExpect(jsonPath("$.lineCounts.pending").value(1));
            mockMvc.perform(get(PENDING))
                    .andExpect(jsonPath("$[*].lineId").value(contains((int) missing)));
            createBatch(List.of(missing), "4.00").andExpect(status().isCreated());
        }

        @Test
        void givenSentBatch_whenEverythingArrives_thenAllShirtsAreReceivedAndNothingIsMissing() throws Exception {
            long batchId = sentBatch(createOrderWithLines(3), "4.00");

            mockMvc.perform(post(BATCHES + "/" + batchId + "/receive"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RECEIVED"))
                    .andExpect(jsonPath("$.lines[*].status").value(contains("RECEIVED", "RECEIVED", "RECEIVED")))
                    .andExpect(jsonPath("$.missingLines.length()").value(0));
        }

        @Test
        void givenShirtThatDidNotArrive_whenEditingItInItsOrder_thenIsAllowedAndGetsNoShipping() throws Exception {
            String order = createOrder(2);
            List<Long> lines = lineIdsOf(order);
            long batchId = sentBatch(lines, "4.00");

            receive(batchId, "{\"missingLineIds\":[" + lines.get(1) + "]}")
                    .andExpect(jsonPath("$.lines[*].shippingShare").value(contains(4.0)));
            mockMvc.perform(put("/api/sales/orders/" + idOf(order) + "/lines/" + lines.get(1))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":%d,\"kitName\":\"Selección Española roja\",\"size\":\"L\"}"
                                    .formatted(productId)))
                    .andExpect(status().isOk());
        }

        @Test
        void givenBatchNotSentOrAlreadyReceived_whenReceiving_thenReturnsConflict() throws Exception {
            List<Long> lines = createOrderWithLines(2);
            long openBatch = idOf(createBatch(lines.subList(0, 1), "4.00").andReturn().getResponse().getContentAsString());
            receive(openBatch, "{}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("todavía no se ha enviado")));

            long sentBatch = sentBatch(lines.subList(1, 2), "4.00");
            receive(sentBatch, "{}").andExpect(status().isOk());
            receive(sentBatch, "{}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("ya se ha recibido")));
        }

        @Test
        void givenMissingShirtThatTheFactorySendsLater_whenMarkedArrived_thenIsReceivedInItsBatchAgain()
                throws Exception {
            List<Long> lines = createOrderWithLines(2);
            long batchId = sentBatch(lines, "4.00");
            receive(batchId, "{\"missingLineIds\":[" + lines.get(1) + "]}").andExpect(status().isOk());

            mockMvc.perform(post(BATCHES + "/" + batchId + "/missing-lines/" + lines.get(1) + "/arrived"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lines[*].status").value(contains("RECEIVED", "RECEIVED")))
                    .andExpect(jsonPath("$.lines[*].shippingShare").value(contains(2.0, 2.0)))
                    .andExpect(jsonPath("$.missingLines.length()").value(0));
            mockMvc.perform(get(PENDING))
                    .andExpect(jsonPath("$[*].lineId").value(not(hasItem(lines.get(1).intValue()))));
        }

        @Test
        void givenMissingShirtAlreadyOrderedAgainInAnotherBatch_whenMarkedArrived_thenReturnsConflict()
                throws Exception {
            List<Long> lines = createOrderWithLines(2);
            long batchId = sentBatch(lines, "4.00");
            receive(batchId, "{\"missingLineIds\":[" + lines.get(1) + "]}").andExpect(status().isOk());
            createBatch(List.of(lines.get(1)), "4.00").andExpect(status().isCreated());

            mockMvc.perform(post(BATCHES + "/" + batchId + "/missing-lines/" + lines.get(1) + "/arrived"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("ya está en otro lote")));
        }

        @Test
        void givenSentBatch_whenMarkingEveryShirtAsMissing_thenReturnsConflict() throws Exception {
            List<Long> lines = createOrderWithLines(2);
            long batchId = sentBatch(lines, "4.00");

            receive(batchId, "{\"missingLineIds\":" + lines + "}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("no ha llegado ninguna camiseta")));
        }

        @Test
        void givenShirtThatIsNotInTheBatch_whenMarkedMissing_thenReturnsNotFound() throws Exception {
            List<Long> lines = createOrderWithLines(2);
            long batchId = sentBatch(lines.subList(0, 1), "4.00");

            receive(batchId, "{\"missingLineIds\":[" + lines.get(1) + "]}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value(containsString("no está en el lote")));
        }

        private long sentBatch(List<Long> lineIds, String shippingCost) throws Exception {
            long batchId = idOf(createBatch(lineIds, shippingCost).andReturn().getResponse().getContentAsString());
            mockMvc.perform(post(BATCHES + "/" + batchId + "/send")).andExpect(status().isOk());
            return batchId;
        }

        private ResultActions receive(long batchId, String json) throws Exception {
            return mockMvc.perform(post(BATCHES + "/" + batchId + "/receive")
                    .contentType(MediaType.APPLICATION_JSON).content(json));
        }
    }

    // Descarga del Excel para el fabricante
    @Nested
    class Excel {

        @Test
        void givenBatch_whenDownloadingExcel_thenReturnsXlsxAttachmentWithItsShirts() throws Exception {
            long batchId = idOf(createBatch(createOrderWithLines(2), "4.00").andReturn().getResponse().getContentAsString());
            String batchNumber = JsonPath.read(mockMvc.perform(get(BATCHES + "/" + batchId))
                    .andReturn().getResponse().getContentAsString(), "$.batchNumber");

            byte[] content = mockMvc.perform(get(BATCHES + "/" + batchId + "/excel"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .andExpect(header().string("Content-Disposition",
                            containsString("attachment; filename=\"" + batchNumber + ".xlsx\"")))
                    .andReturn().getResponse().getContentAsByteArray();

            List<String> texts = cellTexts(content);
            assertThat(texts).filteredOn(text -> text.equals("Equipación: Selección Española blanca")).hasSize(2);
            assertThat(texts).contains("Personalización: Nombre + número \"WILLIAMS JR 17\"");
        }

        @Test
        void givenCancelledShirtInsideBatch_whenDownloadingExcel_thenItIsLeftOut() throws Exception {
            String order = createOrder(3);
            List<Long> lines = lineIdsOf(order);
            long batchId = idOf(createBatch(lines, "4.00").andReturn().getResponse().getContentAsString());
            mockMvc.perform(post("/api/sales/orders/" + idOf(order) + "/lines/" + lines.get(2) + "/cancel"))
                    .andExpect(status().isOk());

            byte[] content = mockMvc.perform(get(BATCHES + "/" + batchId + "/excel"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();

            assertThat(cellTexts(content)).filteredOn(text -> text.startsWith("Equipación: ")).hasSize(2);
            assertThat(cellTexts(content).get(0)).contains("2 camisetas");
        }

        @Test
        void givenDiscardedBatch_whenDownloadingExcel_thenReturnsConflict() throws Exception {
            long batchId = idOf(createBatch(createOrderWithLines(1), "4.00").andReturn().getResponse().getContentAsString());
            mockMvc.perform(post(BATCHES + "/" + batchId + "/cancel")).andExpect(status().isOk());

            mockMvc.perform(get(BATCHES + "/" + batchId + "/excel"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("está cancelado")));
        }

        @Test
        void givenUnknownBatch_whenDownloadingExcel_thenReturnsNotFound() throws Exception {
            mockMvc.perform(get(BATCHES + "/999999/excel"))
                    .andExpect(status().isNotFound());
        }

        private List<String> cellTexts(byte[] content) throws Exception {
            List<String> texts = new ArrayList<>();
            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
                for (Row row : workbook.getSheetAt(0)) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.STRING) {
                            texts.add(cell.getStringCellValue());
                        }
                    }
                }
            }
            return texts;
        }
    }

    // --- helpers ---

    ResultActions createBatch(List<Long> lineIds, String shippingCost) throws Exception {
        return mockMvc.perform(post(BATCHES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lineIds\":%s,\"shippingCost\":%s}".formatted(lineIds, shippingCost)));
    }

    // La primera camiseta lleva "Nombre + número": venta 17,00 / coste 7,70; las demás 15,00 / 6,50
    List<Long> createOrderWithLines(int count) throws Exception {
        return lineIdsOf(createOrder(count));
    }

    String createOrder(int count) throws Exception {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String customizations = i == 0
                    ? "[{\"customizationId\":%d,\"customText\":\"WILLIAMS JR 17\"}]".formatted(customizationId)
                    : "[]";
            lines.append(i == 0 ? "" : ",").append(("{\"productId\":%d,\"kitName\":\"Selección Española blanca\","
                    + "\"size\":\"M\",\"customizations\":%s}").formatted(productId, customizations));
        }
        return postJson("/api/sales/orders", "{\"customerId\":%d,\"lines\":[%s]}".formatted(customerId, lines));
    }

    String postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    static long idOf(String body) {
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    static List<Long> lineIdsOf(String orderBody) {
        List<Number> ids = JsonPath.read(orderBody, "$.lines[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    static Object[] asInts(List<Long> ids) {
        return ids.stream().map(Long::intValue).toArray();
    }
}
