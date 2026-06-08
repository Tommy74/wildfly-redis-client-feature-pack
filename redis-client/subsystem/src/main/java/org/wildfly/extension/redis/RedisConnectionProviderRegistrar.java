/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.redis;

import static org.wildfly.extension.redis.RedisCapabilities.REDIS_CLIENT_PROVIDER_CAPABILITY;

import java.util.Collection;
import java.util.List;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ParentResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.extension.redis.injection.RedisClientConfig;
import org.wildfly.service.capture.ValueExecutorRegistry;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;

public class RedisConnectionProviderRegistrar implements ChildResourceDefinitionRegistrar {

    public static final SimpleAttributeDefinition HOST = new SimpleAttributeDefinitionBuilder("redis-host", ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("localhost"))
            .build();

    public static final SimpleAttributeDefinition PORT = new SimpleAttributeDefinitionBuilder("port", ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(6379))
            .setValidator(new IntRangeValidator(1, 65535, true, true))
            .build();

    public static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder("password", ModelType.STRING, true)
            .setAllowExpression(true)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.CREDENTIAL)
            .build();

    public static final SimpleAttributeDefinition DATABASE = new SimpleAttributeDefinitionBuilder("database", ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(0))
            .setValidator(new IntRangeValidator(0, 15, true, true))
            .build();

    public static final SimpleAttributeDefinition SSL = SimpleAttributeDefinitionBuilder
            .create("ssl", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final SimpleAttributeDefinition CONNECTION_TIMEOUT = new SimpleAttributeDefinitionBuilder("connection-timeout", ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(2000))
            .build();

    public static final SimpleAttributeDefinition MAX_POOL_SIZE = new SimpleAttributeDefinitionBuilder("max-pool-size", ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(8))
            .setValidator(new IntRangeValidator(1, Integer.MAX_VALUE, true, true))
            .build();

    public static final SimpleAttributeDefinition MIN_IDLE = new SimpleAttributeDefinitionBuilder("min-idle", ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(0))
            .setValidator(new IntRangeValidator(0, Integer.MAX_VALUE, true, true))
            .build();

    public static final Collection<AttributeDefinition> ATTRIBUTES = List.of(
            HOST, PORT, PASSWORD, DATABASE, SSL, CONNECTION_TIMEOUT, MAX_POOL_SIZE, MIN_IDLE);

    private final ResourceDescriptor descriptor;
    static final String NAME = "redis-connection";
    public static final PathElement PATH = PathElement.pathElement(NAME);
    public static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PATH);
    private final ValueExecutorRegistry<String, RedisClientConfig> registry = ValueExecutorRegistry.newInstance();

    public RedisConnectionProviderRegistrar(ParentResourceDescriptionResolver parentResolver) {
        this.descriptor = ResourceDescriptor.builder(parentResolver.createChildResolver(PATH))
                .addCapability(REDIS_CLIENT_PROVIDER_CAPABILITY)
                .addAttributes(ATTRIBUTES)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(new RedisConnectionServiceConfigurator(registry)))
                .build();
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext context) {
        ResourceDefinition definition = ResourceDefinition.builder(REGISTRATION, this.descriptor.getResourceDescriptionResolver()).build();
        ManagementResourceRegistration resourceRegistration = parent.registerSubModel(definition);
        ManagementResourceRegistrar.of(this.descriptor).register(resourceRegistration);
        return resourceRegistration;
    }
}
