/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web.sso.redis;

import java.util.List;

import org.wildfly.clustering.web.service.user.DistributableUserManagementProvider;
import org.wildfly.extension.redis.injection.RedisClientConfig;
import org.wildfly.subsystem.service.ServiceInstaller;

public class RedisUserManagementProvider implements DistributableUserManagementProvider {

    private final RedisClientConfig redisConfig;

    public RedisUserManagementProvider(RedisClientConfig redisConfig) {
        this.redisConfig = redisConfig;
    }

    @Override
    public Iterable<ServiceInstaller> getServiceInstallers(String name) {
        // TODO: Implement Redis-backed SSO management
        return List.of();
    }
}
