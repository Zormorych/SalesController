package com.pedropalazon.sales_manager_backend.shared.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI salesControllerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SalesController API")
                .description("API REST para gestionar ventas de camisetas, cobros y pedidos al fabricante")
                .version("v1"));
    }
}
