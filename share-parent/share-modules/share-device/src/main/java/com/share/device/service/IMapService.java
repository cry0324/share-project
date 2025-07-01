package com.share.device.service;

import com.alibaba.fastjson.JSONObject;

public interface IMapService {

    JSONObject calculateLatLng(String keyword);

    Double calculateDistance(String startLongitude, String startLatitude, String endLongitude, String endLatitude);

}
