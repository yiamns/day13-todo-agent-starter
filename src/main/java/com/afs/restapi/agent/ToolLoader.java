package com.afs.restapi.agent;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 工具加载器，负责动态加载和管理工具函数
 * 自动扫描 Spring Bean 中 @Tool 注解，能够自动发现所有带有 @Tool 注解的 Spring Bean
 * 同时保持对配置生成工具的支持
 */
@Component
@Primary
public class ToolLoader {

    private static final Logger logger = LoggerFactory.getLogger(ToolLoader.class);

    private final ApplicationContext applicationContext;

    private final ChatClient chatClient;

    private final List<ToolCallbackProvider> generalToolProviders = new ArrayList<>();
    private final List<Object> scannedToolBeans = new ArrayList<>();
    private boolean hasScanned = false;

    public ToolLoader(ApplicationContext applicationContext, ChatClient.Builder chatClientBuilder) {
        this.applicationContext = applicationContext;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 初始化工具，包括扫描所有 Spring Bean 中的 @Tool 注解
     * 使用 @PostConstruct 避免循环依赖
     */
    @PostConstruct
    private void initializeTools() {
        // 延迟扫描，避免循环依赖
        logger.info("ToolLoader initialized, tool scanning will be performed on first access");
    }

    /**
     * 延迟扫描工具 Bean，只在第一次访问时执行
     */
    private synchronized void ensureToolsScanned() {
        if (!hasScanned) {
            scanForToolBeans();
        }
        initializeGeneralTools();
    }

    /**
     * 扫描所有 Spring Bean 中的 @Tool 注解
     */
    private void scanForToolBeans() {
        if (hasScanned) {
            return;
        }

        logger.info("Scanning for @Tool annotated methods in Spring beans...");

        String[] beanNames = applicationContext.getBeanDefinitionNames();
        int toolBeansFound = 0;
        int toolMethodsFound = 0;

        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> beanClass = bean.getClass();

                // 跳过代理类，获取真实类
                if (beanClass.getName().contains("$$")) {
                    beanClass = beanClass.getSuperclass();
                }

                // 检查是否有 @Tool 注解的方法
                Method[] methods = beanClass.getDeclaredMethods();
                boolean hasToolMethods = false;

                for (Method method : methods) {
                    if (method.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class)) {
                        hasToolMethods = true;
                        toolMethodsFound++;

                        org.springframework.ai.tool.annotation.Tool toolAnnotation =
                                method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
                        logger.debug("Found @Tool method: {}.{} - {}",
                                beanClass.getSimpleName(), method.getName(), toolAnnotation.description());
                    }
                }

                if (hasToolMethods) {
                    scannedToolBeans.add(bean);
                    toolBeansFound++;
                    logger.info("Registered tool bean: {} ({})", beanName, beanClass.getSimpleName());
                }

            } catch (Exception e) {
                logger.debug("Failed to scan bean: {}", beanName, e);
            }
        }

        logger.info("Tool scanning completed: {} beans with {} @Tool methods found",
                toolBeansFound, toolMethodsFound);
        hasScanned = true;
    }

    /**
     * 初始化通用工具
     */
    private void initializeGeneralTools() {
        // 将所有扫描到的工具 Bean 作为通用工具
        for (Object toolBean : scannedToolBeans) {
            try {
                ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                        .toolObjects(toolBean)
                        .build();
                generalToolProviders.add(provider);

                logger.debug("Added tool bean {} to general tools with {} callbacks",
                        toolBean.getClass().getSimpleName(), provider.getToolCallbacks().length);

            } catch (Exception e) {
                logger.error("Failed to create tool provider for bean: {}",
                        toolBean.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 获取所有可用工具（包括自动扫描的和配置生成的）
     *
     * @return 所有可用工具回调列表
     */
    public List<ToolCallback> getAllAvailableTools() {
        List<ToolCallback> allTools = new ArrayList<>();

        allTools.addAll(getGeneralTools());

        return allTools.stream().distinct().toList();
    }

    /**
     * 获取所有通用工具回调
     *
     * @return 通用工具回调列表
     */
    public List<ToolCallback> getGeneralTools() {
        ensureToolsScanned();
        List<ToolCallback> allGeneralTools = new ArrayList<>();
        for (ToolCallbackProvider provider : generalToolProviders) {
            allGeneralTools.addAll(Arrays.asList(provider.getToolCallbacks()));
        }
        return allGeneralTools;
    }
}
