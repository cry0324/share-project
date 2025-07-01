package com.share.device.emqx.handler;

import com.alibaba.fastjson.JSONObject;

public interface MassageHandler {

    /**
     * 策略接口
     * @param message
     */
    void handleMessage(JSONObject message);
}
