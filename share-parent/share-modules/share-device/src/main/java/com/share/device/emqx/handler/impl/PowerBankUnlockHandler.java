package com.share.device.emqx.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.share.common.rabbit.constant.MqConst;
import com.share.common.rabbit.service.RabbitService;
import com.share.device.domain.Cabinet;
import com.share.device.domain.CabinetSlot;
import com.share.device.domain.PowerBank;
import com.share.device.domain.Station;
import com.share.device.emqx.annotation.ShareEmqx;
import com.share.device.emqx.constant.EmqxConstants;
import com.share.device.emqx.handler.MassageHandler;
import com.share.device.service.ICabinetService;
import com.share.device.service.ICabinetSlotService;
import com.share.device.service.IPowerBankService;
import com.share.device.service.IStationService;
import com.share.order.domain.SubmitOrderVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ShareEmqx(topic = EmqxConstants.TOPIC_POWERBANK_UNLOCK)
public class PowerBankUnlockHandler implements MassageHandler {

    private static final String UNLOCK_PREFIX = "powerBank:unlock:";

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ICabinetService cabinetService;

    @Autowired
    private IPowerBankService powerBankService;

    @Autowired
    private ICabinetSlotService cabinetSlotService;

    @Autowired
    private IStationService stationService;

    @Autowired
    private RabbitService rabbitService;

    /**
     * 充电宝弹出后的处理
     *
     * @param message
     */
    @Override
    public void handleMessage(JSONObject message) {
        log.info("handleMessage: {}", message.toJSONString());
        String messageNo = message.getString("mNo");
        String key = UNLOCK_PREFIX + messageNo;
        Boolean ifAbsent = redisTemplate.opsForValue().setIfAbsent(key, messageNo, 1, TimeUnit.HOURS);
        // 幂等性处理，防止重复提交
        if (!ifAbsent) {
            return;
        }
        //柜机编号
        String cabinetNo = message.getString("cNo");
        //充电宝编号
        String powerBankNo = message.getString("pNo");
        //插槽编号
        String slotNo = message.getString("sNo");
        //用户id
        Long userId = message.getLong("uId");
        if (StringUtils.isEmpty(cabinetNo)
                || StringUtils.isEmpty(powerBankNo)
                || StringUtils.isEmpty(slotNo)
                || null == userId) {
            log.info("参数为空: {}", message.toJSONString());
            return;
        }
        //获取柜机
        Cabinet cabinet = cabinetService.getBtCabinetNo(cabinetNo);
        // 获取充电宝
        PowerBank powerBank =powerBankService.getByPowerBankNo(powerBankNo);
        // 获取插槽
        CabinetSlot cabinetSlot = cabinetSlotService.getBtSlotNo(cabinet.getId(), slotNo);
        // 获取站点
        Station station = stationService.getByCabinetId(cabinet.getId());

        //更新充电宝状态
        // 状态（0:未投放 1：可用 2：已租用 3：充电中 4：故障）
        powerBank.setStatus("2");
        powerBankService.updateById(powerBank);

        //更新插槽状态
        // 状态（1：占用 0：空闲）
        cabinetSlot.setStatus("0");
        cabinetSlot.setPowerBankId(null);
        cabinetSlot.setUpdateTime(new Date());
        cabinetSlotService.updateById(cabinetSlot);

        //更新柜机信息
        int freeSlots = cabinet.getFreeSlots() + 1;
        cabinet.setFreeSlots(freeSlots);
        int usedSlots = cabinet.getUsedSlots() - 1;
        cabinet.setUsedSlots(usedSlots);
        //可以借用
        int availableNum = cabinet.getAvailableNum() - 1;
        cabinet.setAvailableNum(availableNum);
        cabinet.setUpdateTime(new Date());
        cabinetService.updateById(cabinet);

        //发送消息构建订单
        SubmitOrderVo submitOrderVo = new SubmitOrderVo();
        submitOrderVo.setMessageNo(messageNo);
        submitOrderVo.setUserId(userId);
        submitOrderVo.setPowerBankNo(powerBankNo);
        submitOrderVo.setStartStationId(station.getId());
        submitOrderVo.setStartStationName(station.getName());
        submitOrderVo.setStartCabinetNo(cabinetNo);
        submitOrderVo.setFeeRuleId(station.getFeeRuleId());
        log.info("构建订单对象: {}", JSONObject.toJSONString(submitOrderVo));
        //发送信息
        rabbitService.sendMessage(MqConst.EXCHANGE_ORDER, MqConst.ROUTING_SUBMIT_ORDER, JSONObject.toJSONString(submitOrderVo));
    }
}
