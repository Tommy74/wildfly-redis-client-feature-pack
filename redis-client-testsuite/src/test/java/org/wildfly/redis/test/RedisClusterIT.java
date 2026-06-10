/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.redis.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.wildfly.extension.redis.injection.RedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.UnifiedJedis;

@EnabledOnOs(OS.LINUX)
public class RedisClusterIT {

    private static final int BASE_PORT = 7000;
    private static final int NODE_COUNT = 3;
    private static final List<GenericContainer<?>> nodes = new ArrayList<>();
    private static UnifiedJedis jedis;

    @BeforeAll
    static void setUp() throws Exception {
        for (int i = 0; i < NODE_COUNT; i++) {
            int port = BASE_PORT + i;
            int busPort = port + 10000;

            GenericContainer<?> node = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withCommand(
                            "redis-server",
                            "--port", String.valueOf(port),
                            "--cluster-enabled", "yes",
                            "--cluster-config-file", "nodes.conf",
                            "--cluster-node-timeout", "5000",
                            "--cluster-announce-ip", "127.0.0.1",
                            "--cluster-announce-port", String.valueOf(port),
                            "--cluster-announce-bus-port", String.valueOf(busPort),
                            "--appendonly", "yes"
                    )
                    .withNetworkMode("host");

            node.start();
            nodes.add(node);
        }

        StringBuilder clusterCreate = new StringBuilder("redis-cli --cluster create ");
        for (int i = 0; i < NODE_COUNT; i++) {
            clusterCreate.append("127.0.0.1:").append(BASE_PORT + i).append(" ");
        }
        clusterCreate.append("--cluster-replicas 0 --cluster-yes");

        nodes.get(0).execInContainer("sh", "-c", clusterCreate.toString());

        // Wait for cluster to stabilize
        for (int attempt = 0; attempt < 30; attempt++) {
            var info = nodes.get(0).execInContainer(
                    "redis-cli", "-p", String.valueOf(BASE_PORT), "cluster", "info");
            if (info.getStdout().contains("cluster_state:ok")) break;
            Thread.sleep(500);
        }

        Set<HostAndPort> clusterNodes = new HashSet<>();
        for (int i = 0; i < NODE_COUNT; i++) {
            clusterNodes.add(new HostAndPort("127.0.0.1", BASE_PORT + i));
        }

        RedisClientConfig config = new RedisClientConfig()
                .clusterNodes(clusterNodes)
                .connectionTimeout(5000);

        jedis = config.createUnifiedJedis();
    }

    @AfterAll
    static void tearDown() {
        if (jedis != null) jedis.close();
        nodes.forEach(GenericContainer::stop);
    }

    @Test
    void testIsClusterMode() {
        Set<HostAndPort> multipleNodes = Set.of(
                new HostAndPort("host1", 7000),
                new HostAndPort("host2", 7001));
        RedisClientConfig config = new RedisClientConfig().clusterNodes(multipleNodes);
        assertTrue(config.isClusterMode());
    }

    @Test
    void testReturnsJedisCluster() {
        assertInstanceOf(JedisCluster.class, jedis);
    }

    @Test
    void testClusterPing() {
        assertEquals("PONG", jedis.ping());
    }

    @Test
    void testClusterSetAndGet() {
        String key = "test-cluster-" + System.currentTimeMillis();
        jedis.set(key, "cluster-value");
        assertEquals("cluster-value", jedis.get(key));
        jedis.del(key);
    }

    @Test
    void testClusterDelete() {
        String key = "test-cdel-" + System.currentTimeMillis();
        jedis.set(key, "to-delete");
        assertEquals(1, jedis.del(key));
        assertNull(jedis.get(key));
    }

    @Test
    void testClusterMultipleKeys() {
        for (int i = 0; i < 100; i++) {
            String key = "mkey-" + i + "-" + System.currentTimeMillis();
            jedis.set(key, "val-" + i);
            assertEquals("val-" + i, jedis.get(key));
            jedis.del(key);
        }
    }
}