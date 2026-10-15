package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SizeController.class)
class SizeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenSizeCatalog_whenListed_thenReturnsAdultAndKidsSizesInOrder() throws Exception {
        mockMvc.perform(get("/api/catalog/sizes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].code").value("S"))
                .andExpect(jsonPath("$[0].label").value("S"))
                .andExpect(jsonPath("$[4].code").value("XXL"))
                .andExpect(jsonPath("$[5].code").value("KIDS_16"))
                .andExpect(jsonPath("$[5].label").value("16 (niño)"))
                .andExpect(jsonPath("$[11].code").value("KIDS_28"));
    }
}
