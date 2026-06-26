/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.ejb.bean.redis;

import java.util.List;

import org.wildfly.clustering.ejb.bean.BeanConfiguration;
import org.wildfly.clustering.ejb.bean.BeanDeploymentConfiguration;
import org.wildfly.clustering.ejb.bean.BeanManagementConfiguration;
import org.wildfly.clustering.ejb.bean.BeanManagementProvider;
import org.wildfly.extension.redis.injection.RedisClientConfig;
import org.wildfly.subsystem.service.ServiceInstaller;

public class RedisBeanManagementProvider implements BeanManagementProvider {

    private final String name;
    private final BeanManagementConfiguration configuration;
    private final RedisClientConfig redisConfig;

    public RedisBeanManagementProvider(String name, BeanManagementConfiguration configuration, RedisClientConfig redisConfig) {
        this.name = name;
        this.configuration = configuration;
        this.redisConfig = redisConfig;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Iterable<ServiceInstaller> getDeploymentServiceInstallers(BeanDeploymentConfiguration configuration) {
        // TODO: Implement Redis-backed EJB bean management
        return List.of();
    }

    @Override
    public ServiceInstaller getBeanManagerFactoryServiceInstaller(org.jboss.msc.service.ServiceName name, BeanConfiguration configuration) {
        // TODO: Implement Redis-backed bean manager factory
        return null;
    }
}
