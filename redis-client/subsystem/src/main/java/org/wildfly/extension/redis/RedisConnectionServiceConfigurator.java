/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.redis;

import static org.wildfly.extension.redis.RedisCapabilities.REDIS_CLIENT_PROVIDER_CAPABILITY;
import static org.wildfly.extension.redis.RedisConnectionProviderRegistrar.*;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import redis.clients.jedis.HostAndPort;
import org.wildfly.extension.redis.injection.RedisClientConfig;
import org.wildfly.service.capture.ValueRegistry;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

public class RedisConnectionServiceConfigurator implements ResourceServiceConfigurator {

    private final ValueRegistry<String, RedisClientConfig> registry;

    RedisConnectionServiceConfigurator(ValueRegistry<String, RedisClientConfig> registry) {
        this.registry = registry;
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        String host = HOST.resolveModelAttribute(context, model).asString();
        int port = PORT.resolveModelAttribute(context, model).asInt();
        String password = PASSWORD.resolveModelAttribute(context, model).asStringOrNull();
        int database = DATABASE.resolveModelAttribute(context, model).asInt();
        boolean ssl = SSL.resolveModelAttribute(context, model).asBoolean();
        int connectionTimeout = CONNECTION_TIMEOUT.resolveModelAttribute(context, model).asInt();
        int maxPoolSize = MAX_POOL_SIZE.resolveModelAttribute(context, model).asInt();
        int minIdle = MIN_IDLE.resolveModelAttribute(context, model).asInt();
        String clusterNodesValue = CLUSTER_NODES.resolveModelAttribute(context, model).asStringOrNull();

        Set<HostAndPort> clusterNodes = new HashSet<>();
        if (clusterNodesValue != null && !clusterNodesValue.isBlank()) {
            for (String node : clusterNodesValue.split(",")) {
                String trimmed = node.trim();
                int lastColon = trimmed.lastIndexOf(':');
                if (lastColon > 0) {
                    String nodeHost = trimmed.substring(0, lastColon);
                    int nodePort = Integer.parseInt(trimmed.substring(lastColon + 1));
                    clusterNodes.add(new HostAndPort(nodeHost, nodePort));
                }
            }
        }

        Supplier<RedisClientConfig> factory = () -> new RedisClientConfig()
                .host(host)
                .port(port)
                .password(password)
                .database(database)
                .ssl(ssl)
                .connectionTimeout(connectionTimeout)
                .maxPoolSize(maxPoolSize)
                .minIdle(minIdle)
                .clusterNodes(clusterNodes);

        Consumer<RedisClientConfig> captor = registry.add(context.getCurrentAddressValue());
        ResourceServiceInstaller installer = CapabilityServiceInstaller.builder(REDIS_CLIENT_PROVIDER_CAPABILITY, factory)
                .withCaptor(captor)
                .asActive()
                .build();
        Consumer<OperationContext> remover = ctx -> registry.remove(ctx.getCurrentAddressValue());
        return new ResourceServiceInstaller() {
            @Override
            public Consumer<OperationContext> install(OperationContext ctx) {
                return installer.install(ctx).andThen(remover);
            }
        };
    }
}
