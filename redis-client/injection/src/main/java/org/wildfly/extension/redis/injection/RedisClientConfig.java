/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.redis.injection;

import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisPooled;

public class RedisClientConfig {

    private String host = "localhost";
    private int port = 6379;
    private String password;
    private int database = 0;
    private boolean ssl = false;
    private int connectionTimeout = 2000;
    private int maxPoolSize = 8;
    private int minIdle = 0;

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

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
