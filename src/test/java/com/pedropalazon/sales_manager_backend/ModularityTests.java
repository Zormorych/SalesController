package com.pedropalazon.sales_manager_backend;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(SalesManagerBackendApplication.class);

    @Test
    void givenApplicationModules_whenVerified_thenNoModuleBoundaryIsViolated() {
        modules.verify();
    }

    @Test
    void givenApplicationModules_whenDocumented_thenDiagramsAreWritten() {
        // Genera diagramas PlantUML y fichas de cada módulo en target/spring-modulith-docs
        new Documenter(modules).writeDocumentation();
    }
}
