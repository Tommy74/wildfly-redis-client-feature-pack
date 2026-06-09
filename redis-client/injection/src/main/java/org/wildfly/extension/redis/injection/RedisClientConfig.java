/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.redis.injection;

import java.util.HashSet;
import java.util.Set;

import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;

public class RedisClientConfig {

    private String host = "localhost";
    private int port = 6379;
    private String password;
    private int database = 0;
    private boolean ssl = false;
    private int connectionTimeout = 2000;
    private int maxPoolSize = 8;
    private int minIdle = 0;
    private Set<HostAndPort> clusterNodes = new HashSet<>();

    public RedisClientConfig host(String host) {
        this.host = host;
        return this;
    }

    public RedisClientConfig port(int port) {
        this.port = port;
        return this;
    }

    public RedisClientConfig password(String password) {
        this.password = password;
        return this;
    }

    public RedisClientConfig database(int database) {
        this.database = database;
        return this;
    }

    public RedisClientConfig ssl(boolean ssl) {
        this.ssl = ssl;
        return this;
    }

    public RedisClientConfig connectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    public RedisClientConfig maxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        return this;
    }

    public RedisClientConfig minIdle(int minIdle) {
        this.minIdle = minIdle;
        return this;
    }

    public RedisClientConfig clusterNodes(Set<HostAndPort> clusterNodes) {
        this.clusterNodes = clusterNodes;
        return this;
    }

    public boolean isClusterMode() {
        return clusterNodes != null && !clusterNodes.isEmpty();
    }

    public UnifiedJedis createUnifiedJedis() {
        if (isClusterMode()) {
            return createJedisCluster();
        }
        return createJedisPooled();
    }

    public JedisPooled createJedisPooled() {
        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxTotal(maxPoolSize);
        poolConfig.setMinIdle(minIdle);

        DefaultJedisClientConfig.Builder clientConfigBuilder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(connectionTimeout)
                .database(database)
                .ssl(ssl);

        if (password != null && !password.isEmpty()) {
            clientConfigBuilder.password(password);
        }

        return new JedisPooled(poolConfig, new HostAndPort(host, port), clientConfigBuilder.build());
    }

    private JedisCluster createJedisCluster() {
        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxTotal(maxPoolSize);
        poolConfig.setMinIdle(minIdle);

        DefaultJedisClientConfig.Builder clientConfigBuilder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(connectionTimeout)
                .ssl(ssl);

        if (password != null && !password.isEmpty()) {
            clientConfigBuilder.password(password);
        }

        return new JedisCluster(clusterNodes, clientConfigBuilder.build(), maxPoolSize, poolConfig);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
