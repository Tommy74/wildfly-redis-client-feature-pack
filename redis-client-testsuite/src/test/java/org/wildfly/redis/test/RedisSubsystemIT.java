/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.redis.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.wildfly.extension.redis.injection.RedisConnection;
import redis.clients.jedis.JedisPooled;

@ExtendWith(ArquillianExtension.class)
public class RedisSubsystemIT {

    @Inject
    @RedisConnection("default")
    private JedisPooled jedis;

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "redis-test.war")
                .addClass(RedisSubsystemIT.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void testRedisInjection() {
        assertNotNull(jedis, "JedisPooled should be injected");
    }

    @Test
    public void testRedisSetGet() {
        String key = "test-key-" + System.currentTimeMillis();
        jedis.set(key, "hello-redis");
        String value = jedis.get(key);
        assertEquals("hello-redis", value);
        jedis.del(key);
    }
}
