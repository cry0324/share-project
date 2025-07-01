package com.share.device.api;

import com.share.common.core.constant.DeviceConstants;
import com.share.common.core.web.controller.BaseController;
import com.share.common.core.web.domain.AjaxResult;
import com.share.common.security.annotation.RequiresLogin;
import com.share.device.service.IDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "站点接口管理")
@RestController
@RequestMapping("/device")
public class DeviceApiController extends BaseController {

    @Autowired
    private IDeviceService deviceService;

    @Operation(summary = "获取附近站点信息列表")
    @GetMapping("/nearbyStationList/{latitude}/{longitude}")
    public AjaxResult nearbyStation(@PathVariable String latitude, @PathVariable String longitude) {
        return success(deviceService.nearbyStation(latitude, longitude, DeviceConstants.SEARCH_PC_RADIUS));
    }

    @Operation(summary = "根据id获取门店详情")
    @RequiresLogin
    @GetMapping("/getStation/{id}/{latitude}/{longitude}")
    public AjaxResult getStation(@PathVariable Long id, @PathVariable String latitude, @PathVariable String longitude)
    {
        return success(deviceService.getStation(id, latitude, longitude));
    }

    @Operation(summary = "扫码充电")
    @RequiresLogin
    @GetMapping("scanCharge/{cabinetNo}")
    public AjaxResult scanCharge(@PathVariable String cabinetNo) {
        return success(deviceService.scanCharge(cabinetNo));
    }
}
