package com.pedropalazon.sales_manager_backend.resale.internal.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
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
class ResaleItemApiTest {

    private static final String VINTED_JACKET = """
            {"name":"Chaqueta Nike vintage","size":"L","notes":"Vinted","purchaseCost":11.70,
             "purchaseDate":"2026-10-02","askingPrice":20.00}""";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenVintedJacket_whenCreatedAndSold_thenProfitIsSalePriceMinusCost() throws Exception {
        mockMvc.perform(postJson("/api/resale/items", VINTED_JACKET))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Chaqueta Nike vintage"))
                .andExpect(jsonPath("$.size").value("L"))
                .andExpect(jsonPath("$.origin").value("MANUAL"))
                .andExpect(jsonPath("$.purchaseCost").value(11.70))
                .andExpect(jsonPath("$.askingPrice").value(20.00))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.profit").value(nullValue()));
        long id = createItem(VINTED_JACKET);

        sell(id, "{\"salePrice\":17.70,\"saleDate\":\"2026-10-15\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.salePrice").value(17.70))
                .andExpect(jsonPath("$.saleDate").value("2026-10-15"))
                .andExpect(jsonPath("$.profit").value(6.00));
    }

    @Test
    void givenSoldItem_whenSoldAgain_thenConflict() throws Exception {
        long id = createItem(VINTED_JACKET);
        sell(id, "{\"salePrice\":17.70}").andExpect(status().isOk());

        sell(id, "{\"salePrice\":18.00}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("ya está vendida")));
    }

    @Test
    void givenAvailableItem_whenUnsold_thenConflict() throws Exception {
        long id = createItem(VINTED_JACKET);

        mockMvc.perform(post("/api/resale/items/{id}/unsell", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("no está vendida")));
    }

    @Test
    void givenSoldItem_whenUnsold_thenAvailableWithoutSaleData() throws Exception {
        long id = createItem(VINTED_JACKET);
        sell(id, "{\"salePrice\":17.70}").andExpect(status().isOk());

        mockMvc.perform(post("/api/resale/items/{id}/unsell", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.salePrice").value(nullValue()))
                .andExpect(jsonPath("$.saleDate").value(nullValue()))
                .andExpect(jsonPath("$.profit").value(nullValue()));
    }

    @Test
    void givenNoPurchaseOrSaleDate_whenCreatingAndSelling_thenTodayIsUsed() throws Exception {
        String today = LocalDate.now().toString();
        mockMvc.perform(postJson("/api/resale/items", "{\"name\":\"Sudadera Adidas\",\"purchaseCost\":8}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseDate").value(today))
                .andExpect(jsonPath("$.askingPrice").value(nullValue()));
        long id = createItem("{\"name\":\"Sudadera Adidas\",\"purchaseCost\":8}");

        sell(id, "{\"salePrice\":12}").andExpect(jsonPath("$.saleDate").value(today));
    }

    @Test
    void givenItem_whenUpdated_thenKeepsStatusAndSaleData() throws Exception {
        long id = createItem(VINTED_JACKET);
        sell(id, "{\"salePrice\":17.70}").andExpect(status().isOk());

        mockMvc.perform(put("/api/resale/items/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Chaqueta Nike vintage 90s\",\"purchaseCost\":10.70,\"askingPrice\":19}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chaqueta Nike vintage 90s"))
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.salePrice").value(17.70))
                .andExpect(jsonPath("$.profit").value(7.00));
    }

    @Test
    void givenUpdateWithoutPurchaseDate_whenUpdating_thenKeepsTheOriginalDate() throws Exception {
        long id = createItem(VINTED_JACKET);

        mockMvc.perform(put("/api/resale/items/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Chaqueta Nike vintage\",\"purchaseCost\":11.70,\"askingPrice\":18}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseDate").value("2026-10-02"))
                .andExpect(jsonPath("$.askingPrice").value(18.00));
    }

    @Test
    void givenBlankNameOrNegativeCost_whenCreating_thenBadRequestOnField() throws Exception {
        mockMvc.perform(postJson("/api/resale/items", "{\"name\":\" \",\"purchaseCost\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        mockMvc.perform(postJson("/api/resale/items", "{\"name\":\"Gorra\",\"purchaseCost\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("purchaseCost"));
    }

    @Test
    void givenItems_whenListingByStatus_thenOnlyThatStatus() throws Exception {
        long sold = createItem(VINTED_JACKET);
        sell(sold, "{\"salePrice\":17.70}").andExpect(status().isOk());
        long available = createItem("{\"name\":\"Sudadera Adidas\",\"purchaseCost\":8}");

        mockMvc.perform(get("/api/resale/items").param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(available));
        mockMvc.perform(get("/api/resale/items"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(available));
    }

    @Test
    void givenUnknownId_whenGetting_thenNotFound() throws Exception {
        mockMvc.perform(get("/api/resale/items/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe la prenda con id 999999"));
    }

    private ResultActions sell(long id, String json) throws Exception {
        return mockMvc.perform(postJson("/api/resale/items/" + id + "/sell", json));
    }

    private long createItem(String json) throws Exception {
        String body = mockMvc.perform(postJson("/api/resale/items", json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(String url,
            String json) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
