package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/sizes")
@Tag(name = "Catálogo - Tallas", description = "Tallas disponibles para cualquier producto")
class SizeController {

    @GetMapping
    List<SizeResponse> list() {
        return Arrays.stream(ClothingSize.values()).map(SizeResponse::from).toList();
    }
}
