/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web.session.redis;

import jakarta.servlet.ServletContext;

import org.wildfly.clustering.session.SessionManager;
import org.wildfly.clustering.session.SessionManagerConfiguration;
import org.wildfly.clustering.session.SessionManagerFactory;
import org.wildfly.clustering.web.service.session.SessionManagerFactoryConfiguration;
import org.wildfly.extension.redis.injection.RedisClientConfig;

import redis.clients.jedis.UnifiedJedis;

public class RedisSessionManagerFactory<C> implements SessionManagerFactory<ServletContext, C> {

    private final RedisClientConfig redisConfig;
    private final SessionManagerFactoryConfiguration<C> configuration;
    private volatile UnifiedJedis jedis;

    public RedisSessionManagerFactory(RedisClientConfig redisConfig, SessionManagerFactoryConfiguration<C> configuration) {
        this.redisConfig = redisConfig;
        this.configuration = configuration;
        this.jedis = redisConfig.createUnifiedJedis();
    }

    @Override
    public SessionManager<C> createSessionManager(SessionManagerConfiguration<ServletContext> managerConfig) {
        String deploymentName = this.configuration.getDeploymentName();
        return new RedisSessionManager<>(this.jedis, deploymentName, managerConfig, this.configuration);
    }

    @Override
    public void close() {
        UnifiedJedis j = this.jedis;
        if (j != null) {
            j.close();
            this.jedis = null;
        }
    }
}
