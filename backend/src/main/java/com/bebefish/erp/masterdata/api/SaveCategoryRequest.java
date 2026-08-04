package com.bebefish.erp.masterdata.api;

import com.bebefish.erp.masterdata.application.SaveCategoryCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveCategoryRequest(
        @Size(max = 50) String categoryCode,
        @NotBlank @Size(max = 100) String categoryName,
        @Min(0) Integer sortOrder,
        @Size(max = 500) String remark
) {
    SaveCategoryCommand toCommand() {
        return new SaveCategoryCommand(
                categoryCode,
                categoryName,
                sortOrder == null ? 0 : sortOrder,
                remark
        );
    }
}
