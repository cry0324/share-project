package com.share.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.share.rule.domain.FeeRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FeeRuleMapper extends BaseMapper<FeeRule> {

    public List<FeeRule> selectFeeRuleList(FeeRule feeRule);

}
