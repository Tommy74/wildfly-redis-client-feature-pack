/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.dmr.ModelNode;
import org.wildfly.clustering.marshalling.ByteBufferMarshaller;
import org.wildfly.clustering.session.SessionAttributePersistenceStrategy;
import org.wildfly.clustering.web.service.routing.RouteLocatorProvider;
import org.wildfly.clustering.web.service.routing.RoutingProvider;
import org.wildfly.clustering.web.service.session.DistributableSessionManagementConfiguration;
import org.wildfly.clustering.web.service.session.DistributableSessionManagementProvider;
import org.wildfly.extension.clustering.web.session.redis.RedisSessionManagementProvider;
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
 * Registers a resource definition for a Redis session management provider.
 */
public class RedisSessionManagementResourceDefinitionRegistrar implements ChildResourceDefinitionRegistrar, ResourceServiceConfigurator {

    private static final RuntimeCapability<Void> CAPABILITY = RuntimeCapability.Builder.of(DistributableSessionManagementProvider.SERVICE_DESCRIPTOR)
            .addRequirements(RoutingProvider.SERVICE_DESCRIPTOR.getName())
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
                SessionManagementResourceRegistration.REDIS.getPathElement(), PathElement.pathElement("session-management"));
        ResourceDescriptor descriptor = ResourceDescriptor.builder(resolver)
                .addCapability(CAPABILITY)
                .addAttributes(List.of(REDIS_CONNECTION, SessionManagementResourceDefinitionRegistrar.GRANULARITY, SessionManagementResourceDefinitionRegistrar.MARSHALLER))
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(this))
                .build();
        ManagementResourceRegistration registration = parent.registerSubModel(
                ResourceDefinition.builder(SessionManagementResourceRegistration.REDIS, resolver).build());
        ManagementResourceRegistrar.of(descriptor).register(registration);

        new NoAffinityResourceDefinitionRegistrar().register(registration, context);
        new LocalAffinityResourceDefinitionRegistrar().register(registration, context);

        return registration;
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        SessionGranularity granularity = SessionManagementResourceDefinitionRegistrar.GRANULARITY.resolve(context, model);
        SessionMarshallerFactory marshallerFactory = SessionManagementResourceDefinitionRegistrar.MARSHALLER.resolve(context, model);
        ServiceDependency<RedisClientConfig> redisConfigDep = REDIS_CONNECTION.resolve(context, model);

        DistributableSessionManagementConfiguration<DeploymentUnit> configuration = new DistributableSessionManagementConfiguration<>() {
            @Override
            public SessionAttributePersistenceStrategy getAttributePersistenceStrategy() {
                return granularity.getAttributePersistenceStrategy();
            }

            @Override
            public Function<DeploymentUnit, ByteBufferMarshaller> getMarshallerFactory() {
                return marshallerFactory;
            }

            @Override
            public Optional<Duration> getIdleThreshold() {
                return Optional.empty();
            }
        };

        return CapabilityServiceInstaller.builder(CAPABILITY, ServiceDependency.on(RouteLocatorProvider.SERVICE_DESCRIPTOR, context.getCurrentAddressValue()).map(
                locatorProvider -> new RedisSessionManagementProvider(configuration, redisConfigDep, locatorProvider)
        )).requires(redisConfigDep).build();
    }
}
