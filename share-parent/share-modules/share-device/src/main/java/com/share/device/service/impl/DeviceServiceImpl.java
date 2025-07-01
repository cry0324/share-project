package com.share.device.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.share.common.core.constant.SecurityConstants;
import com.share.common.core.context.SecurityContextHolder;
import com.share.common.core.exception.ServiceException;
import com.share.device.domain.*;
import com.share.device.emqx.EmqxClientWrapper;
import com.share.device.emqx.constant.EmqxConstants;
import com.share.device.service.*;
import com.share.order.api.RemoteOrderInfoService;
import com.share.order.domain.OrderInfo;
import com.share.rule.api.RemoteFeeRuleService;
import com.share.rule.domain.FeeRule;
import com.share.user.api.RemoteUserService;
import com.share.user.domain.UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class DeviceServiceImpl implements IDeviceService {

    @Autowired
    private IStationService stationService;

    @Autowired
    private ICabinetService cabinetService;

    @Autowired
    private ICabinetSlotService cabinetSlotService;

    @Autowired
    private IPowerBankService powerBankService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private IMapService mapService;

    @Autowired
    private RemoteFeeRuleService remoteFeeRuleService;

    @Autowired
    private RemoteUserService remoteUserService;

    @Autowired
    private RemoteOrderInfoService remoteOrderInfoService;

    @Autowired
    private EmqxClientWrapper emqxClientWrapper;

    /**
     * 扫码充电接口
     *
     * @param cabinetNo 柜机no
     * @return
     */
    @Override
    public ScanChargeVo scanCharge(String cabinetNo) {
        // 1. 获取当前用户的用户信息
        UserInfo userInfo = remoteUserService.getInfo(SecurityContextHolder.getUserId()).getData();
        if (null == userInfo) {
            throw new ServiceException("获取用户信息失败");
        }
        // 2. 检查是否免押金
        if ("0".equals(userInfo.getDepositStatus())) {
            throw new ServiceException("未申请免押金使用");
        }
        // 3. 检查是否有未完成订单
        OrderInfo orderInfo = remoteOrderInfoService.getNoFinishOrder(SecurityContextHolder.getUserId()).getData();
        ScanChargeVo scanChargeVo = new ScanChargeVo();
        if (null != orderInfo) {
            if ("0".equals(orderInfo.getStatus())) {
                scanChargeVo.setStatus("2");
                scanChargeVo.setMessage("有未归还充电宝，请归还后使用");
                return scanChargeVo;
            }
            if ("1".equals(orderInfo.getStatus())) {
                scanChargeVo.setStatus("3");
                scanChargeVo.setMessage("有未支付订单，去支付");
                return scanChargeVo;
            }
        }
        // 4. 获取柜机中的充电宝
        AvailablePowerBankVo availablePowerBankVo = getAvailablePowerBankVo(cabinetNo);
        if (null == availablePowerBankVo) {
            throw new ServiceException("无可用充电宝");
        }
        if (!StringUtils.isEmpty(availablePowerBankVo.getErrMessage())) {
            throw new ServiceException(availablePowerBankVo.getErrMessage());
        }
        // 5. mqtt弹出充电宝
        JSONObject object = new JSONObject();
        object.put("uId", SecurityContextHolder.getUserId());
        object.put("mNo", "mm" + RandomUtil.randomString(8));
        object.put("cNo", cabinetNo);
        object.put("pNo", availablePowerBankVo.getPowerBankNo());
        object.put("sNo", availablePowerBankVo.getSlotNo());
        String topic = String.format(EmqxConstants.TOPIC_SCAN_SUBMIT, cabinetNo);
        emqxClientWrapper.publish(topic, object.toJSONString());

        scanChargeVo.setStatus("1");
        return scanChargeVo;
    }

    private AvailablePowerBankVo getAvailablePowerBankVo(String cabinetNo) {
        AvailablePowerBankVo vo = new AvailablePowerBankVo();
        // 1. 查村cabinetNo的柜机信息
        Cabinet cabinet = cabinetService.getBtCabinetNo(cabinetNo);
        // 2. 检查指定柜机是否有可用充电宝
        if (null == cabinet || cabinet.getAvailableNum() == 0) {
            vo.setErrMessage("无可用充电宝");
            return vo;
        }
        // 获取插槽列表
        List<CabinetSlot> cabinetSlotList = cabinetSlotService.list(new LambdaQueryWrapper<CabinetSlot>()
                .eq(CabinetSlot::getCabinetId, cabinet.getId())
                .eq(CabinetSlot::getStatus, "1") // 状态（1：占用 0：空闲 2：锁定）
        );
        // 获取插槽对应的充电宝id列表
        List<Long> powerBankIdList = cabinetSlotList.stream().filter(item -> null != item.getPowerBankId()).map(CabinetSlot::getPowerBankId).collect(Collectors.toList());
        //获取可用充电宝列表
        List<PowerBank> powerBankList = powerBankService.list(new LambdaQueryWrapper<PowerBank>().in(PowerBank::getId, powerBankIdList).eq(PowerBank::getStatus, "1"));
        if (CollectionUtils.isEmpty(powerBankList)) {
            vo.setErrMessage("无可用充电宝");
            return vo;
        }
        // 根据电量降序排列
        if (powerBankList.size() > 1) {
            Collections.sort(powerBankList, (o1, o2) -> o2.getElectricity().compareTo(o1.getElectricity()));
        }
        // 获取电量最多的充电宝
        PowerBank powerBank = powerBankList.get(0);
        // 获取电量最多的充电宝插槽信息
        CabinetSlot cabinetSlot = cabinetSlotList.stream().filter(item -> null != item.getPowerBankId() && item.getPowerBankId().equals(powerBank.getId())).collect(Collectors.toList()).get(0);
        //锁定柜机卡槽
        cabinetSlot.setStatus("2");
        cabinetSlotService.updateById(cabinetSlot);

        // 设置返回对象
        vo.setPowerBankNo(powerBank.getPowerBankNo());
        vo.setSlotNo(cabinetSlot.getSlotNo());
        return vo;
    }

    @Override
    public List<StationVo> nearbyStation(String latitude, String longitude, Integer radius) {
        //坐标，确定中心点
        // GeoJsonPoint(double x, double y) x 表示经度，y 表示纬度。
        GeoJsonPoint geoJsonPoint = new GeoJsonPoint(Double.parseDouble(longitude), Double.parseDouble(latitude));
        //画圈的半径,50km范围
        Distance d = new Distance(radius, Metrics.KILOMETERS);
        //画了一个圆圈
        Circle circle = new Circle(geoJsonPoint, d);
        //条件排除自己
        Query query = Query.query(Criteria.where("location").withinSphere(circle));
        List<StationLocation> stationLocationList = this.mongoTemplate.find(query, StationLocation.class);
        if (CollectionUtils.isEmpty(stationLocationList)) {
            return null;
        }
        //组装数据
        List<Long> stationIdList = stationLocationList.stream().map(StationLocation::getStationId).collect(Collectors.toList());
        //获取站点列表
        List<Station> stationList = stationService.list(new LambdaQueryWrapper<Station>().in(Station::getId, stationIdList).isNotNull(Station::getCabinetId));

        //获取柜机id列表
        List<Long> cabinetIdList = stationList.stream().map(Station::getCabinetId).collect(Collectors.toList());
        //获取柜机id与柜机信息Map
        Map<Long, Cabinet> cabinetIdToCabinetMap = cabinetService.listByIds(cabinetIdList).stream().collect(Collectors.toMap(Cabinet::getId, Cabinet -> Cabinet));

        List<StationVo> stationVoList = new ArrayList<>();
        stationList.forEach(item -> {
            StationVo stationVo = new StationVo();
            BeanUtils.copyProperties(item, stationVo);
            Double distance = mapService.calculateDistance(longitude, latitude, item.getLongitude().toString(), item.getLatitude().toString());
            stationVo.setDistance(distance);
            // 获取柜机信息
            Cabinet cabinet = cabinetIdToCabinetMap.get(item.getCabinetId());
            //可用充电宝数量大于0，可借用
            if (cabinet.getAvailableNum() > 0) {
                stationVo.setIsUsable("1");
            } else {
                stationVo.setIsUsable("0");
            }
            // 获取空闲插槽数量大于0，可归还
            if (cabinet.getFreeSlots() > 0) {
                stationVo.setIsReturn("1");
            } else {
                stationVo.setIsReturn("0");
            }

            // 获取费用规则
            FeeRule feeRule = remoteFeeRuleService.getFeeRule(item.getFeeRuleId(), SecurityConstants.INNER).getData();
            stationVo.setFeeRule(feeRule.getDescription());
            stationVoList.add(stationVo);
        });
        return stationVoList;
    }

    @Override
    public StationVo getStation(Long id, String latitude, String longitude) {
        Station station = stationService.getById(id);
        StationVo stationVo = new StationVo();
        BeanUtils.copyProperties(station, stationVo);
        // 计算距离
        Double distance = mapService.calculateDistance(longitude, latitude, station.getLongitude().toString(), station.getLatitude().toString());
        stationVo.setDistance(distance);

        // 获取柜机信息
        Cabinet cabinet = cabinetService.getById(station.getCabinetId());
        //可用充电宝数量大于0，可借用
        if (cabinet.getAvailableNum() > 0) {
            stationVo.setIsUsable("1");
        } else {
            stationVo.setIsUsable("0");
        }
        // 获取空闲插槽数量大于0，可归还
        if (cabinet.getFreeSlots() > 0) {
            stationVo.setIsReturn("1");
        } else {
            stationVo.setIsReturn("0");
        }

        // 获取费用规则
        FeeRule feeRule = remoteFeeRuleService.getFeeRule(station.getFeeRuleId(), SecurityConstants.INNER).getData();
        stationVo.setFeeRule(feeRule.getDescription());
        return stationVo;
    }
}
