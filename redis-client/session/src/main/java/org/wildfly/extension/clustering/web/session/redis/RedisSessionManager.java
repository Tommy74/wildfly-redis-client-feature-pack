/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web.session.redis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.wildfly.clustering.cache.batch.Batch;
import org.wildfly.clustering.cache.batch.SuspendedBatch;
import org.wildfly.clustering.function.Supplier;
import org.wildfly.clustering.session.ImmutableSession;
import org.wildfly.clustering.session.Session;
import org.wildfly.clustering.session.SessionManager;
import org.wildfly.clustering.session.SessionManagerConfiguration;
import org.wildfly.clustering.session.SessionStatistics;
import org.wildfly.clustering.web.service.session.SessionManagerFactoryConfiguration;

import redis.clients.jedis.UnifiedJedis;

public class RedisSessionManager<C> implements SessionManager<C> {

    private static final long DEFAULT_MAX_IDLE_SECONDS = 1800;

    private final UnifiedJedis jedis;
    private final String deploymentName;
    private final SessionManagerConfiguration<?> managerConfig;
    private final SessionManagerFactoryConfiguration<C> factoryConfig;
    private volatile boolean started = false;

    public RedisSessionManager(UnifiedJedis jedis, String deploymentName,
                                SessionManagerConfiguration<?> managerConfig,
                                SessionManagerFactoryConfiguration<C> factoryConfig) {
        this.jedis = jedis;
        this.deploymentName = deploymentName;
        this.managerConfig = managerConfig;
        this.factoryConfig = factoryConfig;
    }

    private String sessionKey(String sessionId) {
        return "wf:session:" + this.deploymentName + ":" + sessionId;
    }

    @Override
    public boolean isDistributed() {
        return true;
    }

    @Override
    public CompletionStage<Session<C>> createSessionAsync(String id, Instant creationTime) {
        String key = sessionKey(id);
        if (this.jedis.exists(key)) {
            return CompletableFuture.completedFuture(null);
        }

        long maxIdleSeconds = DEFAULT_MAX_IDLE_SECONDS;

        Map<String, String> data = new HashMap<>();
        data.put("creationTime", String.valueOf(creationTime.toEpochMilli()));
        data.put("lastAccessStart", String.valueOf(creationTime.toEpochMilli()));
        data.put("lastAccessEnd", String.valueOf(creationTime.toEpochMilli()));
        data.put("maxIdle", String.valueOf(maxIdleSeconds));
        data.put("isNew", "true");

        this.jedis.hset(key, data);
        if (maxIdleSeconds > 0) {
            this.jedis.expire(key, maxIdleSeconds);
        }

        return CompletableFuture.completedFuture(new RedisSession<>(this.jedis, key, id, this));
    }

    @Override
    public CompletionStage<Session<C>> findSessionAsync(String id) {
        String key = sessionKey(id);
        if (!this.jedis.exists(key)) {
            return CompletableFuture.completedFuture(null);
        }
        Instant now = Instant.now();
        this.jedis.hset(key, "lastAccessStart", String.valueOf(now.toEpochMilli()));
        this.jedis.hset(key, "isNew", "false");
        return CompletableFuture.completedFuture(new RedisSession<>(this.jedis, key, id, this));
    }

    @Override
    public CompletionStage<ImmutableSession> findImmutableSessionAsync(String id) {
        String key = sessionKey(id);
        if (!this.jedis.exists(key)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(new RedisSession<C>(this.jedis, key, id, this));
    }

    @Override
    public SessionStatistics getStatistics() {
        Set<String> keys = this.jedis.keys("wf:session:" + this.deploymentName + ":*");
        Set<String> sessionIds = new java.util.HashSet<>();
        String prefix = "wf:session:" + this.deploymentName + ":";
        for (String key : keys) {
            sessionIds.add(key.substring(prefix.length()));
        }
        return new SessionStatistics() {
            @Override
            public Set<String> getActiveSessions() {
                return sessionIds;
            }

            @Override
            public Set<String> getSessions() {
                return sessionIds;
            }
        };
    }

    @Override
    public Supplier<String> getIdentifierFactory() {
        return () -> UUID.randomUUID().toString();
    }

    @Override
    public Supplier<Batch> getBatchFactory() {
        return RedisSessionManager::createNoOpBatch;
    }

    private static Batch createNoOpBatch() {
        return new Batch() {
            @Override
            public void close() {
            }

            @Override
            public Status getStatus() {
                return new Status() {
                    @Override
                    public boolean isActive() {
                        return true;
                    }

                    @Override
                    public boolean isDiscarding() {
                        return false;
                    }

                    @Override
                    public boolean isClosed() {
                        return false;
                    }
                };
            }

            @Override
            public SuspendedBatch suspend() {
                return () -> this;
            }

            @Override
            public void discard() {
            }
        };
    }

    @Override
    public boolean isStarted() {
        return this.started;
    }

    @Override
    public void start() {
        this.started = true;
    }

    @Override
    public void stop() {
        this.started = false;
    }

    void removeSession(String key) {
        this.jedis.del(key);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> deserializeAttributes(byte[] data) {
        if (data == null || data.length == 0) {
            return new HashMap<>();
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (Map<String, Object>) ois.readObject();
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    static byte[] serializeAttributes(Map<String, Object> attrs) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(new HashMap<>(attrs));
            oos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
