package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomizationApiTest {

    private static final String CUSTOMIZATIONS = "/api/catalog/customizations";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenValidCustomization_whenCreated_thenReturnsCreatedWithPrices() throws Exception {
        mockMvc.perform(post(CUSTOMIZATIONS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Nombre + número", "2.00", "1.20")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nombre + número"))
                .andExpect(jsonPath("$.salePrice").value(2.0))
                .andExpect(jsonPath("$.purchaseCost").value(1.2))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void givenExistingName_whenCreatingDuplicate_thenReturnsConflict() throws Exception {
        create("Parche", "1.00", "0.50");

        mockMvc.perform(post(CUSTOMIZATIONS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("parche", "1.50", "0.60")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una personalización llamada 'parche'"));
    }

    @Test
    void givenUnknownId_whenRequested_thenReturnsNotFound() throws Exception {
        mockMvc.perform(get(CUSTOMIZATIONS + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe la personalización con id 999999"));
    }

    @Test
    void givenDeactivatedCustomization_whenListing_thenIsHiddenByDefault() throws Exception {
        create("Nombre + número", "2.00", "1.20");
        long patchId = create("Parche", "1.00", "0.50");
        mockMvc.perform(post(CUSTOMIZATIONS + "/" + patchId + "/deactivate")).andExpect(status().isOk());

        mockMvc.perform(get(CUSTOMIZATIONS))
                .andExpect(jsonPath("$[*].name").value(contains("Nombre + número")));
        mockMvc.perform(get(CUSTOMIZATIONS).param("includeInactive", "true"))
                .andExpect(jsonPath("$[*].name").value(contains("Nombre + número", "Parche")));
    }

    @Test
    void givenProductWithSameName_whenCreatingCustomization_thenIsAllowed() throws Exception {
        mockMvc.perform(post("/api/catalog/products").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Parche", "1.00", "0.50")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(CUSTOMIZATIONS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Parche", "1.00", "0.50")))
                .andExpect(status().isCreated());
    }

    private long create(String name, String salePrice, String purchaseCost) throws Exception {
        String body = mockMvc.perform(post(CUSTOMIZATIONS).contentType(MediaType.APPLICATION_JSON)
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
