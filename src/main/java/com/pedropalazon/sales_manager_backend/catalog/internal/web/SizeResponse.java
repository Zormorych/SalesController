package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;

public record SizeResponse(String code, String label) {

    static SizeResponse from(ClothingSize size) {
        return new SizeResponse(size.name(), size.getLabel());
    }
}
