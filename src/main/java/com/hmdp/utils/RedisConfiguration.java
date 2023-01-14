package com.hmdp.utils;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author: 张鹏
 * @description: 配置 Redisson 客户端
 * @date: 2023/1/8 21:18
 */
@Configuration
public class RedisConfiguration {
    @Bean
    public RedissonClient redissonClient() {
        // 配置类
        Config config = new Config();
        // 添加 Redis 地址：此处是单节点地址，也可以通过 config.useClusterServers() 添加集群地址
        config.useSingleServer().setAddress("redis://192.168.79.129").setPassword("root");
        // 创建客户端
        return Redisson.create(config);
    }

}

