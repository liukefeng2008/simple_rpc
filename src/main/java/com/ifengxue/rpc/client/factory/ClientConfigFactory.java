package com.ifengxue.rpc.client.factory;

import com.ifengxue.rpc.client.async.AsyncMethod;
import com.ifengxue.rpc.client.pool.ChannelPoolConfig;
import com.ifengxue.rpc.client.pool.IChannelPool;
import com.ifengxue.rpc.client.pool.SimpleChannelPool;
import com.ifengxue.rpc.client.register.IRegisterCenter;
import com.ifengxue.rpc.protocol.enums.CompressTypeEnum;
import com.ifengxue.rpc.protocol.enums.SerializerTypeEnum;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * 客户端配置工厂
 * Created by LiuKeFeng on 2017-04-21.
 */
public class ClientConfigFactory {
    private static final String SERIALIZE_TYPE_KEY = "serialize";
    private static final String DEFAULT_SERIALIZE_TYPE = "kryo";
    private static final String COMPRESS_TYPE_KEY = "compress";
    private static final String DEFAULT_COMPRESS_TYPE = "deflater";
    private static final String MIN_COMPRESS_FRAME_LENGTH_KEY = "minCompressFrameLength";
    private static final String DEFAULT_MIN_COMPRESS_FRAME_LENGTH = "3145728";
    private static final ClientConfigFactory INSTANCE = new ClientConfigFactory();
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConfigFactory.class);
    private static volatile boolean isInitial = false;
    private static IRegisterCenter registerCenter;
    private static ChannelPoolConfig channelPoolConfig = new ChannelPoolConfig();
    private static Properties protocolProperties = new Properties();
    private static Map<Class<?>, List<AsyncMethod>> asyncClassMethodMap = new HashMap<>();
    private ClientConfigFactory() {}

    public static synchronized void initConfigFactory(String config) {
        if (isInitial) {
            throw new IllegalStateException("客户端已经完成初始化！");
        }
        LOGGER.info("Client config path:{}", config);
        SAXReader saxReader = new SAXReader();
        try {
            Document document = saxReader.read(new File(config));
            Element rootElement = document.getRootElement();
            //初始化注册中心
            Element registerCenterElement = rootElement.element("register-center");
            String className = registerCenterElement.attributeValue("class");
            LOGGER.info("注册中心实现:" + className);
            registerCenter = (IRegisterCenter) Class.forName(className).newInstance();
            LOGGER.info("初始化注册中心成功...");
            registerCenter.init(registerCenterElement);
            //初始化连接池配置
            Element socketPoolElement = rootElement.element("socket-pool");
            if (socketPoolElement != null) {
                List<Element> propertyElements = socketPoolElement.elements("property");
                for (Element propertyElement : propertyElements) {
                    String propertyName = propertyElement.attributeValue("name");
                    String propertyValue = propertyElement.attributeValue("value");
                    BeanUtils.setProperty(channelPoolConfig, propertyName, propertyValue);
                }
                LOGGER.info("初始化连接池配置信息成功...");
            }
            //初始化协议
            Element protocolElement = rootElement.element("protocol");
            if (protocolElement != null) {
                protocolElement.attributeValue(SERIALIZE_TYPE_KEY, DEFAULT_SERIALIZE_TYPE);
                protocolElement.attributeValue(COMPRESS_TYPE_KEY, DEFAULT_COMPRESS_TYPE);
                protocolElement.attributeValue(MIN_COMPRESS_FRAME_LENGTH_KEY, DEFAULT_MIN_COMPRESS_FRAME_LENGTH);
                LOGGER.info("初始化协议成功...");
            }

            //初始化需要异步请求的方法列表
            Element asyncElement = rootElement.element("async");
            if (asyncElement != null && Boolean.parseBoolean(asyncElement.attributeValue("async", "true"))) {
                List<Element> serviceElements = asyncElement.elements("service");
                for (Element serviceElement : serviceElements) {
                    Class<?> clazz = Class.forName(serviceElement.attributeValue("class"));
                    List<Element> methodElements = serviceElement.elements("method");
                    List<AsyncMethod> asyncMethods = new ArrayList<>(methodElements.size());
                    for (Element methodElement : methodElements) {
                        asyncMethods.add(new AsyncMethod(clazz,
                                methodElement.attributeValue("name"),
                                Boolean.parseBoolean(methodElement.attributeValue("async", "true")),
                                Boolean.parseBoolean(methodElement.attributeValue("sent", "false")),
                                Boolean.parseBoolean(methodElement.attributeValue("return", "true"))));
                    }
                    asyncClassMethodMap.put(clazz, asyncMethods.stream().filter(AsyncMethod::isAsync).collect(Collectors.toList()));
                }
            }
            asyncClassMethodMap.forEach((clazzKey, asyncMethodListValue) -> {
                if (!asyncMethodListValue.isEmpty()) {
                    LOGGER.info("异步请求服务:{},方法列表[{}]", clazzKey.getSimpleName(),
                            asyncMethodListValue.stream().map(AsyncMethod::getMethodName).collect(Collectors.joining(", ")));
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ClientConfigFactory getInstance() {
        return INSTANCE;
    }

    /**
     * 当发送的包>=该值，则自动压缩
     * @return
     * @see #getCompressTypeEnum()
     */
    public long minCompressFrameLength() {
        return Long.parseLong(protocolProperties.getProperty(MIN_COMPRESS_FRAME_LENGTH_KEY, DEFAULT_MIN_COMPRESS_FRAME_LENGTH));
    }

    /**
     * 获取客户端使用的序列化类型
     * @return
     */
    public SerializerTypeEnum getSerializerTypeEnum() {
        return SerializerTypeEnum.getEnumByName(protocolProperties.getProperty(SERIALIZE_TYPE_KEY, DEFAULT_SERIALIZE_TYPE));
    }

    /**
     * 获取客户端使用的压缩类型
     * @return
     */
    public CompressTypeEnum getCompressTypeEnum() {
        return CompressTypeEnum.getEnumByName(protocolProperties.getProperty(COMPRESS_TYPE_KEY, DEFAULT_COMPRESS_TYPE));
    }

    /**
     * 连接池实现
     * @return
     */
    public IChannelPool getChannelPool() {
        return new SimpleChannelPool(newKeyedChannelPoolConfig(), getRegisterCenter());
    }

    /**
     * 注册中心实现
     * @return
     */
    public IRegisterCenter getRegisterCenter() {
        return registerCenter;
    }

    /**
     * 连接池配置
     * @return
     */
    public ChannelPoolConfig getChannelPoolConfig() {
        return channelPoolConfig;
    }

    /**
     * 创建用于commons-pool2配置的新实例
     * @return
     */
    public GenericKeyedObjectPoolConfig newKeyedChannelPoolConfig() {
        GenericKeyedObjectPoolConfig genericKeyedObjectPoolConfig = new GenericKeyedObjectPoolConfig();
        genericKeyedObjectPoolConfig.setMinIdlePerKey(channelPoolConfig.getMinIdle());
        genericKeyedObjectPoolConfig.setMaxTotalPerKey(channelPoolConfig.getMaxPoolSize());
        genericKeyedObjectPoolConfig.setMaxIdlePerKey(channelPoolConfig.getMaxIdle());
        genericKeyedObjectPoolConfig.setTestOnBorrow(channelPoolConfig.isTestOnBorrow());
        genericKeyedObjectPoolConfig.setMaxWaitMillis(channelPoolConfig.getMaxWaitTimeout());
        return genericKeyedObjectPoolConfig;
    }

    /**
     * 返回需要异步请求的方法列表
     * @return
     */
    public Map<Class<?>, List<AsyncMethod>> getAsyncClassMethodMap() {
        return Collections.unmodifiableMap(asyncClassMethodMap);
    }
}
