# Redis Client Feature Pack for WildFly

A WildFly Galleon feature pack that provides Redis client support via [Jedis](https://github.com/redis/jedis). It adds a `redis-client` subsystem to WildFly that manages Redis connection pools and makes them injectable into your Jakarta EE applications via CDI.

## Prerequisites

- Java 17+
- Maven 3.9+
- Podman (for running Redis locally)

## Quick Start

### 1. Start a local Redis instance

```bash
podman run -d --name redis -p 6379:6379 redis:7-alpine
```

### 2. Build the feature pack

```bash
cd redis-client-feature-pack
mvn clean install -DskipTests
```

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
        redis-host="localhost"
        port="6379"
        database="0"
        max-pool-size="8"/>
</subsystem>
```

You can also configure it via the WildFly CLI:

```
/subsystem=redis-client/redis-connection=default:add(redis-host=localhost, port=6379)
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
import redis.clients.jedis.JedisPooled;

@WebServlet("/redis")
public class RedisServlet extends HttpServlet {

    @Inject
    @RedisConnection("default")
    private JedisPooled jedis;

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

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `name` | string | (required) | Connection name, used with `@RedisConnection("name")` |
| `redis-host` | string | `localhost` | Redis server hostname or IP |
| `port` | int | `6379` | Redis server port |
| `password` | string | (none) | Authentication password |
| `database` | int | `0` | Redis database index (0-15) |
| `ssl` | boolean | `false` | Enable SSL/TLS |
| `connection-timeout` | int | `2000` | Connection timeout in milliseconds |
| `max-pool-size` | int | `8` | Maximum connections in the pool |
| `min-idle` | int | `0` | Minimum idle connections |

All attributes support WildFly expressions, so you can use system properties or environment variables:

```xml
<redis-connection name="default"
    redis-host="${env.REDIS_HOST:localhost}"
    port="${env.REDIS_PORT:6379}"
    password="${env.REDIS_PASSWORD}"/>
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
mvn wildfly:provision wildfly:dev
```

Then in another terminal:

```bash
curl http://localhost:8080/redis-example/api/redis/set/mykey/myvalue
# OK

curl http://localhost:8080/redis-example/api/redis/get/mykey
# myvalue
```

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
