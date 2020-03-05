package com.webank.ai.fate.serving;

import com.webank.ai.fate.register.common.NamedThreadFactory;
import com.webank.ai.fate.register.provider.FateServer;
import com.webank.ai.fate.register.provider.FateServerBuilder;
import com.webank.ai.fate.register.router.RouterService;
import com.webank.ai.fate.register.zookeeper.ZookeeperRegistry;
import com.webank.ai.fate.serving.core.bean.Configuration;
import com.webank.ai.fate.serving.core.bean.Dict;
import com.webank.ai.fate.serving.core.bean.SpringContextUtil;
import com.webank.ai.fate.serving.federatedml.model.BaseModel;
import com.webank.ai.fate.serving.service.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @Description TODO
 * @Author kaideng
 **/
@Service
public class NewServingServer implements InitializingBean{

    Logger logger = LoggerFactory.getLogger(NewServingServer.class);

    @Value("${port:8000}")
    int port;

    private Server server;
    @Autowired
    InferenceService  inferenceService;
    @Autowired
    ModelService  modelService;
    @Autowired
    ProxyService  proxyService;

    @Autowired
    ZookeeperRegistry zookeeperRegistry;

    @Autowired
    RouterService routerService;

    @Autowired
    Environment environment;



    @Override
    public void afterPropertiesSet() throws Exception {

        int processors = Runtime.getRuntime().availableProcessors();
        Integer corePoolSize = Configuration.getPropertyInt("serving.core.pool.size", processors);
        Integer maxPoolSize = Configuration.getPropertyInt("serving.max.pool.size", processors * 2);
        Integer aliveTime = Configuration.getPropertyInt("serving.pool.alive.time", 1000);
        Integer queueSize = Configuration.getPropertyInt("serving.pool.queue.size", 10);
        Executor executor = new ThreadPoolExecutor(corePoolSize, maxPoolSize, aliveTime.longValue(), TimeUnit.MILLISECONDS,
                new SynchronousQueue(), new NamedThreadFactory("ServingServer", true));

        FateServerBuilder serverBuilder = (FateServerBuilder) ServerBuilder.forPort(port);
        serverBuilder.keepAliveTime(100,TimeUnit.MILLISECONDS);
        serverBuilder.executor(executor);
        //new ServiceOverloadProtectionHandle()
        serverBuilder.addService(ServerInterceptors.intercept(inferenceService, new ServiceExceptionHandler(), new ServiceOverloadProtectionHandle()), InferenceService.class);
        serverBuilder.addService(ServerInterceptors.intercept(modelService, new ServiceExceptionHandler(), new ServiceOverloadProtectionHandle()), ModelService.class);
        serverBuilder.addService(ServerInterceptors.intercept(proxyService, new ServiceExceptionHandler(), new ServiceOverloadProtectionHandle()), ProxyService.class);
        server = serverBuilder.build();
        server.start();

        boolean useRegister = environment.getProperty(Dict.USE_REGISTER, boolean.class, Boolean.TRUE);
        if (useRegister) {
            logger.info("serving-server is using register center");

            zookeeperRegistry.subProject(Dict.PROPERTY_PROXY_ADDRESS);
            zookeeperRegistry.subProject(Dict.PROPERTY_FLOW_ADDRESS);

            FateServer.serviceSets.forEach(servie -> {
                try {
                    String serviceName = servie.serviceName();
                    String weightKey = serviceName + ".weight";
                    int weight = environment.getProperty(weightKey, int.class, 0);
                    if (weight > 0) {
                        zookeeperRegistry.getServieWeightMap().put(weightKey, weight);
                    }
                } catch (Throwable e) {
                    logger.error("parse interface weight error", e);
                }
            });
            zookeeperRegistry.register(FateServer.serviceSets);
        } else {
            logger.warn("serving-server not use register center");
        }
    }
}