package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// @Transactional: cada test deshace sus cambios en BD al terminar
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductApiTest {

    private static final String PRODUCTS = "/api/catalog/products";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenValidProduct_whenCreated_thenReturnsCreatedWithLocationAndBody() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Camiseta Fan", "15.00", "6.50")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(PRODUCTS + "/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Camiseta Fan"))
                .andExpect(jsonPath("$.salePrice").value(15.0))
                .andExpect(jsonPath("$.purchaseCost").value(6.5))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void givenExistingName_whenCreatingWithOtherCaseAndSpaces_thenReturnsConflict() throws Exception {
        create("Camiseta Fan", "15.00", "6.50");

        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("  camiseta fan ", "16.00", "7.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe un producto llamado 'camiseta fan'"));
    }

    @Test
    void givenPriceWithThreeDecimals_whenCreated_thenReturnsBadRequestWithFieldError() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Camiseta Fan", "15.999", "6.50")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'salePrice')].message")
                        .value("El precio de venta admite como máximo 8 enteros y 2 decimales"));
    }

    @Test
    void givenTooLargePrice_whenCreated_thenReturnsBadRequest() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Camiseta Fan", "123456789.00", "6.50")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("salePrice"));
    }

    @Test
    void givenEmptyBody_whenCreated_thenReturnsOneErrorPerRequiredField() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.errors[?(@.field == 'purchaseCost')].message").value("El coste es obligatorio"));
    }

    @Test
    void givenUnknownId_whenRequested_thenReturnsNotFound() throws Exception {
        mockMvc.perform(get(PRODUCTS + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el producto con id 999999"));
    }

    @Test
    void givenNonNumericId_whenRequested_thenReturnsBadRequestInSpanish() throws Exception {
        mockMvc.perform(get(PRODUCTS + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"));
    }

    @Test
    void givenExistingProduct_whenUpdatedKeepingItsName_thenReturnsNewPrices() throws Exception {
        long id = create("Camiseta Player", "17.00", "8.00");

        mockMvc.perform(put(PRODUCTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Camiseta Player", "18.00", "8.75")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Camiseta Player"))
                .andExpect(jsonPath("$.salePrice").value(18.0))
                .andExpect(jsonPath("$.purchaseCost").value(8.75));
    }

    @Test
    void givenTwoProducts_whenRenamingOneToTheOthersName_thenReturnsConflict() throws Exception {
        create("Camiseta Fan", "15.00", "6.50");
        long playerId = create("Camiseta Player", "17.00", "8.00");

        mockMvc.perform(put(PRODUCTS + "/" + playerId).contentType(MediaType.APPLICATION_JSON)
                        .content(json("CAMISETA FAN", "17.00", "8.00")))
                .andExpect(status().isConflict());
    }

    @Test
    void givenDeactivatedProduct_whenListing_thenOnlyAppearsWhenIncludingInactive() throws Exception {
        create("Camiseta Fan", "15.00", "6.50");
        long tracksuitId = create("Chándal", "50.00", "34.00");

        mockMvc.perform(post(PRODUCTS + "/" + tracksuitId + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get(PRODUCTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(contains("Camiseta Fan")));
        mockMvc.perform(get(PRODUCTS).param("includeInactive", "true"))
                .andExpect(jsonPath("$[*].name").value(contains("Camiseta Fan", "Chándal")));
        mockMvc.perform(get(PRODUCTS + "/" + tracksuitId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void givenDeactivatedProduct_whenActivated_thenIsActiveAgain() throws Exception {
        long id = create("Chándal", "50.00", "34.00");
        mockMvc.perform(post(PRODUCTS + "/" + id + "/deactivate")).andExpect(status().isOk());

        mockMvc.perform(post(PRODUCTS + "/" + id + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    private long create(String name, String salePrice, String purchaseCost) throws Exception {
        String body = mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(json(name, salePrice, purchaseCost)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String json(String name, String salePrice, String purchaseCost) {
        return """
                {"name":"%s","salePrice":%s,"purchaseCost":%s}""".formatted(name, salePrice, purchaseCost);
    }
}
