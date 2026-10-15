package com.pedropalazon.sales_manager_backend.customers.internal.web;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerApiTest {

    private static final String CUSTOMERS = "/api/customers";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenValidCustomer_whenCreated_thenReturnsCreatedWithNormalizedPhone() throws Exception {
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana Pérez", "+34 600-123-456", "Pide siempre talla M")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(CUSTOMERS + "/")))
                .andExpect(jsonPath("$.name").value("Ana Pérez"))
                .andExpect(jsonPath("$.phone").value("+34600123456"))
                .andExpect(jsonPath("$.notes").value("Pide siempre talla M"));
    }

    @Test
    void givenEmptyPhone_whenCreatingTwoCustomers_thenBothAreCreatedWithoutPhone() throws Exception {
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content(json("Ana Pérez", "", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").doesNotExist());
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content(json("Juan García", "", null)))
                .andExpect(status().isCreated());
    }

    @Test
    void givenExistingPhone_whenCreatingWithOtherFormat_thenReturnsConflictNamingTheOwner() throws Exception {
        create("Ana Pérez", "+34600123456");

        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Otra Persona", "+34 600 123 456", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El teléfono +34600123456 ya pertenece al cliente Ana Pérez"));
    }

    @Test
    void givenInvalidPhone_whenCreated_thenReturnsBadRequestOnPhone() throws Exception {
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content(json("Ana Pérez", "abc", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phone"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("El teléfono solo puede tener dígitos, espacios, guiones, puntos, paréntesis y un + inicial"));
    }

    @Test
    void givenPhoneWithOnlySeparators_whenCreated_thenReturnsBadRequestOnPhone() throws Exception {
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content(json("Ana Pérez", "------", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content(json("Ana Pérez", "+(((((", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenMissingName_whenCreated_thenReturnsBadRequestOnName() throws Exception {
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content(json(" ", "600111222", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("El nombre es obligatorio"));
    }

    @Test
    void givenCustomers_whenSearchingByNameOrPhone_thenReturnsMatchesSortedByName() throws Exception {
        create("Mariano López", "600111222");
        create("Ana Pérez", "600333444");
        create("Juan García", "600555666");

        mockMvc.perform(get(CUSTOMERS).param("search", "EZ"))
                .andExpect(jsonPath("$[*].name").value(contains("Ana Pérez", "Mariano López")));
        mockMvc.perform(get(CUSTOMERS).param("search", "555 666"))
                .andExpect(jsonPath("$[*].name").value(contains("Juan García")));
        mockMvc.perform(get(CUSTOMERS))
                .andExpect(jsonPath("$[*].name").value(contains("Ana Pérez", "Juan García", "Mariano López")));
    }

    @Test
    void givenExistingCustomer_whenUpdatedKeepingItsPhone_thenReturnsUpdatedData() throws Exception {
        long id = create("Ana Pérez", "600333444");

        mockMvc.perform(put(CUSTOMERS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana Pérez Ruiz", "600 333 444", "Cliente habitual")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Pérez Ruiz"))
                .andExpect(jsonPath("$.phone").value("600333444"))
                .andExpect(jsonPath("$.notes").value("Cliente habitual"));
    }

    @Test
    void givenTwoCustomers_whenGivingOneTheOthersPhone_thenReturnsConflict() throws Exception {
        create("Ana Pérez", "600333444");
        long juanId = create("Juan García", "600555666");

        mockMvc.perform(put(CUSTOMERS + "/" + juanId).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Juan García", "600-333-444", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void givenUnknownId_whenRequested_thenReturnsNotFound() throws Exception {
        mockMvc.perform(get(CUSTOMERS + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el cliente con id 999999"));
    }

    private long create(String name, String phone) throws Exception {
        String body = mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON)
                        .content(json(name, phone, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String json(String name, String phone, String notes) {
        return """
                {"name":%s,"phone":%s,"notes":%s}""".formatted(quote(name), quote(phone), quote(notes));
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
