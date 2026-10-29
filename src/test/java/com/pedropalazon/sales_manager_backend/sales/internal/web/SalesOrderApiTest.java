package com.pedropalazon.sales_manager_backend.sales.internal.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
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

// Los datos de otros módulos (producto, personalización, cliente) se crean por su API, como haría el frontend
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalesOrderApiTest {

    private static final String ORDERS = "/api/sales/orders";

    @Autowired
    private MockMvc mockMvc;

    private long fanShirtId;
    private long nameAndNumberId;
    private long anaId;

    @BeforeEach
    void createCatalogAndCustomer() throws Exception {
        fanShirtId = createAndGetId("/api/catalog/products",
                "{\"name\":\"Camiseta Fan\",\"salePrice\":15.00,\"purchaseCost\":6.50}");
        nameAndNumberId = createAndGetId("/api/catalog/customizations",
                "{\"name\":\"Nombre + número\",\"salePrice\":2.00,\"purchaseCost\":1.20}");
        anaId = createAndGetId("/api/customers", "{\"name\":\"Ana Pérez\",\"phone\":\"600333444\"}");
    }

    @Test
    void givenCatalogPrices_whenOrderCreated_thenCopiesPricesAndComputesTotals() throws Exception {
        createOrder(orderJson(anaId, "\"2026-10-15\"", lineJson(fanShirtId, "M", null,
                customizationJson(nameAndNumberId, "WILLIAMS JR 17", null))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(ORDERS + "/")))
                .andExpect(jsonPath("$.orderNumber").value(startsWith("SO-2026-")))
                .andExpect(jsonPath("$.orderDate").value("2026-10-15"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.customer.name").value("Ana Pérez"))
                .andExpect(jsonPath("$.lines[0].productName").value("Camiseta Fan"))
                .andExpect(jsonPath("$.lines[0].kitName").value("Selección Española blanca 2 estrellas"))
                .andExpect(jsonPath("$.lines[0].size").value("M"))
                .andExpect(jsonPath("$.lines[0].status").value("PENDING"))
                .andExpect(jsonPath("$.lines[0].unitSalePrice").value(15.0))
                .andExpect(jsonPath("$.lines[0].unitPurchaseCost").value(6.5))
                .andExpect(jsonPath("$.lines[0].customizations[0].customizationName").value("Nombre + número"))
                .andExpect(jsonPath("$.lines[0].customizations[0].customText").value("WILLIAMS JR 17"))
                .andExpect(jsonPath("$.lines[0].customizations[0].salePrice").value(2.0))
                .andExpect(jsonPath("$.lines[0].totalSale").value(17.0))
                .andExpect(jsonPath("$.totals.sale").value(17.0))
                .andExpect(jsonPath("$.totals.cost").value(7.7))
                .andExpect(jsonPath("$.totals.profit").value(9.3))
                .andExpect(jsonPath("$.totals.activeLines").value(1));
    }

    @Test
    void givenZeroPriceOverride_whenOrderCreated_thenKeepsZeroInsteadOfCatalogPrice() throws Exception {
        createOrder(orderJson(anaId, null, lineJson(fanShirtId, "L", null,
                customizationJson(nameAndNumberId, "PEDRO 10", "0"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].customizations[0].salePrice").value(0.0))
                .andExpect(jsonPath("$.lines[0].customizations[0].purchaseCost").value(1.2))
                .andExpect(jsonPath("$.totals.sale").value(15.0))
                .andExpect(jsonPath("$.totals.cost").value(7.7))
                .andExpect(jsonPath("$.totals.profit").value(7.3));
    }

    @Test
    void givenLineSalePriceOverride_whenOrderCreated_thenUsesThatPrice() throws Exception {
        createOrder(orderJson(anaId, null, lineJson(fanShirtId, "S", "12.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].unitSalePrice").value(12.5))
                .andExpect(jsonPath("$.lines[0].unitPurchaseCost").value(6.5));
    }

    @Test
    void givenNoOrderDate_whenOrderCreated_thenUsesToday() throws Exception {
        createOrder(orderJson(anaId, null, lineJson(fanShirtId, "M", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderDate").value(LocalDate.now().toString()));
    }

    @Test
    void givenCatalogPriceChangedAfterOrder_whenOrderRead_thenKeepsCopiedNameAndPrice() throws Exception {
        long orderId = createOrderAndGetId(orderJson(anaId, null, lineJson(fanShirtId, "M", null)));
        mockMvc.perform(put("/api/catalog/products/" + fanShirtId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Camiseta Fan 2027\",\"salePrice\":16.00,\"purchaseCost\":7.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(ORDERS + "/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].productName").value("Camiseta Fan"))
                .andExpect(jsonPath("$.lines[0].unitSalePrice").value(15.0));
    }

    // Un pedido para uno mismo no necesita cliente
    @Test
    void givenNoCustomer_whenOrderCreated_thenIsCreatedWithoutCustomerAndListedAndPendingToOrder() throws Exception {
        String body = "{\"lines\":[" + lineJson(fanShirtId, "M", null) + "]}";
        String created = createOrder(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andExpect(jsonPath("$.totals.sale").value(15.0))
                .andReturn().getResponse().getContentAsString();
        long orderId = ((Number) JsonPath.read(created, "$.id")).longValue();

        mockMvc.perform(get(ORDERS + "/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer").doesNotExist());
        mockMvc.perform(get(ORDERS))
                .andExpect(jsonPath("$[?(@.id == " + orderId + ")].customerId").value(contains((Object) null)));
        mockMvc.perform(get("/api/purchasing/pending-lines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.orderId == " + orderId + ")].customerName").value(contains((Object) null)));
    }

    @Test
    void givenNoLines_whenOrderCreated_thenReturnsBadRequestOnLines() throws Exception {
        createOrder("{\"customerId\":" + anaId + ",\"lines\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines"))
                .andExpect(jsonPath("$.errors[0].message").value("El pedido necesita al menos una línea"));
    }

    @Test
    void givenLineWithoutKitName_whenOrderCreated_thenErrorPointsToTheNestedField() throws Exception {
        createOrder("{\"customerId\":" + anaId + ",\"lines\":[{\"productId\":" + fanShirtId + ",\"size\":\"M\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].kitName"))
                .andExpect(jsonPath("$.errors[0].message").value("La equipación es obligatoria"));
    }

    @Test
    void givenNullLineOrCustomization_whenOrderCreated_thenReturnsBadRequestInsteadOfServerError() throws Exception {
        createOrder("{\"customerId\":" + anaId + ",\"lines\":[null]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(startsWith("lines[0]")))
                .andExpect(jsonPath("$.errors[0].message").value("La línea no puede estar vacía"));
        createOrder(orderJson(anaId, null, lineJson(fanShirtId, "M", null, "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("La personalización no puede estar vacía"));
    }

    @Test
    void givenUnknownSize_whenOrderCreated_thenReturnsBadRequestInSpanish() throws Exception {
        createOrder(orderJson(anaId, null, lineJson(fanShirtId, "XXXL", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"));
    }

    @Test
    void givenDeactivatedProduct_whenOrderCreated_thenReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/catalog/products/" + fanShirtId + "/deactivate")).andExpect(status().isOk());

        createOrder(orderJson(anaId, null, lineJson(fanShirtId, "M", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El producto 'Camiseta Fan' está desactivado"));
    }

    @Test
    void givenUnknownCustomer_whenOrderCreated_thenReturnsNotFound() throws Exception {
        createOrder(orderJson(999999L, null, lineJson(fanShirtId, "M", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el cliente con id 999999"));
    }

    @Test
    void givenUnknownOrder_whenRequested_thenReturnsNotFound() throws Exception {
        mockMvc.perform(get(ORDERS + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el pedido con id 999999"));
    }

    private ResultActions createOrder(String json) throws Exception {
        return mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createOrderAndGetId(String json) throws Exception {
        String body = createOrder(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createAndGetId(String url, String json) throws Exception {
        String body = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    static String orderJson(long customerId, String orderDate, String... lines) {
        return "{\"customerId\":%d,\"orderDate\":%s,\"lines\":[%s]}"
                .formatted(customerId, orderDate == null ? "null" : orderDate, String.join(",", lines));
    }

    static String lineJson(long productId, String size, String unitSalePrice, String... customizations) {
        return ("{\"productId\":%d,\"kitName\":\"Selección Española blanca 2 estrellas\",\"size\":\"%s\","
                + "\"unitSalePrice\":%s,\"customizations\":[%s]}")
                .formatted(productId, size, unitSalePrice == null ? "null" : unitSalePrice,
                        String.join(",", customizations));
    }

    static String customizationJson(long customizationId, String customText, String salePrice) {
        return "{\"customizationId\":%d,\"customText\":\"%s\",\"salePrice\":%s}"
                .formatted(customizationId, customText, salePrice == null ? "null" : salePrice);
    }
}
