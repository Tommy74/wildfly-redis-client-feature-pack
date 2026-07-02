# Redis Client Feature Pack for WildFly

A WildFly Galleon feature pack that enables **WildFly to offload HTTP session data, SSO tokens, and stateful EJB bean state to Redis**.

By default, WildFly uses Infinispan for distributable session management, which requires setting up and maintaining an Infinispan cluster. This feature pack provides **Redis as a drop-in alternative**: sessions are stored in Redis hashes with TTL-based expiration, so any WildFly node with a Redis connection can serve any session. When a WildFly node goes down, sessions are not lost — they remain in Redis and are immediately available to all surviving nodes. This makes session failover simple and eliminates the need for an Infinispan cluster.

The feature pack also adds a `redis-client` subsystem that manages Redis connection pools with full Jedis support, making `UnifiedJedis` injectable into your Jakarta EE applications via CDI.

## Prerequisites

- Java 17+
- Maven 3.9+
- Podman (for running Redis locally)

### Running the Integration Tests

The test suite uses [Testcontainers](https://testcontainers.com/). On Fedora/RHEL with Podman:

```bash
systemctl --user start podman.socket
export DOCKER_HOST=unix:///run/user/$UID/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true

mvn clean install -Denforcer.skip
```

---

## Part 1: Try It Out

This section gets you up and running in minutes using the included example application. You will see WildFly offloading HTTP session data to Redis — session attributes are stored as Redis hashes, not in-memory on the WildFly node.

### 1. Start Redis

```bash
podman run --rm -it --name redis -p 6379:6379 redis:7-alpine
```

### 2. Build the feature pack

```bash
mvn clean install -DskipTests -Denforcer.skip
```

### 3. Run the example application

```bash
cd redis-client-example
mvn clean wildfly:dev -Denforcer.skip
```

The example provisions a WildFly server with the `redis-client` and `redis-web-clustering` layers, configures a Redis connection, and sets up `distributable-web` to store HTTP sessions in Redis.

### 4. Try the Redis endpoints

The example exposes two sets of REST endpoints:

**Direct Redis access** (`/api/redis`):

```bash
# Store a value
curl http://localhost:8080/redis-example/api/redis/set/mykey/myvalue

# Read it back
curl http://localhost:8080/redis-example/api/redis/get/mykey
# myvalue
```

Check session data is actually in redis:

```bash
podman exec redis redis-cli keys 'wf:session:*'
wf:session:redis-example.war:f975930d-bd00-48c7-98fe-cf78b319707c
````

**HTTP session management** (`/api/session`):

Build and provision the two demo servers (from the `redis-client-example` directory):

```bash
cd redis-client-example
mvn clean package -Denforcer.skip
```

Start Redis (if you haven't yet):

```bash
podman run --rm -it --name redis -p 6379:6379 redis:7-alpine
```

Start wildfly node 1:

```bash
./target/server-1/bin/standalone.sh --stability=community -Djboss.socket.binding.port-offset=100
```

Start wildfly node 2:

```bash
./target/server-2/bin/standalone.sh --stability=community -Djboss.socket.binding.port-offset=200
```

Store session data from wildfly node 2:

```bash
curl -b cookie.txt -c cookie.txt -X PUT http://localhost:8280/redis-example/api/session/color/BLUE
{"key":"color","sessionId":"0dde229f-94d4-4e16-8ffb-6dba49580674","value":"BLUE"}
```

Get the same session data from wildfly node 1:

```bash
curl -b cookie.txt -c cookie.txt http://localhost:8180/redis-example/api/session/color
{"sessionId":"0dde229f-94d4-4e16-8ffb-6dba49580674","value":"BLUE","key":"color"}
```

### What the example provisions

The `redis-client-example/pom.xml` uses the `wildfly-maven-plugin` to provision a server with:

- **Galleon layers**: `jaxrs-server`, `redis-client`, `redis-web-clustering`
- **Redis connection**: `default` connecting to `127.0.0.1:6379`
- **distributable-web**: `redis-session-management` as the default session management provider with `SESSION` granularity and `no-affinity`
- **distributable-web**: `redis-single-sign-on-management` as the default SSO provider

---

## Part 2: Production Configuration with SSL and Session Failover

In production, you want WildFly to **offload all session data to Redis over an encrypted connection**, so that sessions survive node failures and can be served by any WildFly instance in the cluster. This section shows a complete production-ready setup:

- **Session offloading**: `distributable-web` and `distributable-ejb` configured to store HTTP sessions, SSO tokens, and EJB bean state in Redis instead of Infinispan
- **Managed connections**: `remote-destination-outbound-socket-binding` for Redis server addresses — no hardcoded host:port values in the subsystem
- **TLS encryption**: Elytron `client-ssl-context` for encrypted communication between WildFly and Redis
- **Session failover**: multiple WildFly nodes sharing the same Redis backend, so killing one node has zero impact on session availability

### Architecture

```
                     +-----------+
           +-------->|   Redis   |<--------+
           |  TLS    |  Cluster  |   TLS   |
           |         +-----------+         |
           |                               |
    +------+------+                 +------+------+
    |  WildFly 1  |                 |  WildFly 2  |
    |  (node1)    |                 |  (node2)    |
    +-------------+                 +-------------+
         ^                                ^
         |          Load Balancer         |
         +----------+--------+-----------+
                    |        |
                  Users / Clients
```

Sessions are stored in Redis, so when a WildFly node goes down, all other nodes can transparently serve the same sessions.

### Step 1: Start Redis with TLS

Generate certificates and start a TLS-enabled Redis instance:

```bash
# Generate a CA key and self-signed certificate
openssl req -x509 -newkey rsa:2048 -keyout ca-key.pem -out ca-cert.pem \
  -days 365 -nodes -subj '/CN=Redis CA'

# Generate a server key and certificate signed by the CA
openssl req -newkey rsa:2048 -keyout server-key.pem -out server-req.pem \
  -nodes -subj '/CN=localhost'
openssl x509 -req -in server-req.pem -CA ca-cert.pem -CAkey ca-key.pem \
  -CAcreateserial -out server-cert.pem -days 365 \
  -extfile <(echo "subjectAltName=DNS:localhost,IP:127.0.0.1")
rm server-req.pem

# Start Redis with TLS
podman run --rm -it --name redis-tls -p 6380:6379 \
  -v ./ca-cert.pem:/tls/ca-cert.pem:ro \
  -v ./server-cert.pem:/tls/server-cert.pem:ro \
  -v ./server-key.pem:/tls/server-key.pem:ro \
  redis:7-alpine \
  redis-server \
    --tls-port 6379 --port 0 \
    --tls-cert-file /tls/server-cert.pem \
    --tls-key-file /tls/server-key.pem \
    --tls-ca-cert-file /tls/ca-cert.pem
```

### Step 2: Create a truststore for WildFly

Import the CA certificate into a PKCS12 truststore:

```bash
keytool -importcert -alias redis-ca -file ca-cert.pem \
  -keystore redis-truststore.p12 -storetype PKCS12 \
  -storepass changeit -noprompt
```

Copy `redis-truststore.p12` to your WildFly server's configuration directory (`$JBOSS_HOME/standalone/configuration/`).

### Step 3: Provision WildFly with Redis session support

Add the feature pack to your application's `pom.xml`:

```xml
<plugin>
    <groupId>org.wildfly.plugins</groupId>
    <artifactId>wildfly-maven-plugin</artifactId>
    <version>6.0.0.Final</version>
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
            <layer>redis-web-clustering</layer>
        </layers>
    </configuration>
</plugin>
```

The `redis-web-clustering` layer pulls in `web-clustering` (distributable-web subsystem) and `redis-client` (Redis connection management).

### Step 4: Configure outbound socket bindings, Elytron SSL, and Redis session management

Use the WildFly CLI or packaging scripts to configure the server. The example below uses `packagingScripts` in the `wildfly-maven-plugin` so the server is fully configured at provision time:

```xml
<packagingScripts>
    <packaging-script>
        <commands>
            <!-- 1. Outbound socket binding for Redis -->
            <command>/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=redis-server:add(host=${redis.host:localhost},port=${redis.port:6380})</command>

            <!-- 2. Elytron SSL configuration -->
            <command>/subsystem=elytron/key-store=redis-truststore:add(credential-reference={clear-text=changeit},path=redis-truststore.p12,relative-to=jboss.server.config.dir,type=PKCS12)</command>
            <command>/subsystem=elytron/trust-manager=redis-trust-manager:add(key-store=redis-truststore)</command>
            <command>/subsystem=elytron/client-ssl-context=redis-ssl-context:add(trust-manager=redis-trust-manager)</command>

            <!-- 3. Redis connection using socket binding + SSL -->
            <command>/subsystem=redis-client/redis-connection=default:add(outbound-socket-bindings=[redis-server],ssl-context=redis-ssl-context)</command>

            <!-- 4. distributable-web: Redis session management -->
            <command>/subsystem=distributable-web/redis-session-management=default:add(redis-connection=default,granularity=SESSION)</command>
            <command>/subsystem=distributable-web/redis-session-management=default/affinity=no-affinity:add()</command>
            <command>/subsystem=distributable-web:write-attribute(name=default-session-management,value=default)</command>

            <!-- 5. distributable-web: Redis SSO management -->
            <command>/subsystem=distributable-web/redis-single-sign-on-management=default:add(redis-connection=default)</command>
            <command>/subsystem=distributable-web:write-attribute(name=default-single-sign-on-management,value=default)</command>

            <!-- 6. distributable-ejb: Redis bean management -->
            <command>/subsystem=distributable-ejb/redis-bean-management=default:add(redis-connection=default,max-active-beans=10000)</command>
            <command>/subsystem=distributable-ejb:write-attribute(name=default-bean-management,value=default)</command>
        </commands>
    </packaging-script>
</packagingScripts>
```

This produces the following XML configuration in `standalone.xml`:

```xml
<!-- Outbound socket binding -->
<socket-binding-group name="standard-sockets" ...>
    <outbound-socket-binding name="redis-server">
        <remote-destination host="${redis.host:localhost}" port="${redis.port:6380}"/>
    </outbound-socket-binding>
</socket-binding-group>

<!-- Elytron SSL chain -->
<subsystem xmlns="urn:wildfly:elytron:18.0">
    <tls>
        <key-stores>
            <key-store name="redis-truststore">
                <credential-reference clear-text="changeit"/>
                <implementation type="PKCS12"/>
                <file path="redis-truststore.p12" relative-to="jboss.server.config.dir"/>
            </key-store>
        </key-stores>
        <trust-managers>
            <trust-manager name="redis-trust-manager" key-store="redis-truststore"/>
        </trust-managers>
        <client-ssl-contexts>
            <client-ssl-context name="redis-ssl-context" trust-manager="redis-trust-manager"/>
        </client-ssl-contexts>
    </tls>
</subsystem>

<!-- Redis connection using socket binding + SSL context -->
<subsystem xmlns="urn:jboss:domain:redis-client:1.1">
    <redis-connection name="default"
        outbound-socket-bindings="redis-server"
        ssl-context="redis-ssl-context"/>
</subsystem>

<!-- distributable-web with Redis providers -->
<subsystem xmlns="urn:jboss:domain:distributable-web:community:6.0">
    <session-management default="default">
        <redis-session-management name="default" redis-connection="default" granularity="SESSION">
            <no-affinity/>
        </redis-session-management>
    </session-management>
    <single-sign-on-management default="default">
        <redis-single-sign-on-management name="default" redis-connection="default"/>
    </single-sign-on-management>
    <local-routing/>
</subsystem>

<!-- distributable-ejb with Redis bean management -->
<subsystem xmlns="urn:jboss:domain:distributable-ejb:community:3.0">
    <bean-management default="default">
        <redis-bean-management name="default" redis-connection="default" max-active-beans="10000"/>
    </bean-management>
</subsystem>
```

### Step 5: Make your web application distributable

Add `<distributable/>` to your `WEB-INF/web.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0">
    <distributable/>
</web-app>
```

This tells WildFly to use the configured session management provider (Redis in this case) for all HTTP sessions in this application.

### Step 6: Run multiple WildFly nodes

Start two WildFly instances with different port offsets, both pointing to the same Redis:

```bash
# Node 1 (default ports)
$JBOSS_HOME/bin/standalone.sh \
  -Djboss.node.name=node1 \
  -Dredis.host=localhost \
  -Dredis.port=6380

# Node 2 (offset by 100)
$JBOSS_HOME/bin/standalone.sh \
  -Djboss.node.name=node2 \
  -Djboss.socket.binding.port-offset=100 \
  -Djboss.server.data.dir=$JBOSS_HOME/standalone/data-node2 \
  -Djboss.server.log.dir=$JBOSS_HOME/standalone/log-node2 \
  -Djboss.server.temp.dir=$JBOSS_HOME/standalone/tmp-node2 \
  -Dredis.host=localhost \
  -Dredis.port=6380
```

Deploy your distributable `.war` to both nodes. Create a session on node 1, then access it from node 2 — the session data is served from Redis, and survives even if node 1 is killed.

### Connecting to a Redis Cluster

For a multi-node Redis Cluster, define one outbound socket binding per Redis node:

```
/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=redis-1:add(host=redis1.example.com,port=6380)
/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=redis-2:add(host=redis2.example.com,port=6380)
/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=redis-3:add(host=redis3.example.com,port=6380)

/subsystem=redis-client/redis-connection=default:add(outbound-socket-bindings=[redis-1 redis-2 redis-3],ssl-context=redis-ssl-context)
```

When multiple socket bindings are provided, the subsystem creates a `JedisCluster` client. When a single binding is provided, it creates a `JedisPooled` client. Both are injected as `UnifiedJedis`.

---

## Reference

### Subsystem Configuration

Each `<redis-connection>` element supports the following attributes:

| Attribute                  | Type    | Default    | Description                                                     |
|----------------------------|---------|------------|-----------------------------------------------------------------|
| `name`                     | string  | (required) | Connection name, used with `@RedisConnection("name")`           |
| `cluster-nodes`            | string  | (none)     | Comma-separated `host:port` pairs (single node or cluster)      |
| `outbound-socket-bindings` | string  | (none)     | Space-separated outbound socket binding names                   |
| `password`                 | string  | (none)     | Authentication password                                         |
| `ssl`                      | boolean | `false`    | Enable SSL/TLS (uses JVM default trust store)                   |
| `ssl-context`              | string  | (none)     | Reference to an Elytron `client-ssl-context` (implies ssl=true) |
| `connection-timeout`       | int     | `2000`     | Connection timeout in milliseconds                              |
| `max-pool-size`            | int     | `8`        | Maximum connections in the pool                                  |
| `min-idle`                 | int     | `0`        | Minimum idle connections                                         |

Either `cluster-nodes` or `outbound-socket-bindings` must be provided (they are mutually exclusive).

All attributes support WildFly expressions (`${property:default}`).

### Session Management Configuration

**`redis-session-management`** (in `distributable-web` subsystem):

| Attribute          | Type   | Default    | Description                                                       |
|--------------------|--------|------------|-------------------------------------------------------------------|
| `name`             | string | (required) | Provider name, referenced by `default-session-management`         |
| `redis-connection` | string | (required) | Reference to a `redis-connection` in the `redis-client` subsystem |
| `granularity`      | enum   | (required) | `SESSION` (coarse) or `ATTRIBUTE` (fine)                          |
| `marshaller`       | enum   | `JBOSS`    | `JBOSS` or `PROTOSTREAM`                                         |

Affinity child elements: `<no-affinity/>` (recommended for Redis) or `<local-affinity/>`.

**`redis-single-sign-on-management`** (in `distributable-web` subsystem):

| Attribute          | Type   | Default    | Description                                                       |
|--------------------|--------|------------|-------------------------------------------------------------------|
| `name`             | string | (required) | Provider name, referenced by `default-single-sign-on-management`  |
| `redis-connection` | string | (required) | Reference to a `redis-connection` in the `redis-client` subsystem |

**`redis-bean-management`** (in `distributable-ejb` subsystem):

| Attribute          | Type   | Default    | Description                                                       |
|--------------------|--------|------------|-------------------------------------------------------------------|
| `name`             | string   | (required) | Provider name, referenced by `default-bean-management`                        |
| `redis-connection` | string   | (required) | Reference to a `redis-connection` in the `redis-client` subsystem             |
| `max-active-beans` | int      | (none)     | Maximum active beans before passivation                                       |
| `idle-threshold`   | duration | (none)     | ISO 8601 duration after which a bean is eligible for passivation               |

### Using Redis in Application Code

Add the injection dependency to your application:

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

Then inject and use:

```java
import org.wildfly.extension.redis.injection.RedisConnection;
import redis.clients.jedis.UnifiedJedis;

@ApplicationScoped
public class MyService {

    @Inject
    @RedisConnection("default")
    private UnifiedJedis jedis;

    public void store(String key, String value) {
        jedis.set(key, value);
    }

    public String load(String key) {
        return jedis.get(key);
    }
}
```

### Running a Redis Cluster Locally

A Redis Cluster requires a minimum of 3 master nodes. You can spin one up with Podman using host networking:

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

Tear it down with:

```bash
podman rm -f redis-7000 redis-7001 redis-7002
```

## Project Structure

```
redis-client-feature-pack/
├── redis-client/
│   ├── injection/          CDI qualifier, config, portable extension
│   ├── subsystem/          WildFly extension and subsystem implementation
│   └── session/            Redis-backed session/SSO/bean management overlay
├── redis-client-feature-pack/  Galleon feature pack (layers, JBoss modules)
├── redis-client-example/       Sample JAX-RS application with Redis sessions
└── redis-client-testsuite/     Integration tests (Arquillian + Testcontainers)
```

## License

Apache License, Version 2.0
