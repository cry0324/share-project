package com.share.device.emqx.factory.impl;

import com.share.device.emqx.annotation.ShareEmqx;
import com.share.device.emqx.factory.MessageHandlerFactory;
import com.share.device.emqx.handler.MassageHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class MessageHandlerFactoryImpl implements MessageHandlerFactory, ApplicationContextAware {


    private Map<String, MassageHandler> handlerMap = new HashMap<>();

    /**
     * 初始化bean对象
     *
     * @param ioc
     */
    @Override
    public void setApplicationContext(ApplicationContext ioc) {
        // 获取对象
        Map<String, MassageHandler> beanMap = ioc.getBeansOfType(MassageHandler.class);
        for (MassageHandler massageHandler : beanMap.values()) {
            ShareEmqx shareEmqx = AnnotatedElementUtils.findAllMergedAnnotations(massageHandler.getClass(), ShareEmqx.class).iterator().next();
            if (null != shareEmqx) {
                String topic = shareEmqx.topic();
                // 初始化到map
                handlerMap.put(topic, massageHandler);
            }
        }
    }

    @Override
    public MassageHandler getMassageHandler(String topic) {
        return handlerMap.get(topic);
    }
}
