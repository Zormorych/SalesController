package com.pedropalazon.sales_manager_backend.purchasing.internal.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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

    // --- helpers (los usa también FactoryBatchLifecycleApiTest) ---

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
