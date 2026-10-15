package com.pedropalazon.sales_manager_backend.payments.internal.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentApiTest {

    @Autowired
    private MockMvc mockMvc;

    private long orderId;

    // Pedido de 17,00 €: Camiseta Fan (15,00) + Nombre y número (2,00)
    @BeforeEach
    void createOrderOfSeventeenEuros() throws Exception {
        long productId = createAndGetId("/api/catalog/products",
                "{\"name\":\"Camiseta Fan\",\"salePrice\":15.00,\"purchaseCost\":6.50}");
        long customizationId = createAndGetId("/api/catalog/customizations",
                "{\"name\":\"Nombre + número\",\"salePrice\":2.00,\"purchaseCost\":1.20}");
        long customerId = createAndGetId("/api/customers", "{\"name\":\"Ana Pérez\"}");
        orderId = createAndGetId("/api/sales/orders", ("{\"customerId\":%d,\"lines\":[{\"productId\":%d,"
                + "\"kitName\":\"Selección Española blanca\",\"size\":\"M\","
                + "\"customizations\":[{\"customizationId\":%d,\"customText\":\"WILLIAMS JR 17\"}]}]}")
                .formatted(customerId, productId, customizationId));
    }

    @Test
    void givenOrder_whenPaidInTwoParts_thenPendingReachesZero() throws Exception {
        pay("{\"amount\":10.00,\"paymentDate\":\"2026-10-15\",\"notes\":\"Señal por Bizum\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderTotal").value(17.0))
                .andExpect(jsonPath("$.paidAmount").value(10.0))
                .andExpect(jsonPath("$.pendingAmount").value(7.0))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].notes").value("Señal por Bizum"))
                .andExpect(jsonPath("$.payments[0].status").value("REGISTERED"));

        pay("{\"amount\":7.00}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paidAmount").value(17.0))
                .andExpect(jsonPath("$.pendingAmount").value(0.0))
                .andExpect(jsonPath("$.payments.length()").value(2));
    }

    @Test
    void givenFullyPaidOrder_whenPayingMore_thenReturnsConflictAlreadyPaid() throws Exception {
        pay("{\"amount\":17.00}").andExpect(status().isCreated());

        pay("{\"amount\":0.01}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("ya está pagado")));
    }

    @Test
    void givenOrder_whenPaymentExceedsPending_thenReturnsConflictWithBothAmounts() throws Exception {
        pay("{\"amount\":20.00}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(allOf(
                        containsString("El pago de 20,00 € supera lo pendiente del pedido SO-"),
                        containsString("(17,00 €)"))));
    }

    @Test
    void givenZeroOrNegativeAmount_whenPaying_thenReturnsBadRequestOnAmount() throws Exception {
        pay("{\"amount\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message").value("El importe debe ser mayor que 0"));
        pay("{\"amount\":-5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    void givenUnknownOrder_whenPayingOrReadingSummary_thenReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/sales/orders/999999/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el pedido con id 999999"));
        mockMvc.perform(get("/api/sales/orders/999999/payments"))
                .andExpect(status().isNotFound());
    }

    @Test
    void givenCancelledOrderWithPayment_whenPayingOrReading_thenRejectsAndShowsAmountToRefund() throws Exception {
        pay("{\"amount\":10.00}").andExpect(status().isCreated());
        mockMvc.perform(post("/api/sales/orders/" + orderId + "/cancel")).andExpect(status().isOk());

        pay("{\"amount\":5.00}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("está cancelado y no admite cobros")));
        mockMvc.perform(get("/api/sales/orders/" + orderId + "/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCancelled").value(true))
                .andExpect(jsonPath("$.orderTotal").value(0.0))
                .andExpect(jsonPath("$.paidAmount").value(10.0))
                .andExpect(jsonPath("$.pendingAmount").value(-10.0));
    }

    @Test
    void givenWrongPayment_whenVoided_thenStopsCountingAndFreesThatAmount() throws Exception {
        long paymentId = paymentIdOf(pay("{\"amount\":10.00}").andExpect(status().isCreated()), 0);

        mockMvc.perform(post("/api/sales/orders/" + orderId + "/payments/" + paymentId + "/void"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments[0].status").value("VOIDED"))
                .andExpect(jsonPath("$.paidAmount").value(0.0))
                .andExpect(jsonPath("$.pendingAmount").value(17.0));

        pay("{\"amount\":17.00}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paidAmount").value(17.0))
                .andExpect(jsonPath("$.payments.length()").value(2));
    }

    @Test
    void givenVoidedPayment_whenVoidingAgain_thenReturnsConflict() throws Exception {
        long paymentId = paymentIdOf(pay("{\"amount\":10.00}").andExpect(status().isCreated()), 0);
        mockMvc.perform(post("/api/sales/orders/" + orderId + "/payments/" + paymentId + "/void"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sales/orders/" + orderId + "/payments/" + paymentId + "/void"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El pago " + paymentId + " ya está anulado"));
    }

    @Test
    void givenPaymentOfAnotherOrder_whenVoiding_thenReturnsNotFound() throws Exception {
        long paymentId = paymentIdOf(pay("{\"amount\":10.00}").andExpect(status().isCreated()), 0);

        mockMvc.perform(post("/api/sales/orders/999999/payments/" + paymentId + "/void"))
                .andExpect(status().isNotFound());
    }

    @Test
    void givenNoPaymentDate_whenPaying_thenUsesToday() throws Exception {
        pay("{\"amount\":5.00}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payments[0].paymentDate").value(LocalDate.now().toString()));
    }

    private ResultActions pay(String json) throws Exception {
        return mockMvc.perform(post("/api/sales/orders/" + orderId + "/payments")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long paymentIdOf(ResultActions result, int index) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.payments[" + index + "].id")).longValue();
    }

    private long createAndGetId(String url, String json) throws Exception {
        String body = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
