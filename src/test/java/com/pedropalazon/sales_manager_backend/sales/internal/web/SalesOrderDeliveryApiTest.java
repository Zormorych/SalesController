package com.pedropalazon.sales_manager_backend.sales.internal.web;

import static com.pedropalazon.sales_manager_backend.sales.internal.web.SalesOrderApiTest.lineJson;
import static com.pedropalazon.sales_manager_backend.sales.internal.web.SalesOrderApiTest.orderJson;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pedropalazon.sales_manager_backend.sales.SalesOrderApi;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Las camisetas se dan por pedidas y recibidas con la API de ventas, como hará la recepción del lote
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalesOrderDeliveryApiTest {

    private static final String ORDERS = "/api/sales/orders";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SalesOrderApi salesOrderApi;

    private long productId;
    private long customerId;

    @BeforeEach
    void createProductAndCustomer() throws Exception {
        productId = idOf(postJson("/api/catalog/products", "{\"name\":\"Camiseta Fan\",\"salePrice\":15.00,\"purchaseCost\":6.50}"));
        customerId = idOf(postJson("/api/customers", "{\"name\":\"Ana Pérez\"}"));
    }

    @Test
    void givenReceivedShirts_whenDeliveredOneByOne_thenProgressGoesToReadyAndThenCompleted() throws Exception {
        String order = createOrder(2);
        List<Long> lines = lineIdsOf(order);
        orderAndReceive(lines);

        mockMvc.perform(post(ORDERS + "/" + idOf(order) + "/lines/" + lines.get(0) + "/deliver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[*].status").value(contains("DELIVERED", "RECEIVED")))
                .andExpect(jsonPath("$.progress").value("READY_TO_DELIVER"))
                .andExpect(jsonPath("$.lineCounts.delivered").value(1));
        mockMvc.perform(post(ORDERS + "/" + idOf(order) + "/lines/" + lines.get(1) + "/deliver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value("COMPLETED"));
    }

    @Test
    void givenSomeShirtsReceived_whenDeliveringTheWholeOrder_thenDeliversOnlyTheReceivedOnes() throws Exception {
        String order = createOrder(3);
        List<Long> lines = lineIdsOf(order);
        salesOrderApi.markLinesOrdered(lines);
        salesOrderApi.receiveLines(lines.subList(0, 2), List.of());

        mockMvc.perform(post(ORDERS + "/" + idOf(order) + "/deliver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[*].status").value(contains("DELIVERED", "DELIVERED", "ORDERED")))
                .andExpect(jsonPath("$.progress").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.lineCounts.ordered").value(1));
    }

    @Test
    void givenShirtNotReceived_whenDelivered_thenReturnsConflict() throws Exception {
        String order = createOrder(1);
        long lineId = lineIdsOf(order).get(0);

        mockMvc.perform(post(ORDERS + "/" + idOf(order) + "/lines/" + lineId + "/deliver"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La línea " + lineId + " todavía no se ha recibido"));
    }

    @Test
    void givenCancelledShirt_whenDelivered_thenConflictSaysItIsCancelled() throws Exception {
        String order = createOrder(2);
        long lineId = lineIdsOf(order).get(1);
        mockMvc.perform(post(ORDERS + "/" + idOf(order) + "/lines/" + lineId + "/cancel")).andExpect(status().isOk());

        mockMvc.perform(post(ORDERS + "/" + idOf(order) + "/lines/" + lineId + "/deliver"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La línea " + lineId + " está cancelada"));
    }

    @Test
    void givenDeliveredShirt_whenDeliveredAgain_thenReturnsConflict() throws Exception {
        String order = createOrder(1);
        long lineId = lineIdsOf(order).get(0);
        orderAndReceive(List.of(lineId));
        mockMvc.perform(post(ORDERS + "/" + idOf(order) + "/lines/" + lineId + "/deliver")).andExpect(status().isOk());

        mockMvc.perform(post(ORDERS + "/" + idOf(order) + "/lines/" + lineId + "/deliver"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La línea " + lineId + " ya está entregada"));
    }

    @Test
    void givenOrderWithoutReceivedShirts_whenDeliveringTheWholeOrder_thenReturnsConflict() throws Exception {
        String order = createOrder(2);

        mockMvc.perform(post(ORDERS + "/" + idOf(order) + "/deliver"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no tiene camisetas recibidas pendientes de entregar")));
    }

    private void orderAndReceive(List<Long> lineIds) {
        salesOrderApi.markLinesOrdered(lineIds);
        salesOrderApi.receiveLines(lineIds, List.of());
    }

    private String createOrder(int lineCount) throws Exception {
        String[] lines = new String[lineCount];
        for (int i = 0; i < lineCount; i++) {
            lines[i] = lineJson(productId, "M", null);
        }
        return postJson(ORDERS, orderJson(customerId, null, lines));
    }

    private String postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static long idOf(String body) {
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static List<Long> lineIdsOf(String orderBody) {
        List<Number> ids = JsonPath.read(orderBody, "$.lines[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}
