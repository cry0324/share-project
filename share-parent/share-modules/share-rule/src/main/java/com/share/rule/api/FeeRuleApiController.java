package com.share.rule.api;

import com.share.common.core.domain.R;
import com.share.common.security.annotation.InnerAuth;
import com.share.rule.domain.FeeRule;
import com.share.rule.domain.FeeRuleRequestForm;
import com.share.rule.domain.FeeRuleResponseVo;
import com.share.rule.service.IFeeRuleService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/feeRule")
@SuppressWarnings({"unchecked", "rawtypes"})
public class FeeRuleApiController {

    @Autowired
    private IFeeRuleService feeRuleService;

    @Operation(summary = "批量获取费用规则信息")
    @InnerAuth
    @PostMapping(value = "/getFeeRuleList")
    public R<List<FeeRule>> getFeeRuleList(@RequestBody List<Long> feeRuleIdList)
    {
        return R.ok(feeRuleService.listByIds(feeRuleIdList));
    }

    @Operation(summary = "获取费用规则详细信息")
    @InnerAuth
    @GetMapping(value = "/getFeeRule/{id}")
    public R<FeeRule> getFeeRule(@PathVariable("id") Long id)
    {
        return R.ok(feeRuleService.getById(id));
    }

    @Operation(summary = "计算订单费用")
    @InnerAuth
    @PostMapping("/calculateOrderFee")
    public R<FeeRuleResponseVo> calculateOrderFee(@RequestBody FeeRuleRequestForm calculateOrderFeeForm) {
        return R.ok(feeRuleService.calculateOrderFee(calculateOrderFeeForm));
    }
}
