/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web.session.redis;

import java.util.function.Supplier;

import jakarta.servlet.ServletContext;

import org.jboss.as.server.deployment.DeploymentUnit;
import org.wildfly.clustering.session.SessionManagerFactory;
import org.wildfly.clustering.web.service.deployment.WebDeploymentConfiguration;
import org.wildfly.clustering.web.service.deployment.WebDeploymentServiceDescriptor;
import org.wildfly.clustering.web.service.routing.RouteLocatorProvider;
import org.wildfly.clustering.web.service.session.DistributableSessionManagementConfiguration;
import org.wildfly.clustering.web.service.session.DistributableSessionManagementProvider;
import org.wildfly.clustering.web.service.session.SessionManagerFactoryConfiguration;
import org.wildfly.extension.redis.injection.RedisClientConfig;
import org.wildfly.subsystem.service.DeploymentServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;
import org.wildfly.common.function.Functions;

public class RedisSessionManagementProvider implements DistributableSessionManagementProvider {

    private final DistributableSessionManagementConfiguration<DeploymentUnit> configuration;
    private final ServiceDependency<RedisClientConfig> redisConfig;
    private final RouteLocatorProvider locatorProvider;

    public RedisSessionManagementProvider(DistributableSessionManagementConfiguration<DeploymentUnit> configuration,
                                          ServiceDependency<RedisClientConfig> redisConfig,
                                          RouteLocatorProvider locatorProvider) {
        this.configuration = configuration;
        this.redisConfig = redisConfig;
        this.locatorProvider = locatorProvider;
    }

    @Override
    public <C> DeploymentServiceInstaller getSessionManagerFactoryServiceInstaller(SessionManagerFactoryConfiguration<C> factoryConfiguration) {
        ServiceDependency<RedisClientConfig> redisConfig = this.redisConfig;
        Supplier<SessionManagerFactory<ServletContext, C>> factory = () ->
                new RedisSessionManagerFactory<>(redisConfig.get(), factoryConfiguration);
        return ServiceInstaller.builder(factory)
                .provides(WebDeploymentServiceDescriptor.SESSION_MANAGER_FACTORY.resolve(factoryConfiguration.getDeploymentUnit()))
                .requires(redisConfig)
                .onStop(Functions.closingConsumer())
                .build();
    }

    @Override
    public DeploymentServiceInstaller getRouteLocatorServiceInstaller(WebDeploymentConfiguration configuration) {
        return this.locatorProvider.getServiceInstaller(null, configuration);
    }

    @Override
    public DistributableSessionManagementConfiguration<DeploymentUnit> getSessionManagementConfiguration() {
        return this.configuration;
    }
}
