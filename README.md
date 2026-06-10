# Redis Client Feature Pack for WildFly

A WildFly Galleon feature pack that provides Redis client support via [Jedis](https://github.com/redis/jedis). It adds a `redis-client` subsystem to WildFly that manages Redis connection pools and makes them injectable into your Jakarta EE applications via CDI.

## Prerequisites

- Java 17+
- Maven 3.9+
- Podman (for running Redis locally)

## Quick Start

### 1. Start a local Redis instance

```bash
podman run --rm -it --name redis -p 6379:6379 redis:7-alpine
```

### 2. Build the feature pack

```bash
cd redis-client-feature-pack
mvn clean install -DskipTests -Denforcer.skip
```

> NOTE: use `mvn clean install -T 1.5C -ntp -DskipTests -Denforcer.skip -Daether.dependencyCollector.impl=bf -Dmaven.artifact.threads=20` to speed up the build

### Alternative: use the included example application

If you want to skip the manual setup (steps 3–6 below), you can use the `redis-client-example` module that ships with this project. It is a ready-to-run JAX-RS application with Redis `set`, `get`, and `delete` endpoints already wired up:

```bash
# Start Redis
podman run --rm -it --name redis -p 6379:6379 redis:7-alpine
```

```bash
# Build the entire project
cd redis-client-feature-pack
mvn clean install -DskipTests -Denforcer.skip
```

```bash
# Provision and run the example
cd redis-client-example
mvn clean wildfly:provision wildfly:dev
```

The application starts at `http://localhost:8080/redis-example/api/redis`. You can use it as-is to experiment with the subsystem, or copy it as a starting point for your own application.

For example, you can try adding some entry into Redis and then reading it back:
1. http://localhost:8080/redis-example/api/redis/set/some-entry/some-value
2. http://localhost:8080/redis-example/api/redis/get/some-entry : should return "some-value"

### 3. Provision a WildFly server with Redis support

Add the feature pack to your application's `pom.xml` using the `wildfly-maven-plugin`:

```xml
<plugin>
    <groupId>org.wildfly.plugins</groupId>
    <artifactId>wildfly-maven-plugin</artifactId>
    <version>5.1.1.Final</version>
    <configuration>
        <feature-packs>
            <feature-pack>
                <groupId>org.wildfly</groupId>
                <artifactId>wildfly-galleon-pack</artifactId>
                <version>40.0.0.Final</version>
            </feature-pack>
            <feature-pack>
                <groupId>org.wildfly.redis</groupId>
                <artifactId>redis-client-feature-pack</artifactId>
                <version>1.0.0-SNAPSHOT</version>
            </feature-pack>
        </feature-packs>
        <layers>
            <layer>jaxrs-server</layer>
            <layer>redis-client</layer>
        </layers>
    </configuration>
</plugin>
```

### 4. Configure the Redis connection

After provisioning, the `redis-client` subsystem appears in `standalone.xml`. Add a named connection:

```xml
<subsystem xmlns="urn:jboss:domain:redis-client:1.0">
    <redis-connection name="default"
        cluster-nodes="${redis.cluster.nodes:127.0.0.1:6379}"/>
</subsystem>
```

You can also configure it via the WildFly CLI:

```
/subsystem=redis-client/redis-connection=default:add(cluster-nodes="${redis.cluster.nodes:127.0.0.1:6379}")
```

### 5. Add the injection dependency to your application

```xml
<dependency>
    <groupId>org.wildfly.redis</groupId>
    <artifactId>redis-client-injection</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>5.2.0</version>
    <scope>provided</scope>
</dependency>
```

### 6. Use Redis in your servlet or JAX-RS resource

```java
package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.wildfly.extension.redis.injection.RedisConnection;
import redis.clients.jedis.UnifiedJedis;

@WebServlet("/redis")
public class RedisServlet extends HttpServlet {

    @Inject
    @RedisConnection("default")
    private UnifiedJedis jedis;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String action = req.getParameter("action");
        String key = req.getParameter("key");
        String value = req.getParameter("value");

        resp.setContentType("text/plain");

        switch (action != null ? action : "ping") {
            case "set":
                jedis.set(key, value);
                resp.getWriter().println("OK - stored " + key + "=" + value);
                break;
            case "get":
                String result = jedis.get(key);
                resp.getWriter().println(key + "=" + (result != null ? result : "(nil)"));
                break;
            case "del":
                jedis.del(key);
                resp.getWriter().println("OK - deleted " + key);
                break;
            default:
                String pong = jedis.ping();
                resp.getWriter().println("PING -> " + pong);
                break;
        }
    }
}
```

Try it out:

```bash
# Ping Redis
curl "http://localhost:8080/your-app/redis"

# Store a value
curl "http://localhost:8080/your-app/redis?action=set&key=greeting&value=hello"

# Retrieve it
curl "http://localhost:8080/your-app/redis?action=get&key=greeting"

# Delete it
curl "http://localhost:8080/your-app/redis?action=del&key=greeting"
```

## Subsystem Configuration Reference

Each `<redis-connection>` element supports the following attributes:

| Attribute            | Type    | Default    | Description                                                          |
|----------------------|---------|------------|----------------------------------------------------------------------|
| `name`               | string  | (required) | Connection name, used with `@RedisConnection("name")`               |
| `cluster-nodes`      | string  | (required) | Comma-separated `host:port` pairs (single node or cluster)           |
| `password`           | string  | (none)     | Authentication password                                              |
| `ssl`                | boolean | `false`    | Enable SSL/TLS                                                       |
| `connection-timeout` | int     | `2000`     | Connection timeout in milliseconds                                   |
| `max-pool-size`      | int     | `8`        | Maximum connections in the pool                                      |
| `min-idle`           | int     | `0`        | Minimum idle connections                                             |

All attributes support WildFly expressions, so you can use system properties or environment variables:

```xml
<redis-connection name="default"
    cluster-nodes="${redis.cluster.nodes:127.0.0.1:6379}"
    password="${env.REDIS_PASSWORD}"/>
```

The `cluster-nodes` attribute is used for both single-node and cluster deployments. When a single `host:port` is provided, the subsystem creates a `JedisPooled` client; when multiple comma-separated `host:port` pairs are provided, it creates a `JedisCluster` client. Both are injected as `UnifiedJedis`, so your application code works the same way regardless of the mode.

## Connecting to a Redis Cluster

To connect to a Redis Cluster, provide multiple bootstrap nodes in `cluster-nodes`:

```xml
<subsystem xmlns="urn:jboss:domain:redis-client:1.0">
    <redis-connection name="default"
        cluster-nodes="redis1:7000,redis2:7001,redis3:7002"
        max-pool-size="8"/>
</subsystem>
```

Or via the WildFly CLI:

```
/subsystem=redis-client/redis-connection=default:add(cluster-nodes="127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002")
```

### Running a Redis Cluster locally with Podman

A Redis Cluster requires a minimum of 3 master nodes. You can spin one up on your laptop using Podman with host networking so that all nodes share `127.0.0.1` and can discover each other:

```bash
# Start 3 Redis nodes on the host network
for port in 7000 7001 7002; do
  podman run -d --rm --name "redis-${port}" --network host \
    redis:7-alpine \
    redis-server \
      --port "${port}" \
      --cluster-enabled yes \
      --cluster-config-file "nodes-${port}.conf" \
      --cluster-node-timeout 5000 \
      --appendonly yes
done

# Form the cluster
podman exec -it redis-7000 \
  redis-cli --cluster create \
    127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 \
    --cluster-replicas 0 --cluster-yes
```

Then configure the subsystem to connect to it by setting the system property:

```bash
-Dredis.cluster.nodes=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002
```

To tear it all down:

```bash
podman rm -f redis-7000 redis-7001 redis-7002
```

## Running the Example Application

The project includes a ready-to-run example:

```bash
# Start Redis
podman run -d --name redis -p 6379:6379 redis:7-alpine

# Build everything
mvn clean install -DskipTests

# Provision and run the example
cd redis-client-example
mvn clean wildfly:provision wildfly:dev
```

Then in another terminal:

```bash
curl http://localhost:8080/redis-example/api/redis/set/mykey/myvalue
# OK

curl http://localhost:8080/redis-example/api/redis/get/mykey
# myvalue
```

## Running the Integration Tests

The test suite uses [Testcontainers](https://testcontainers.com/) to start Redis containers automatically. On Fedora/RHEL with Podman, enable the Podman socket and configure the environment before running the tests:

```bash
systemctl --user start podman.socket
export DOCKER_HOST=unix:///run/user/$UID/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
```

Then run:

```bash
mvn clean install -Denforcer.skip
```

The test suite includes:

- **Single-node test** (`RedisSingleNodeIT`): Verifies `RedisClientConfig` creates a `JedisPooled` client against a single Redis container
- **Cluster test** (`RedisClusterIT`): Verifies `RedisClientConfig` creates a `JedisCluster` client against a 3-node Redis Cluster (Linux only)
- **WildFly integration test** (`RedisSubsystemIT`): Verifies CDI injection of `UnifiedJedis` in a provisioned WildFly server

## Project Structure

```
redis-client-feature-pack/
├── redis-client/
│   ├── injection/          CDI qualifier, config, portable extension
│   └── subsystem/          WildFly extension and subsystem implementation
├── redis-client-feature-pack/  Galleon feature pack (layers, JBoss modules)
├── redis-client-example/       Sample JAX-RS application
└── redis-client-testsuite/     Integration tests (Arquillian + Testcontainers)
```

## License

Apache License, Version 2.0
