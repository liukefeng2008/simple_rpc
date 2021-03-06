package com.ifengxue.rpc.server.factory;

import com.ifengxue.rpc.client.async.AsyncConfig;
import com.ifengxue.rpc.client.async.IAsyncConfig;
import com.ifengxue.rpc.server.handle.IInvokeHandler;
import com.ifengxue.rpc.server.handle.MethodInvokeHandler;
import com.ifengxue.rpc.server.interceptor.Interceptor;
import com.ifengxue.rpc.server.register.IRegisterCenter;
import com.ifengxue.rpc.server.service.IServiceProvider;
import com.ifengxue.rpc.server.service.XmlServiceProvider;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 服务端配置工厂
 *
 * Created by LiuKeFeng on 2017-04-21.
 */
public class ServerConfigFactory {
    private static final ServerConfigFactory INSTANCE = new ServerConfigFactory();
    private static final String SERVER_SERVICE_NAME = "server.service.name";
    private static final String DEFAULT_SERVER_SERVICE_NAME = "RpcService";
    private static final String SERVER_SERVICE_BIND_HOST = "server.service.bind.host";
    private static final String DEFAULT_SERVER_SERVICE_BIND_HOST = "localhost";
    private static final String SERVER_SERVICE_BIND_PORT = "server.service.bind.port";
    private static final String DEFAULT_SERVER_SERVICE_BIND_PORT = "9091";
    private static final String SERVER_JSON_RPC_SERVICE_BIND_HOST = "server.json.rpc.service.bind.host";
    private static final String DEFAULT_SERVER_JSON_RPC_SERVICE_BIND_HOST = "localhost";
    private static final String SERVER_JSON_RPC_SERVICE_BIND_PORT = "server.json.rpc.service.bind.port";
    private static final String DEFAULT_SERVER_JSON_RPC_SERVICE_BIND_PORT = "9092";
    private static final String SERVER_JSON_RPC_ENABLE = "server.json.rpc.enable";
    private static final String DEFAULT_SERVER_JSON_RPC_ENABLE = "false";

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConfigFactory.class);
    private static volatile boolean isInitial;
    private static Properties serviceProperties = new Properties();
    private static List<String> classNames = new ArrayList<>();
    private static List<Interceptor> interceptors = new ArrayList<>();
    private static IRegisterCenter registerCenter;
    private static IServiceProvider serviceProvider;
    private ServerConfigFactory() {}
    public static ServerConfigFactory getInstance() {
        return INSTANCE;
    }

    /**
     *  初始化配置工厂
     * @param config 配置文件的位置
     */
    public static synchronized void initConfig(String config) {
        if (isInitial) {
            throw new IllegalStateException("服务端已经完成初始化！");
        }
        LOGGER.info("Server config path:{}", config);
        SAXReader saxReader = new SAXReader();
        try {
            Document document = saxReader.read(new File(config));
            Element rootElement = document.getRootElement();
            //初始化服务启动参数
            Element serverElement = rootElement.element("server");
            if (serverElement != null) {
                serviceProperties.setProperty(SERVER_SERVICE_NAME, serverElement.attributeValue("name", DEFAULT_SERVER_SERVICE_NAME));
                serviceProperties.setProperty(SERVER_SERVICE_BIND_HOST, serverElement.attributeValue("host", DEFAULT_SERVER_SERVICE_BIND_HOST));
                serviceProperties.setProperty(SERVER_SERVICE_BIND_PORT, serverElement.attributeValue("port", DEFAULT_SERVER_SERVICE_BIND_PORT));
            }
            Element jsonRpcServerElement = rootElement.element("json-rpc-server");
            if (jsonRpcServerElement != null) {
                serviceProperties.setProperty(SERVER_JSON_RPC_SERVICE_BIND_HOST, jsonRpcServerElement.attributeValue("host", DEFAULT_SERVER_JSON_RPC_SERVICE_BIND_HOST));
                serviceProperties.setProperty(SERVER_JSON_RPC_SERVICE_BIND_PORT, jsonRpcServerElement.attributeValue("port", DEFAULT_SERVER_JSON_RPC_SERVICE_BIND_PORT));
                serviceProperties.setProperty(SERVER_JSON_RPC_ENABLE, jsonRpcServerElement.attributeValue("enable", DEFAULT_SERVER_JSON_RPC_ENABLE));
            }

            //初始化对外提供的服务实现类
            Element serviceElement = rootElement.element("services");
            List<Element> serviceElements = serviceElement.elements("service");
            for (Element element : serviceElements) {
                classNames.add(element.attributeValue("class"));
            }
            String serviceProviderClassName = serviceElement.attributeValue("providerClass", XmlServiceProvider.class.getName());
            Class<IServiceProvider> serviceProviderClass = (Class<IServiceProvider>) Class.forName(serviceProviderClassName);
            //XmlServiceProvider特殊处理
            if (serviceProviderClass.equals(XmlServiceProvider.class)) {
                serviceProvider = new XmlServiceProvider(classNames);
            } else {
                serviceProvider = serviceProviderClass.newInstance();
            }

            //初始化拦截器
            Element interceptorsElement = rootElement.element("interceptors");
            if (interceptorsElement != null) {
                List<Element> interceptorElements = interceptorsElement.elements("interceptor");
                for (Element interceptorElement : interceptorElements) {
                    Interceptor interceptor = (Interceptor) Class.forName(interceptorElement.attributeValue("class")).newInstance();
                    LOGGER.info("register interceptor:" + interceptor.getClass().getName());
                    interceptors.add(interceptor);
                }
            }
            //初始化注册中心
            Element registerCenterElement = rootElement.element("register-center");
            if (registerCenterElement != null) {
                registerCenter = (IRegisterCenter) Class.forName(registerCenterElement.attributeValue("class")).newInstance();
                registerCenter.init(registerCenterElement);
                registerCenter.register(serviceProperties.getProperty(
                        SERVER_SERVICE_NAME, DEFAULT_SERVER_SERVICE_NAME),
                        serviceProperties.getProperty(SERVER_SERVICE_BIND_HOST, DEFAULT_SERVER_SERVICE_BIND_HOST),
                        Integer.parseInt(serviceProperties.getProperty(SERVER_SERVICE_BIND_PORT, DEFAULT_SERVER_SERVICE_BIND_PORT)));
            } else {
                LOGGER.warn("当前没有注册中心");
            }
        } catch (Exception e) {
            throw new IllegalStateException("服务端初始化失败:" + e.getMessage(), e);
        }
        isInitial = true;
    }
    public List<Interceptor> getAllInterceptor() {
        return interceptors;
    }

    public IServiceProvider getServiceProvider() {
        return serviceProvider;
    }

    public IRegisterCenter getRegisterCenter() {
        return registerCenter;
    }

    public IInvokeHandler getInvokeHandler() {
        return new MethodInvokeHandler();
    }

    public String getServiceName() {
        return serviceProperties.getProperty(SERVER_SERVICE_NAME, DEFAULT_SERVER_SERVICE_NAME);
    }

    public String getBindHost() {
        return serviceProperties.getProperty(SERVER_SERVICE_BIND_HOST, DEFAULT_SERVER_SERVICE_BIND_HOST);
    }

    public int getBindPort() {
        return Integer.parseInt(
                serviceProperties.getProperty(SERVER_SERVICE_BIND_PORT, DEFAULT_SERVER_SERVICE_BIND_PORT));
    }

    public boolean getEnableJSONRpc() {
        return Boolean.parseBoolean(
                serviceProperties.getProperty(SERVER_JSON_RPC_ENABLE, DEFAULT_SERVER_JSON_RPC_ENABLE));
    }

    public String getJSONRpcBindHost() {
        return serviceProperties.getProperty(
                SERVER_JSON_RPC_SERVICE_BIND_HOST, DEFAULT_SERVER_JSON_RPC_SERVICE_BIND_HOST);
    }

    public int getJSONRpcBindPort() {
        return Integer.parseInt(
                serviceProperties.getProperty(SERVER_JSON_RPC_SERVICE_BIND_PORT, DEFAULT_SERVER_JSON_RPC_SERVICE_BIND_PORT));
    }
}
