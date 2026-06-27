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
import org.wildfly.extension.redis.injection.RedisConnection;
import redis.clients.jedis.UnifiedJedis;

@ExtendWith(ArquillianExtension.class)
public class RedisSslIT {

    private static final String CONTAINER_NAME = "redis-ssl-test";
    private static Process redisProcess;

    @Inject
    @RedisConnection("ssl-conn")
    private UnifiedJedis jedis;

    @Inject
    @RedisConnection("ssl-socket-binding-conn")
    private UnifiedJedis jedisSslSocketBinding;

    @BeforeAll
    static void startRedis() throws Exception {
        new ProcessBuilder("podman", "rm", "-f", CONTAINER_NAME)
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start().waitFor();
        Path tlsDir = new File("src/test/resources/tls").getAbsoluteFile().toPath();
        ProcessBuilder pb = new ProcessBuilder(
                "podman", "run", "--rm", "--network", "host",
                "--name", CONTAINER_NAME,
                "-v", tlsDir + ":/tls:ro,Z",
                "redis:7-alpine",
                "redis-server",
                "--tls-port", "6380",
                "--port", "0",
                "--tls-cert-file", "/tls/server-cert.pem",
                "--tls-key-file", "/tls/server-key.pem",
                "--tls-ca-cert-file", "/tls/ca-cert.pem",
                "--tls-auth-clients", "no"
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        redisProcess = pb.start();
        Thread.sleep(3000);
    }

    @AfterAll
    static void stopRedis() {
        try {
            new ProcessBuilder("podman", "stop", CONTAINER_NAME)
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start().waitFor();
        } catch (Exception ignored) {}
        if (redisProcess != null) {
            redisProcess.destroyForcibly();
        }
    }

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "redis-ssl-test.war")
                .addClass(RedisSslIT.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addPackages(true, "org.hamcrest");
    }

    @Test
    public void testRedisInjectionViaSslContext() {
        assertNotNull(jedis, "UnifiedJedis should be injected via SSL context");
    }

    @Test
    public void testRedisSetGetViaSslContext() {
        String key = "test-ssl-key-" + System.currentTimeMillis();
        jedis.set(key, "ssl-value");
        String value = jedis.get(key);
        assertEquals("ssl-value", value);
        jedis.del(key);
    }

    @Test
    public void testRedisInjectionViaSslSocketBinding() {
        assertNotNull(jedisSslSocketBinding, "UnifiedJedis should be injected via SSL + socket binding");
    }

    @Test
    public void testRedisSetGetViaSslSocketBinding() {
        String key = "test-ssl-sb-key-" + System.currentTimeMillis();
        jedisSslSocketBinding.set(key, "ssl-sb-value");
        String value = jedisSslSocketBinding.get(key);
        assertEquals("ssl-sb-value", value);
        jedisSslSocketBinding.del(key);
    }

}
