/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.redis.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.wildfly.extension.redis.injection.RedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;

public class RedisSingleNodeIT {

    private static RedisContainer redis;
    private static UnifiedJedis jedis;

    @BeforeAll
    static void setUp() {
        redis = new RedisContainer("redis:7-alpine");
        redis.start();

        RedisClientConfig config = new RedisClientConfig()
                .clusterNodes(Set.of(new HostAndPort(redis.getHost(), redis.getMappedPort(6379))));

        assertFalse(config.isClusterMode(), "Single node should not be cluster mode");
        jedis = config.createUnifiedJedis();
    }

    @AfterAll
    static void tearDown() {
        if (jedis != null) jedis.close();
        if (redis != null) redis.stop();
    }

    @Test
    void testReturnsJedisPooled() {
        assertInstanceOf(JedisPooled.class, jedis);
    }

    @Test
    void testPing() {
        assertEquals("PONG", jedis.ping());
    }

    @Test
    void testSetAndGet() {
        String key = "test-single-" + System.currentTimeMillis();
        jedis.set(key, "value1");
        assertEquals("value1", jedis.get(key));
        jedis.del(key);
    }

    @Test
    void testDelete() {
        String key = "test-del-" + System.currentTimeMillis();
        jedis.set(key, "to-delete");
        assertEquals(1, jedis.del(key));
        assertNull(jedis.get(key));
    }
}