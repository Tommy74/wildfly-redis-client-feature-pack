/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.redis.injection;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import java.util.Map;
import redis.clients.jedis.JedisPooled;

public class RedisPortableExtension implements Extension {

    private final Map<String, RedisClientConfig> configs;

    public RedisPortableExtension(Map<String, RedisClientConfig> configs) {
        this.configs = configs;
    }

    void afterBeanDiscovery(@Observes AfterBeanDiscovery abd) {
        for (Map.Entry<String, RedisClientConfig> entry : configs.entrySet()) {
            String name = entry.getKey();
            RedisClientConfig config = entry.getValue();
            abd.addBean()
                    .types(JedisPooled.class)
                    .qualifiers(new RedisConnectionLiteral(name))
                    .scope(ApplicationScoped.class)
                    .name(name)
                    .produceWith(instance -> config.createJedisPooled())
                    .disposeWith((jedis, instance) -> jedis.close());
        }
    }
}
