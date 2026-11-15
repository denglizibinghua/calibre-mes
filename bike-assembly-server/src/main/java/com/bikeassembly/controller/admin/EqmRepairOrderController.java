package com.bikeassembly.controller.admin;

import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.dto.CompleteRepairDTO;
import com.bikeassembly.dto.EqmRepairOrderDTO;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.EqmRepairOrderService;
import com.bikeassembly.validation.OnCreate;
import com.bikeassembly.vo.EqmRepairOrderVO;
import jakarta.validation.groups.Default;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/eqm-repair-order")
@Slf4j
public class EqmRepairOrderController {

    @Autowired
    private EqmRepairOrderService eqmRepairOrderService;

    @GetMapping("/list")
    @RequirePermission(PermissionConstant.ADMIN_REPAIR_VIEW)
    public Result<PageResult<EqmRepairOrderVO>> list(@RequestParam(defaultValue = "1") Long page,
                                                      @RequestParam(defaultValue = "15") Long size) {
        PageResult<EqmRepairOrderVO> pageResult = eqmRepairOrderService.listEqmRepairOrders(page, size, "all", null);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_REPAIR_VIEW)
    public Result<EqmRepairOrderVO> getById(@PathVariable Long id) {
        EqmRepairOrderVO vo = eqmRepairOrderService.getEqmRepairOrderById(id);
        return Result.success(vo);
    }

    @PostMapping
    @RequirePermission(PermissionConstant.ADMIN_REPAIR_MANAGE)
    public Result<EqmRepairOrderVO> save(@Validated({Default.class, OnCreate.class}) @RequestBody EqmRepairOrderDTO dto) {
        EqmRepairOrderVO vo = eqmRepairOrderService.saveEqmRepairOrder(dto);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_REPAIR_MANAGE)
    public Result<EqmRepairOrderVO> update(@PathVariable Long id, @Validated @RequestBody EqmRepairOrderDTO dto) {
        EqmRepairOrderVO vo = eqmRepairOrderService.updateEqmRepairOrder(id, dto);
        return Result.success(vo);
    }

    /**
     * 接单维修：待受理(0) → 维修中(1)，维修人取当前登录用户。
     */
    @PutMapping("/{id}/accept")
    @RequirePermission(PermissionConstant.ADMIN_REPAIR_MANAGE)
    public Result<EqmRepairOrderVO> accept(@PathVariable Long id) {
        EqmRepairOrderVO vo = eqmRepairOrderService.acceptEqmRepairOrder(id);
        return Result.success(vo);
    }

    /**
     * 完成维修：维修中(1) → 已完成(2)，提交维修结果；完成后联动恢复设备运行状态。
     */
    @PutMapping("/{id}/complete")
    @RequirePermission(PermissionConstant.ADMIN_REPAIR_MANAGE)
    public Result<EqmRepairOrderVO> complete(@PathVariable Long id,
                                              @Validated @RequestBody(required = false) CompleteRepairDTO dto) {
        EqmRepairOrderVO vo = eqmRepairOrderService.completeEqmRepairOrderWithDto(id, dto);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionConstant.ADMIN_REPAIR_MANAGE)
    public Result<Void> delete(@PathVariable Long id) {
        eqmRepairOrderService.deleteEqmRepairOrder(id);
        return Result.success();
    }
}
