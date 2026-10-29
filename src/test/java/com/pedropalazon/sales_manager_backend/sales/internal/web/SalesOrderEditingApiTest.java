package com.pedropalazon.sales_manager_backend.sales.internal.web;

import static com.pedropalazon.sales_manager_backend.sales.internal.web.SalesOrderApiTest.customizationJson;
import static com.pedropalazon.sales_manager_backend.sales.internal.web.SalesOrderApiTest.lineJson;
import static com.pedropalazon.sales_manager_backend.sales.internal.web.SalesOrderApiTest.orderJson;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalesOrderEditingApiTest {

    private static final String ORDERS = "/api/sales/orders";

    @Autowired
    private MockMvc mockMvc;

    private long fanShirtId;
    private long playerShirtId;
    private long nameAndNumberId;
    private long anaId;
    private long juanId;

    @BeforeEach
    void createCatalogAndCustomers() throws Exception {
        fanShirtId = createAndGetId("/api/catalog/products",
                "{\"name\":\"Camiseta Fan\",\"salePrice\":15.00,\"purchaseCost\":6.50}");
        playerShirtId = createAndGetId("/api/catalog/products",
                "{\"name\":\"Camiseta Player\",\"salePrice\":17.00,\"purchaseCost\":8.00}");
        nameAndNumberId = createAndGetId("/api/catalog/customizations",
                "{\"name\":\"Nombre + número\",\"salePrice\":2.00,\"purchaseCost\":1.20}");
        anaId = createAndGetId("/api/customers", "{\"name\":\"Ana Pérez\",\"phone\":\"600333444\"}");
        juanId = createAndGetId("/api/customers", "{\"name\":\"Juan García\"}");
    }

    @Test
    void givenOrdersOfTwoCustomers_whenListingByCustomer_thenReturnsOnlyHersNewestFirst() throws Exception {
        createAndGetId(ORDERS, orderJson(anaId, "\"2026-10-01\"", lineJson(fanShirtId, "M", null)));
        createAndGetId(ORDERS, orderJson(anaId, "\"2026-10-10\"", lineJson(fanShirtId, "M", null),
                lineJson(playerShirtId, "L", null)));
        createAndGetId(ORDERS, orderJson(juanId, "\"2026-10-05\"", lineJson(fanShirtId, "S", null)));

        mockMvc.perform(get(ORDERS).param("customerId", String.valueOf(anaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].orderDate").value("2026-10-10"))
                .andExpect(jsonPath("$[0].customerName").value("Ana Pérez"))
                .andExpect(jsonPath("$[0].activeLines").value(2))
                .andExpect(jsonPath("$[0].totalSale").value(32.0))
                .andExpect(jsonPath("$[0].totalProfit").value(17.5))
                .andExpect(jsonPath("$[1].orderDate").value("2026-10-01"));
        mockMvc.perform(get(ORDERS))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].customerName").value("Juan García"));
    }

    @Test
    void givenOrder_whenHeaderUpdated_thenChangesCustomerDateAndNotes() throws Exception {
        long orderId = createAndGetId(ORDERS, orderJson(anaId, "\"2026-10-01\"", lineJson(fanShirtId, "M", null)));

        mockMvc.perform(put(ORDERS + "/" + orderId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":" + juanId + ",\"orderDate\":\"2026-10-03\",\"notes\":\"Pagará en mano\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.name").value("Juan García"))
                .andExpect(jsonPath("$.orderDate").value("2026-10-03"))
                .andExpect(jsonPath("$.notes").value("Pagará en mano"));
    }

    @Test
    void givenOrderWithCustomer_whenHeaderUpdatedWithoutCustomer_thenCustomerIsRemoved() throws Exception {
        long orderId = createAndGetId(ORDERS, orderJson(anaId, null, lineJson(fanShirtId, "M", null)));

        mockMvc.perform(put(ORDERS + "/" + orderId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderDate\":\"2026-10-03\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer").doesNotExist());
    }

    @Test
    void givenOrder_whenLineAdded_thenTotalsIncludeIt() throws Exception {
        long orderId = createAndGetId(ORDERS, orderJson(anaId, null, lineJson(fanShirtId, "M", null)));

        mockMvc.perform(post(ORDERS + "/" + orderId + "/lines").contentType(MediaType.APPLICATION_JSON)
                        .content(lineJson(fanShirtId, "KIDS_24", null, customizationJson(nameAndNumberId, "LUCAS 9", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[1].id").isNumber())
                .andExpect(jsonPath("$.lines[1].sizeLabel").value("24 (niño)"))
                .andExpect(jsonPath("$.totals.sale").value(32.0))
                .andExpect(jsonPath("$.totals.activeLines").value(2));
    }

    @Test
    void givenPendingLine_whenReplaced_thenUsesNewProductSizeAndCatalogPrices() throws Exception {
        String body = createOrder(orderJson(anaId, null, lineJson(fanShirtId, "M", "10.00",
                customizationJson(nameAndNumberId, "PEDRO 10", "0"))));
        long orderId = idOf(body);
        long lineId = lineIdOf(body, 0);

        mockMvc.perform(put(ORDERS + "/" + orderId + "/lines/" + lineId).contentType(MediaType.APPLICATION_JSON)
                        .content(lineJson(playerShirtId, "L", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].id").value(lineId))
                .andExpect(jsonPath("$.lines[0].productName").value("Camiseta Player"))
                .andExpect(jsonPath("$.lines[0].size").value("L"))
                .andExpect(jsonPath("$.lines[0].unitSalePrice").value(17.0))
                .andExpect(jsonPath("$.lines[0].customizations.length()").value(0))
                .andExpect(jsonPath("$.totals.sale").value(17.0));
    }

    @Test
    void givenProductDeactivatedAfterOrdering_whenEditingLineKeepingIt_thenIsAllowed() throws Exception {
        String body = createOrder(orderJson(anaId, null, lineJson(fanShirtId, "M", null,
                customizationJson(nameAndNumberId, "PEDRO 10", null))));
        mockMvc.perform(post("/api/catalog/products/" + fanShirtId + "/deactivate")).andExpect(status().isOk());
        mockMvc.perform(post("/api/catalog/customizations/" + nameAndNumberId + "/deactivate")).andExpect(status().isOk());

        mockMvc.perform(put(ORDERS + "/" + idOf(body) + "/lines/" + lineIdOf(body, 0)).contentType(MediaType.APPLICATION_JSON)
                        .content(lineJson(fanShirtId, "L", null, customizationJson(nameAndNumberId, "PEDRO 11", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].productName").value("Camiseta Fan"))
                .andExpect(jsonPath("$.lines[0].size").value("L"))
                .andExpect(jsonPath("$.lines[0].customizations[0].customText").value("PEDRO 11"));
    }

    @Test
    void givenDeactivatedProduct_whenSwitchingALineToIt_thenReturnsConflict() throws Exception {
        String body = createOrder(orderJson(anaId, null, lineJson(fanShirtId, "M", null)));
        mockMvc.perform(post("/api/catalog/products/" + playerShirtId + "/deactivate")).andExpect(status().isOk());

        mockMvc.perform(put(ORDERS + "/" + idOf(body) + "/lines/" + lineIdOf(body, 0)).contentType(MediaType.APPLICATION_JSON)
                        .content(lineJson(playerShirtId, "L", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El producto 'Camiseta Player' está desactivado"));
    }

    @Test
    void givenTwoLines_whenOneCancelled_thenItStaysVisibleButIsExcludedFromTotals() throws Exception {
        String body = createOrder(orderJson(anaId, null, lineJson(fanShirtId, "M", null),
                lineJson(playerShirtId, "L", null)));
        long orderId = idOf(body);
        long playerLineId = lineIdOf(body, 1);

        mockMvc.perform(post(ORDERS + "/" + orderId + "/lines/" + playerLineId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.totals.sale").value(15.0))
                .andExpect(jsonPath("$.totals.activeLines").value(1));
    }

    @Test
    void givenSingleActiveLine_whenCancellingIt_thenReturnsConflict() throws Exception {
        String body = createOrder(orderJson(anaId, null, lineJson(fanShirtId, "M", null)));

        mockMvc.perform(post(ORDERS + "/" + idOf(body) + "/lines/" + lineIdOf(body, 0) + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("No se puede cancelar la última línea del pedido: cancela el pedido entero"));
    }

    @Test
    void givenOrder_whenCancelled_thenOrderAndLinesAreCancelledAndTotalsAreZero() throws Exception {
        long orderId = createAndGetId(ORDERS, orderJson(anaId, null, lineJson(fanShirtId, "M", null),
                lineJson(playerShirtId, "L", null)));

        mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.lines[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.lines[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.totals.sale").value(0.0))
                .andExpect(jsonPath("$.totals.activeLines").value(0));
    }

    @Test
    void givenCancelledOrder_whenEditingOrAddingLines_thenReturnsConflict() throws Exception {
        long orderId = createAndGetId(ORDERS, orderJson(anaId, null, lineJson(fanShirtId, "M", null)));
        mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel")).andExpect(status().isOk());

        mockMvc.perform(put(ORDERS + "/" + orderId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":" + anaId + ",\"orderDate\":\"2026-10-03\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("está cancelado y no se puede modificar")));
        mockMvc.perform(post(ORDERS + "/" + orderId + "/lines").contentType(MediaType.APPLICATION_JSON)
                        .content(lineJson(fanShirtId, "M", null)))
                .andExpect(status().isConflict());
        mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    void givenLineOfAnotherOrder_whenCancelling_thenReturnsNotFound() throws Exception {
        String first = createOrder(orderJson(anaId, null, lineJson(fanShirtId, "M", null), lineJson(fanShirtId, "L", null)));
        String second = createOrder(orderJson(juanId, null, lineJson(fanShirtId, "S", null)));

        mockMvc.perform(post(ORDERS + "/" + idOf(first) + "/lines/" + lineIdOf(second, 0) + "/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("No existe la línea " + lineIdOf(second, 0))));
    }

    private String createOrder(String json) throws Exception {
        return mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private long createAndGetId(String url, String json) throws Exception {
        String body = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    private static long idOf(String body) {
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static long lineIdOf(String body, int index) {
        return ((Number) JsonPath.read(body, "$.lines[" + index + "].id")).longValue();
    }
}
