/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.redis.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.redis.testcontainers.RedisContainer;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.wildfly.redis.test.app.SessionServlet;

/**
 * Tests that HTTP session data stored in Redis survives WildFly node failure.
 *
 * <ol>
 *   <li>Starts a Redis container via Testcontainers</li>
 *   <li>Starts two WildFly instances (different port offsets) provisioned with distributable-web + Redis</li>
 *   <li>Deploys a distributable web app to both nodes</li>
 *   <li>Creates a session on node 1, stores an attribute</li>
 *   <li>Reads the attribute from node 2 (verifies Redis-backed session sharing)</li>
 *   <li>Kills node 1</li>
 *   <li>Verifies the session and its data are still accessible on node 2</li>
 * </ol>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RedisSessionFailoverIT {

    private static final String JBOSS_HOME = System.getProperty("jboss.home.distributable",
            new File("target/wildfly-distributable").getAbsolutePath());
    private static final int NODE1_PORT_OFFSET = 100;
    private static final int NODE2_PORT_OFFSET = 200;
    private static final int NODE1_HTTP_PORT = 8080 + NODE1_PORT_OFFSET;
    private static final int NODE2_HTTP_PORT = 8080 + NODE2_PORT_OFFSET;
    private static final String APP_NAME = "session-failover-test";
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

    private static RedisContainer redis;
    private static Process node1Process;
    private static Process node2Process;
    private static HttpClient httpClient;
    private static String sessionId;
    private static Thread shutdownHook;

    @BeforeAll
    static void setup() throws Exception {
        killStaleWildFlyProcesses();

        shutdownHook = new Thread(RedisSessionFailoverIT::teardown, "failover-test-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        redis = new RedisContainer("redis:7-alpine");
        redis.start();

        String redisNodes = redis.getHost() + ":" + redis.getMappedPort(6379);

        WebArchive war = ShrinkWrap.create(WebArchive.class, APP_NAME + ".war")
                .addClass(SessionServlet.class)
                .addAsWebInfResource(new StringAsset(
                        "<?xml version=\"1.0\"?>\n<web-app xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" version=\"6.0\">\n    <distributable/>\n</web-app>"),
                        "web.xml")
                .addAsWebInfResource(new StringAsset(""), "beans.xml");

        Path deployDir = Path.of(JBOSS_HOME, "standalone", "deployments");
        deployDir.toFile().mkdirs();
        File warFile = deployDir.resolve(APP_NAME + ".war").toFile();
        war.as(ZipExporter.class).exportTo(warFile, true);

        node1Process = startWildFly(NODE1_PORT_OFFSET, redisNodes, "node1");
        node2Process = startWildFly(NODE2_PORT_OFFSET, redisNodes, "node2");

        waitForServer(NODE1_HTTP_PORT);
        waitForServer(NODE2_HTTP_PORT);

        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @AfterAll
    static void teardown() {
        destroyProcess(node1Process);
        node1Process = null;
        destroyProcess(node2Process);
        node2Process = null;
        killStaleWildFlyProcesses();
        if (redis != null) {
            redis.stop();
            redis = null;
        }
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
            }
            shutdownHook = null;
        }
    }

    @Test
    @Order(1)
    void testCreateSessionOnNode1() throws Exception {
        String url = String.format("http://localhost:%d/%s/session?action=set&key=testKey&value=failoverValue", NODE1_HTTP_PORT, APP_NAME);
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Node1 should respond 200");
        Map<String, String> body = parseBody(response.body());
        sessionId = body.get("sessionId");
        assertNotNull(sessionId, "Session ID should be returned");
        assertEquals("failoverValue", body.get("value"));
    }

    @Test
    @Order(2)
    void testReadSessionFromNode2BeforeFailover() throws Exception {
        String url = String.format("http://localhost:%d/%s/session?action=get&key=testKey", NODE2_HTTP_PORT, APP_NAME);
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Node2 should find the session stored in Redis");
        Map<String, String> body = parseBody(response.body());
        assertEquals("failoverValue", body.get("value"), "Session attribute should be accessible from node2 via Redis");
    }

    @Test
    @Order(3)
    void testMultipleSessionAttributes() throws Exception {
        String setUrl = String.format("http://localhost:%d/%s/session?action=set&key=attr2&value=secondValue", NODE1_HTTP_PORT, APP_NAME);
        HttpResponse<String> setResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(setUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, setResponse.statusCode());

        String getUrl1 = String.format("http://localhost:%d/%s/session?action=get&key=testKey", NODE2_HTTP_PORT, APP_NAME);
        HttpResponse<String> getResponse1 = httpClient.send(
                HttpRequest.newBuilder(URI.create(getUrl1)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals("failoverValue", parseBody(getResponse1.body()).get("value"), "Original attribute should still be accessible");

        String getUrl2 = String.format("http://localhost:%d/%s/session?action=get&key=attr2", NODE2_HTTP_PORT, APP_NAME);
        HttpResponse<String> getResponse2 = httpClient.send(
                HttpRequest.newBuilder(URI.create(getUrl2)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals("secondValue", parseBody(getResponse2.body()).get("value"), "Second attribute should be accessible from node2");
    }

    @Test
    @Order(4)
    void testKillNode1AndReadFromNode2() throws Exception {
        destroyProcess(node1Process);
        node1Process = null;

        Thread.sleep(2000);

        String url = String.format("http://localhost:%d/%s/session?action=get&key=testKey", NODE2_HTTP_PORT, APP_NAME);
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Node2 should still respond after node1 is killed");
        Map<String, String> body = parseBody(response.body());
        assertEquals("failoverValue", body.get("value"), "Session data should survive node1 failure because it is stored in Redis");
    }

    @Test
    @Order(5)
    void testSessionInvalidation() throws Exception {
        String invalidateUrl = String.format("http://localhost:%d/%s/session?action=invalidate", NODE2_HTTP_PORT, APP_NAME);
        HttpResponse<String> invalidateResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(invalidateUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, invalidateResponse.statusCode());
        assertTrue(invalidateResponse.body().contains("invalidated="), "Session should be invalidated");

        String getUrl = String.format("http://localhost:%d/%s/session?action=get&key=testKey", NODE2_HTTP_PORT, APP_NAME);
        HttpResponse<String> getResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(getUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, getResponse.statusCode(), "Invalidated session should not be found");
    }

    @Test
    @Order(6)
    void testNewSessionAfterInvalidation() throws Exception {
        String setUrl = String.format("http://localhost:%d/%s/session?action=set&key=newKey&value=newValue", NODE2_HTTP_PORT, APP_NAME);
        HttpResponse<String> setResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(setUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, setResponse.statusCode());
        Map<String, String> body = parseBody(setResponse.body());
        String newSessionId = body.get("sessionId");
        assertNotNull(newSessionId, "New session should be created");
        assertEquals("newValue", body.get("value"));
    }

    private static Process startWildFly(int portOffset, String redisNodes, String nodeName) throws Exception {
        String javaHome = System.getProperty("java.home");
        String standaloneSh = Path.of(JBOSS_HOME, "bin", "standalone.sh").toString();

        ProcessBuilder pb = new ProcessBuilder(
                standaloneSh,
                "--stability=community",
                "-Djboss.socket.binding.port-offset=" + portOffset,
                "-Djboss.node.name=" + nodeName,
                "-Djboss.server.data.dir=" + Path.of(JBOSS_HOME, "standalone", "data-" + nodeName),
                "-Djboss.server.log.dir=" + Path.of(JBOSS_HOME, "standalone", "log-" + nodeName),
                "-Djboss.server.temp.dir=" + Path.of(JBOSS_HOME, "standalone", "tmp-" + nodeName),
                "-Dredis.cluster.nodes=" + redisNodes
        );
        pb.environment().put("JAVA_HOME", javaHome);
        pb.redirectErrorStream(true);
        pb.directory(new File(JBOSS_HOME));

        Process process = pb.start();
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[" + nodeName + "] " + line);
                }
            } catch (Exception ignored) {
            }
        }, "wildfly-" + nodeName + "-output").start();

        return process;
    }

    private static void waitForServer(int httpPort) throws Exception {
        HttpClient probeClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> response = probeClient.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + httpPort + "/" + APP_NAME + "/session")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) return;
            } catch (Exception ignored) {
            }
            Thread.sleep(1000);
        }
        throw new RuntimeException("Server on port " + httpPort + " did not start within " + STARTUP_TIMEOUT);
    }

    private static void killStaleWildFlyProcesses() {
        ProcessHandle.allProcesses()
                .filter(ph -> ph.info().commandLine()
                        .map(cmd -> cmd.contains("wildfly-distributable") && cmd.contains("org.jboss.as.standalone"))
                        .orElse(false))
                .forEach(ph -> {
                    ph.destroyForcibly();
                    try {
                        ph.onExit().get(10, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                    }
                });
    }

    private static void destroyProcess(Process process) {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            try {
                process.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static Map<String, String> parseBody(String body) {
        return body.lines()
                .filter(line -> line.contains("="))
                .collect(Collectors.toMap(
                        line -> line.substring(0, line.indexOf('=')),
                        line -> line.substring(line.indexOf('=') + 1),
                        (a, b) -> b));
    }
}
