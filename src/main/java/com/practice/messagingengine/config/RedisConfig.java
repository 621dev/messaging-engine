package com.practice.messagingengine.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;

@Configuration
public class RedisConfig {

    @Profile("local")
    @Bean
    public RedisConnectionFactory localRedisConnectionFactory(
            @Value("${spring.data.redis.host:192.168.0.136}") @NonNull String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
    }

    @Profile("dev")
    @Bean
    public RedisConnectionFactory clusterRedisConnectionFactory(
            @Value("${spring.data.redis.cluster.nodes}") @NonNull List<String> clusterNodes) {
        return new LettuceConnectionFactory(new RedisClusterConfiguration(clusterNodes));
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        return template;
    }
}
