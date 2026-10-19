package com.pedropalazon.sales_manager_backend.resale.internal.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import com.pedropalazon.sales_manager_backend.catalog.internal.CustomizationService;
import com.pedropalazon.sales_manager_backend.catalog.internal.ProductService;
import com.pedropalazon.sales_manager_backend.customers.internal.CustomerService;
import com.pedropalazon.sales_manager_backend.purchasing.internal.FactoryBatchService;
import com.pedropalazon.sales_manager_backend.sales.internal.OrderLineDraft;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderService;
import com.pedropalazon.sales_manager_backend.sales.internal.SalesOrderView;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class ResaleFromOrderLineApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomizationService customizationService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private FactoryBatchService batchService;

    private SalesOrderView.Details order;
    private Long player;
    private Long nameAndNumber;
    private Long customerId;
    private Long personalizedLine;
    private Long patchLine;
    private Long notReceivedLine;

    // Pedido con 3 camisetas recibidas en un lote de 4,50 € (1,50 cada una) y una cuarta aún sin pedir.
    // La primera: Player 17,00 / 7,70 + "Nombre + número" 2,00 / 1,20 con "WILLIAMS JR 17"
    @BeforeEach
    void createReceivedShirts() {
        player = productService.create("Camiseta Player", new BigDecimal("17.00"), new BigDecimal("7.70")).getId();
        nameAndNumber = customizationService.create("Nombre + número", new BigDecimal("2.00"),
                new BigDecimal("1.20")).getId();
        Long patch = customizationService.create("Parche", new BigDecimal("1.00"), new BigDecimal("0.50")).getId();
        customerId = customerService.create("Ana Pérez", null, null).getId();
        OrderLineDraft personalized = new OrderLineDraft(player, "Selección Española blanca", ClothingSize.M, null, null,
                List.of(new OrderLineDraft.Customization(nameAndNumber, "WILLIAMS JR 17", null, null)));
        OrderLineDraft withPatch = new OrderLineDraft(player, "Real Madrid local", ClothingSize.L, null, null,
                List.of(new OrderLineDraft.Customization(patch, null, null, null)));
        OrderLineDraft plain = new OrderLineDraft(player, "Betis local", ClothingSize.S, null, null, List.of());
        order = salesOrderService.create(customerId, LocalDate.of(2026, 10, 5), null,
                List.of(personalized, withPatch, plain, plain));
        List<Long> lines = order.lines().stream().map(SalesOrderView.Line::id).toList();
        personalizedLine = lines.get(0);
        patchLine = lines.get(1);
        notReceivedLine = lines.get(3);

        Long batchId = batchService.create(lines.subList(0, 3), new BigDecimal("4.50"), null).id();
        batchService.send(batchId);
        batchService.receive(batchId, List.of());
    }

    @Test
    void givenReceivedShirt_whenMovedToStockWithCustomerPaying_thenItemCostsZero() throws Exception {
        moveToStock("{\"lineId\":%d,\"customerPays\":true,\"askingPrice\":25}".formatted(personalizedLine))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("ORDER_LINE"))
                .andExpect(jsonPath("$.salesOrderLineId").value(personalizedLine))
                .andExpect(jsonPath("$.name").value("Camiseta Player · Selección Española blanca · WILLIAMS JR 17"))
                .andExpect(jsonPath("$.size").value("M"))
                .andExpect(jsonPath("$.purchaseCost").value(0.00))
                .andExpect(jsonPath("$.purchaseDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.askingPrice").value(25.00))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        mockMvc.perform(get("/api/sales/orders/{id}", order.id()))
                .andExpect(jsonPath("$.totals.sale").value(order.totals().sale().doubleValue()))
                .andExpect(jsonPath("$.lines[0].status").value("STOCKED_PAID"));
    }

    // Por defecto no se le cobra al cliente: la camiseta sale del pedido y entra al stock con su coste y su envío
    @Test
    void givenReceivedShirt_whenMovedToStockByDefault_thenCustomerIsNotChargedAndItemCostsLineCostPlusShippingShare()
            throws Exception {
        moveToStock("{\"lineId\":%d}".formatted(personalizedLine))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseCost").value(10.40))
                .andExpect(jsonPath("$.askingPrice").value(nullValue()));

        mockMvc.perform(get("/api/sales/orders/{id}", order.id()))
                .andExpect(jsonPath("$.lines[0].status").value("STOCKED_REFUNDED"))
                .andExpect(jsonPath("$.totals.sale").value(order.totals().sale().subtract(new BigDecimal("19.00"))
                        .doubleValue()));
    }

    @Test
    void givenShirtAlreadyMoved_whenMovedAgain_thenConflictAndNoSecondItem() throws Exception {
        moveToStock("{\"lineId\":%d}".formatted(personalizedLine)).andExpect(status().isCreated());

        moveToStock("{\"lineId\":%d}".formatted(personalizedLine))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("no está recibida")));
        mockMvc.perform(get("/api/resale/items"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void givenShirtNotReceived_whenMovedToStock_thenConflictAndNoItem() throws Exception {
        moveToStock("{\"lineId\":%d}".formatted(notReceivedLine))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/resale/items"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void givenUnknownLine_whenMovedToStock_thenNotFound() throws Exception {
        moveToStock("{\"lineId\":999999}")
                .andExpect(status().isNotFound());
    }

    @Test
    void givenMissingLineId_whenMovedToStock_thenBadRequestOnField() throws Exception {
        moveToStock("{\"customerPays\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lineId"));
    }

    @Test
    void givenCustomizationWithoutText_whenMovedToStock_thenItsNameIsUsed() throws Exception {
        moveToStock("{\"lineId\":%d}".formatted(patchLine))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Camiseta Player · Real Madrid local · Parche"))
                .andExpect(jsonPath("$.size").value("L"));
    }

    // Producto, equipación y personalizaciones pueden sumar más de lo que cabe en el nombre de la prenda: se recorta
    @Test
    void givenVeryLongKitName_whenMovedToStock_thenNameIsCutToFitAndItemIsCreated() throws Exception {
        OrderLineDraft longKit = new OrderLineDraft(player, "K".repeat(160), ClothingSize.M, null, null,
                List.of(new OrderLineDraft.Customization(nameAndNumber, "VINICIUS JR 7", null, null)));
        Long lineId = salesOrderService.create(customerId, LocalDate.of(2026, 10, 6), null, List.of(longKit))
                .lines().get(0).id();
        Long batchId = batchService.create(List.of(lineId), new BigDecimal("4.00"), null).id();
        batchService.send(batchId);
        batchService.receive(batchId, List.of());

        moveToStock("{\"lineId\":%d}".formatted(lineId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(hasLength(160)));
    }

    private ResultActions moveToStock(String json) throws Exception {
        return mockMvc.perform(post("/api/resale/items/from-order-line")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
