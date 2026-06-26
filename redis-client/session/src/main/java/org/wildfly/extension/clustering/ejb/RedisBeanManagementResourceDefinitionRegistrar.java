/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.ejb;

import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.clustering.ejb.bean.BeanManagementConfiguration;
import org.wildfly.clustering.ejb.bean.BeanManagementProvider;
import org.wildfly.extension.clustering.ejb.bean.redis.RedisBeanManagementProvider;
import org.wildfly.extension.redis.injection.RedisClientConfig;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.capability.CapabilityReference;
import org.wildfly.subsystem.resource.capability.CapabilityReferenceAttributeDefinition;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Registers a resource definition for a Redis bean management provider.
 */
public class RedisBeanManagementResourceDefinitionRegistrar extends BeanManagementResourceDefinitionRegistrar {

    private static final UnaryServiceDescriptor<RedisClientConfig> REDIS_CONNECTION_DESCRIPTOR =
            UnaryServiceDescriptor.of("org.wildfly.redis.connection", RedisClientConfig.class);

    static final CapabilityReferenceAttributeDefinition<RedisClientConfig> REDIS_CONNECTION =
            new CapabilityReferenceAttributeDefinition.Builder<>("redis-connection",
                    CapabilityReference.builder(CAPABILITY, REDIS_CONNECTION_DESCRIPTOR).build())
                    .build();

    RedisBeanManagementResourceDefinitionRegistrar() {
        super(BeanManagementResourceRegistration.REDIS);
    }

    @Override
    public ResourceDescriptor.Builder apply(ResourceDescriptor.Builder builder) {
        return super.apply(builder).addAttributes(List.of(REDIS_CONNECTION));
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        String name = context.getCurrentAddressValue();
        BeanManagementConfiguration beanConfig = this.resolve(context, model);
        ServiceDependency<RedisClientConfig> redisConfigDep = REDIS_CONNECTION.resolve(context, model);
        return CapabilityServiceInstaller.builder(CAPABILITY, redisConfigDep.map(
                config -> new RedisBeanManagementProvider(name, beanConfig, config)
        )).build();
    }
}
