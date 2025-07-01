package com.share.device.controller;

import com.share.common.core.web.controller.BaseController;
import com.share.common.core.web.domain.AjaxResult;
import com.share.common.core.web.page.TableDataInfo;
import com.share.common.security.annotation.RequiresPermissions;
import com.share.device.domain.CabinetType;
import com.share.device.service.ICabinetTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@Tag(name = "柜机类型接口管理")
@RestController
@RequestMapping("/cabinetType")
public class CabinetTypeController extends BaseController {
    @Autowired
    private ICabinetTypeService cabinetTypeService;

    /**
     * 查询柜机类型列表
     */
    @Operation(summary = "查询柜机类型列表")
    @GetMapping("/list")
    public TableDataInfo list(CabinetType cabinetType) {
        startPage();
        List<CabinetType> list = cabinetTypeService.selectCabinetTypeList(cabinetType);
        return getDataTable(list);
    }

    /**
     * 根据id查询详情
     */
    @Operation(summary = "根据id查询详情")
    @GetMapping("/{id}")
    public AjaxResult getCabinetType(@PathVariable Long id) {
        CabinetType cabinetType = cabinetTypeService.getById(id);
        return success(cabinetType);
    }

    @Operation(summary = "新增柜机类型")
    @PostMapping
    @RequiresPermissions("device:cabinetType:add")
    public AjaxResult add(@RequestBody CabinetType cabinetType) {
        return toAjax(cabinetTypeService.save(cabinetType));
    }

    @Operation(summary = "修改柜机类型")
    @PutMapping
    public AjaxResult edit(@RequestBody CabinetType cabinetType) {
        return toAjax(cabinetTypeService.updateById(cabinetType));
    }

    @Operation(summary = "删除柜机类型")
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(cabinetTypeService.removeBatchByIds(Arrays.asList(ids)));
    }

    @Operation(summary = "查询全部柜机类型列表")
    @GetMapping("/getCabinetTypeList")
    public AjaxResult getCabinetTypeList() {
        return success(cabinetTypeService.list());
    }
}
