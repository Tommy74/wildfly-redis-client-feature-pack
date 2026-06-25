/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.redis.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.file.Path;
import jakarta.inject.Inject;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.wildfly.extension.redis.injection.RedisConnection;
import redis.clients.jedis.UnifiedJedis;

@ExtendWith(ArquillianExtension.class)
public class RedisSslSocketBindingIT {

    private static GenericContainer<?> redis;

    @Inject
    @RedisConnection("ssl-socket-binding-conn")
    private UnifiedJedis jedis;

    @BeforeAll
    static void startRedis() {
        Path tlsDir = new File("src/test/resources/tls").getAbsoluteFile().toPath();
        Path truststorePath = tlsDir.resolve("truststore.p12");

        redis = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379)
                .withFileSystemBind(tlsDir.toString(), "/tls", BindMode.READ_ONLY)
                .withCommand(
                        "redis-server",
                        "--tls-port", "6379",
                        "--port", "0",
                        "--tls-cert-file", "/tls/server-cert.pem",
                        "--tls-key-file", "/tls/server-key.pem",
                        "--tls-ca-cert-file", "/tls/ca-cert.pem"
                )
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
        redis.start();

        String sslHost = redis.getHost();
        int sslPort = redis.getMappedPort(6379);

        System.setProperty("redis.ssl.host", sslHost);
        System.setProperty("redis.ssl.port", String.valueOf(sslPort));
        System.setProperty("redis.ssl.nodes", sslHost + ":" + sslPort);
        System.setProperty("redis.truststore.path", truststorePath.toString());
        System.setProperty("redis.cluster.nodes", sslHost + ":" + sslPort);
    }

    @AfterAll
    static void stopRedis() {
        if (redis != null) redis.stop();
    }

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "redis-ssl-socket-binding-test.war")
                .addClass(RedisSslSocketBindingIT.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void testRedisInjectionViaSslAndSocketBinding() {
        assertNotNull(jedis, "UnifiedJedis should be injected via SSL context and socket binding");
    }

    @Test
    public void testRedisSetGetViaSslAndSocketBinding() {
        String key = "test-ssl-sb-key-" + System.currentTimeMillis();
        jedis.set(key, "ssl-socket-binding-value");
        String value = jedis.get(key);
        assertEquals("ssl-socket-binding-value", value);
        jedis.del(key);
    }
}
