package com.share.rule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.share.rule.domain.*;
import com.share.rule.mapper.FeeRuleMapper;
import com.share.rule.service.IFeeRuleService;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class FeeRuleServiceImpl extends ServiceImpl<FeeRuleMapper, FeeRule> implements IFeeRuleService {
    @Autowired
    private FeeRuleMapper feeRuleMapper;

    @Autowired
    private KieContainer kieContainer;


    @Override
    public List<FeeRule> selectFeeRuleList(FeeRule feeRule) {
        return feeRuleMapper.selectFeeRuleList(feeRule);
    }

    @Override
    public List<FeeRule> getALLFeeRuleList() {
        return feeRuleMapper.selectList(new LambdaQueryWrapper<FeeRule>().eq(FeeRule::getStatus, "1"));
    }

    /**
     * 使用规则引擎进行计算费用
     *
     * @param calculateOrderFeeForm
     * @return
     */
    @Override
    public FeeRuleResponseVo calculateOrderFee(FeeRuleRequestForm calculateOrderFeeForm) {
        // 1. 开启会话
        KieSession kieSession = kieContainer.newKieSession();
        FeeRuleRequest feeRuleRequest = new FeeRuleRequest();
        feeRuleRequest.setDurations(calculateOrderFeeForm.getDuration());
        FeeRuleResponse feeRuleResponse = new FeeRuleResponse();
        kieSession.setGlobal("feeRuleResponse", feeRuleResponse);
        kieSession.insert(feeRuleRequest);
        // 2. 触发规则
        kieSession.fireAllRules();
        // 3. 关闭会话
        kieSession.dispose();
        FeeRuleResponseVo feeRuleResponseVo = new FeeRuleResponseVo();
        feeRuleResponseVo.setTotalAmount(new BigDecimal(feeRuleResponse.getTotalAmount()));
        feeRuleResponseVo.setFreePrice(new BigDecimal(feeRuleResponse.getFreePrice()));
        feeRuleResponseVo.setFreeDescription(feeRuleResponse.getFreeDescription());
        feeRuleResponseVo.setExceedDescription(feeRuleResponse.getExceedDescription());
        feeRuleResponseVo.setExceedPrice(new BigDecimal(feeRuleResponse.getExceedPrice()));
        return feeRuleResponseVo;
    }

}
