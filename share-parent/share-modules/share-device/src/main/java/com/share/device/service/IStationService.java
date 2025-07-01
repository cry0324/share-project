package com.share.device.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.share.device.domain.Station;

import java.util.Collection;
import java.util.List;

public interface IStationService extends IService<Station>
{

    public List<Station> selectStationList(Station station);

    int saveStation(Station station);

    int updateStation(Station station);

    boolean removeByIds(Collection<?> list);

    void updateData();

    Station getByCabinetId(Long cabinetId);
}
