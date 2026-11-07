package com.bebefish.erp.inventory.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.application.ReplenishmentCalculator.Config;
import com.bebefish.erp.inventory.application.ReplenishmentService;
import com.bebefish.erp.inventory.application.ReplenishmentService.ListResult;
import com.bebefish.erp.inventory.application.ReplenishmentService.Target;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/replenishment")
public class ReplenishmentController {
    private final ReplenishmentService service;

    public ReplenishmentController(ReplenishmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<ListResult> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(service.list(status, warehouseId, keyword, page, size));
    }

    @PutMapping("/{skuId}")
    @PreAuthorize("hasAuthority('inventory:view') and hasAuthority('inventory:edit')")
    public ApiResponse<Void> save(
            @PathVariable long skuId,
            @RequestParam(required = false) Long warehouseId,
            @RequestBody SaveRequest request
    ) {
        service.save(skuId, warehouseId, request.toConfig());
        return ApiResponse.success(null);
    }

    @PutMapping("/batch")
    @PreAuthorize("hasAuthority('inventory:view') and hasAuthority('inventory:edit')")
    public ApiResponse<Void> saveBatch(@RequestBody BatchSaveRequest request) {
        service.saveBatch(request.toTargets(), request.config() == null ? null : request.config().toConfig());
        return ApiResponse.success(null);
    }

    record BatchSaveRequest(List<TargetRequest> targets, SaveRequest config) {
        List<Target> toTargets() {
            return targets == null ? null : targets.stream()
                    .map(target -> target == null ? null : target.toTarget()).toList();
        }
    }

    record TargetRequest(BigDecimal skuId, BigDecimal warehouseId) {
        Target toTarget() {
            try {
                return new Target(skuId == null ? null : skuId.longValueExact(),
                        warehouseId == null ? null : warehouseId.longValueExact());
            } catch (ArithmeticException exception) {
                throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "补货记录无效");
            }
        }
    }

    record SaveRequest(String mode, BigDecimal bufferDays, BigDecimal leadDays,
                       BigDecimal manualSafety, BigDecimal manualPoint) {
        Config toConfig() {
            try {
                return new Config(mode, bufferDays == null ? null : bufferDays.intValueExact(),
                        leadDays == null ? null : leadDays.intValueExact(),
                        manualSafety == null ? null : manualSafety.longValueExact(),
                        manualPoint == null ? null : manualPoint.longValueExact());
            } catch (ArithmeticException exception) {
                throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "天数和库存阈值须填写有效整数");
            }
        }
    }
}
