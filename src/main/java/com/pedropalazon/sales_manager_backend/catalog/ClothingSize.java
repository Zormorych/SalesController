package com.pedropalazon.sales_manager_backend.catalog;

// Tallas comunes a todos los productos (camisetas, chándales...). Se llama ClothingSize y no Size
// para no chocar con la anotación de validación jakarta.validation.constraints.Size.
public enum ClothingSize {

    S("S"),
    M("M"),
    L("L"),
    XL("XL"),
    XXL("XXL"),
    KIDS_16("16 (niño)"),
    KIDS_18("18 (niño)"),
    KIDS_20("20 (niño)"),
    KIDS_22("22 (niño)"),
    KIDS_24("24 (niño)"),
    KIDS_26("26 (niño)"),
    KIDS_28("28 (niño)");

    private final String label;

    ClothingSize(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
