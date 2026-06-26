/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web;

import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.wildfly.clustering.web.service.user.DistributableUserManagementProvider;
import org.wildfly.extension.clustering.web.sso.redis.RedisUserManagementProvider;
import org.wildfly.extension.redis.injection.RedisClientConfig;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.capability.CapabilityReference;
import org.wildfly.subsystem.resource.capability.CapabilityReferenceAttributeDefinition;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Registers a resource definition for a Redis single sign-on management provider.
 */
public class RedisUserManagementResourceDefinitionRegistrar implements ChildResourceDefinitionRegistrar, ResourceServiceConfigurator {

    private static final RuntimeCapability<Void> CAPABILITY = RuntimeCapability.Builder.of(DistributableUserManagementProvider.SERVICE_DESCRIPTOR)
            .setAllowMultipleRegistrations(true)
            .build();

    private static final UnaryServiceDescriptor<RedisClientConfig> REDIS_CONNECTION_DESCRIPTOR =
            UnaryServiceDescriptor.of("org.wildfly.redis.connection", RedisClientConfig.class);

    static final CapabilityReferenceAttributeDefinition<RedisClientConfig> REDIS_CONNECTION =
            new CapabilityReferenceAttributeDefinition.Builder<>("redis-connection",
                    CapabilityReference.builder(CAPABILITY, REDIS_CONNECTION_DESCRIPTOR).build())
                    .build();

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext context) {
        ResourceDescriptionResolver resolver = DistributableWebSubsystemResourceDefinitionRegistrar.RESOLVER.createChildResolver(
                UserManagementResourceRegistration.REDIS.getPathElement());
        ResourceDescriptor descriptor = ResourceDescriptor.builder(resolver)
                .addCapability(CAPABILITY)
                .addAttributes(List.of(REDIS_CONNECTION))
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(this))
                .build();
        ManagementResourceRegistration registration = parent.registerSubModel(
                ResourceDefinition.builder(UserManagementResourceRegistration.REDIS, descriptor.getResourceDescriptionResolver()).build());
        ManagementResourceRegistrar.of(descriptor).register(registration);
        return registration;
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        ServiceDependency<RedisClientConfig> redisConfigDep = REDIS_CONNECTION.resolve(context, model);
        return CapabilityServiceInstaller.builder(CAPABILITY, redisConfigDep.map(RedisUserManagementProvider::new)).build();
    }
}
