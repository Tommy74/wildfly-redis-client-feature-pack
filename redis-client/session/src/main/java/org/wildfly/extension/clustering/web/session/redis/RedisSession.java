/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web.session.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.wildfly.clustering.session.Session;
import org.wildfly.clustering.session.SessionMetaData;

import redis.clients.jedis.UnifiedJedis;

public class RedisSession<C> implements Session<C> {

    private final UnifiedJedis jedis;
    private final String key;
    private final String id;
    private final RedisSessionManager<C> manager;
    private final C context;
    private volatile boolean valid = true;

    RedisSession(UnifiedJedis jedis, String key, String id, RedisSessionManager<C> manager, C context) {
        this.jedis = jedis;
        this.key = key;
        this.id = id;
        this.manager = manager;
        this.context = context;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public boolean isValid() {
        return this.valid;
    }

    @Override
    public SessionMetaData getMetaData() {
        return new RedisSessionMetaData(this.jedis, this.key);
    }

    @Override
    public Map<String, Object> getAttributes() {
        String encoded = this.jedis.hget(this.key, "attributes");
        if (encoded == null || encoded.isEmpty()) {
            return new RedisSessionAttributes(this.jedis, this.key, new HashMap<>());
        }
        byte[] data = Base64.getDecoder().decode(encoded);
        Map<String, Object> attrs = RedisSessionManager.deserializeAttributes(data);
        return new RedisSessionAttributes(this.jedis, this.key, attrs);
    }

    @Override
    public void invalidate() {
        this.valid = false;
        this.manager.removeSession(this.key);
    }

    @Override
    public C getContext() {
        return this.context;
    }

    @Override
    public void close() {
        if (this.valid) {
            Instant now = Instant.now();
            this.jedis.hset(this.key, "lastAccessEnd", String.valueOf(now.toEpochMilli()));
            String maxIdleStr = this.jedis.hget(this.key, "maxIdle");
            if (maxIdleStr != null) {
                long maxIdle = Long.parseLong(maxIdleStr);
                if (maxIdle > 0) {
                    this.jedis.expire(this.key, maxIdle);
                }
            }
        }
    }

    private static class RedisSessionAttributes extends HashMap<String, Object> {
        private final UnifiedJedis jedis;
        private final String key;

        RedisSessionAttributes(UnifiedJedis jedis, String key, Map<String, Object> initial) {
            super(initial);
            this.jedis = jedis;
            this.key = key;
        }

        @Override
        public Object put(String name, Object value) {
            Object old = super.put(name, value);
            flush();
            return old;
        }

        @Override
        public Object remove(Object name) {
            Object old = super.remove(name);
            flush();
            return old;
        }

        private void flush() {
            byte[] data = RedisSessionManager.serializeAttributes(this);
            this.jedis.hset(this.key, "attributes", Base64.getEncoder().encodeToString(data));
        }
    }

    private static class RedisSessionMetaData implements SessionMetaData {
        private final UnifiedJedis jedis;
        private final String key;

        RedisSessionMetaData(UnifiedJedis jedis, String key) {
            this.jedis = jedis;
            this.key = key;
        }

        @Override
        public Instant getCreationTime() {
            String val = this.jedis.hget(this.key, "creationTime");
            return val != null ? Instant.ofEpochMilli(Long.parseLong(val)) : Instant.now();
        }

        @Override
        public Optional<Instant> getLastAccessStartTime() {
            String val = this.jedis.hget(this.key, "lastAccessStart");
            return val != null ? Optional.of(Instant.ofEpochMilli(Long.parseLong(val))) : Optional.empty();
        }

        @Override
        public Optional<Instant> getLastAccessEndTime() {
            String val = this.jedis.hget(this.key, "lastAccessEnd");
            return val != null ? Optional.of(Instant.ofEpochMilli(Long.parseLong(val))) : Optional.empty();
        }

        @Override
        public Optional<Duration> getMaxIdle() {
            String val = this.jedis.hget(this.key, "maxIdle");
            return val != null ? Optional.of(Duration.ofSeconds(Long.parseLong(val))) : Optional.empty();
        }

        @Override
        public boolean isExpired() {
            return !this.jedis.exists(this.key);
        }

        @Override
        public void setLastAccess(Instant startTime, Instant endTime) {
            this.jedis.hset(this.key, "lastAccessStart", String.valueOf(startTime.toEpochMilli()));
            this.jedis.hset(this.key, "lastAccessEnd", String.valueOf(endTime.toEpochMilli()));
        }

        @Override
        public void setMaxIdle(Duration maxIdle) {
            this.jedis.hset(this.key, "maxIdle", String.valueOf(maxIdle.getSeconds()));
            if (maxIdle.getSeconds() > 0) {
                this.jedis.expire(this.key, maxIdle.getSeconds());
            }
        }
    }
}
