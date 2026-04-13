/*
 * Copyright 2020 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.redis.jedis;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.resps.Tuple;

/**
 * In-memory implementation of {@link JedisCommands} for testing without a real Redis instance.
 * Provides basic Redis data-structure semantics using ConcurrentHashMaps.
 */
public class InMemoryJedisCommands implements JedisCommands {

    private final ConcurrentHashMap<String, String> strings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, byte[]> bytesStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> hashes =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> sets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<ScoredMember>> sortedSets =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkedList<String>> lists = new ConcurrentHashMap<>();

    private record ScoredMember(String member, double score) implements Comparable<ScoredMember> {
        @Override
        public int compareTo(ScoredMember o) {
            int c = Double.compare(score, o.score);
            return c != 0 ? c : member.compareTo(o.member);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ScoredMember sm && member.equals(sm.member);
        }

        @Override
        public int hashCode() {
            return member.hashCode();
        }
    }

    // --- String commands ---

    @Override
    public String set(String key, String value) {
        strings.put(key, value);
        return "OK";
    }

    @Override
    public String set(String key, String value, SetParams params) {
        // Simplified: ignores EX/PX/NX/XX for in-memory testing
        Boolean nx = getField(params, "nx");
        if (nx != null && nx && strings.containsKey(key)) {
            return null;
        }
        strings.put(key, value);
        return "OK";
    }

    @Override
    public String set(byte[] key, byte[] value, SetParams params) {
        String k = new String(key, StandardCharsets.UTF_8);
        Boolean nx = getField(params, "nx");
        if (nx != null && nx && bytesStore.containsKey(k)) {
            return null;
        }
        bytesStore.put(k, value);
        return "OK";
    }

    @Override
    public String set(byte[] key, byte[] value) {
        bytesStore.put(new String(key, StandardCharsets.UTF_8), value);
        return "OK";
    }

    @Override
    public void mset(byte[]... keyvalues) {
        for (int i = 0; i < keyvalues.length; i += 2) {
            bytesStore.put(new String(keyvalues[i], StandardCharsets.UTF_8), keyvalues[i + 1]);
        }
    }

    @Override
    public String get(String key) {
        return strings.get(key);
    }

    @Override
    public byte[] getBytes(byte[] key) {
        return bytesStore.get(new String(key, StandardCharsets.UTF_8));
    }

    @Override
    public List<String> mget(String[] keys) {
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            result.add(strings.get(key));
        }
        return result;
    }

    @Override
    public List<byte[]> mgetBytes(byte[]... keys) {
        List<byte[]> result = new ArrayList<>();
        for (byte[] key : keys) {
            result.add(bytesStore.get(new String(key, StandardCharsets.UTF_8)));
        }
        return result;
    }

    @Override
    public Boolean exists(String key) {
        return strings.containsKey(key)
                || hashes.containsKey(key)
                || sets.containsKey(key)
                || sortedSets.containsKey(key)
                || bytesStore.containsKey(key);
    }

    @Override
    public Long persist(String key) {
        return exists(key) ? 1L : 0L;
    }

    @Override
    public Long expire(String key, long seconds) {
        // In-memory: no TTL tracking
        return exists(key) ? 1L : 0L;
    }

    @Override
    public Long ttl(String key) {
        return -1L;
    }

    @Override
    public Long setnx(String key, String value) {
        return strings.putIfAbsent(key, value) == null ? 1L : 0L;
    }

    @Override
    public Long incrBy(String key, long increment) {
        AtomicLong counter =
                counters.computeIfAbsent(
                        key,
                        k -> {
                            String existing = strings.get(k);
                            return new AtomicLong(existing != null ? Long.parseLong(existing) : 0);
                        });
        long newVal = counter.addAndGet(increment);
        strings.put(key, String.valueOf(newVal));
        return newVal;
    }

    @Override
    public Long del(String key) {
        boolean removed =
                strings.remove(key) != null
                        || hashes.remove(key) != null
                        || sets.remove(key) != null
                        || sortedSets.remove(key) != null
                        || bytesStore.remove(key) != null;
        counters.remove(key);
        return removed ? 1L : 0L;
    }

    // --- Hash commands ---

    @Override
    public Long hset(String key, String field, String value) {
        ConcurrentHashMap<String, String> hash =
                hashes.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        return hash.put(field, value) == null ? 1L : 0L;
    }

    @Override
    public Long hset(String key, Map<String, String> fieldValues) {
        ConcurrentHashMap<String, String> hash =
                hashes.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        long added = 0;
        for (Map.Entry<String, String> e : fieldValues.entrySet()) {
            if (hash.put(e.getKey(), e.getValue()) == null) added++;
        }
        return added;
    }

    @Override
    public String hget(String key, String field) {
        ConcurrentHashMap<String, String> hash = hashes.get(key);
        return hash != null ? hash.get(field) : null;
    }

    @Override
    public Long hsetnx(String key, String field, String value) {
        ConcurrentHashMap<String, String> hash =
                hashes.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        return hash.putIfAbsent(field, value) == null ? 1L : 0L;
    }

    @Override
    public Long hincrBy(String key, String field, long value) {
        ConcurrentHashMap<String, String> hash =
                hashes.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        String current = hash.getOrDefault(field, "0");
        long newVal = Long.parseLong(current) + value;
        hash.put(field, String.valueOf(newVal));
        return newVal;
    }

    @Override
    public Boolean hexists(String key, String field) {
        ConcurrentHashMap<String, String> hash = hashes.get(key);
        return hash != null && hash.containsKey(field);
    }

    @Override
    public Long hdel(String key, String... fields) {
        ConcurrentHashMap<String, String> hash = hashes.get(key);
        if (hash == null) return 0L;
        long removed = 0;
        for (String field : fields) {
            if (hash.remove(field) != null) removed++;
        }
        return removed;
    }

    @Override
    public Long hlen(String key) {
        ConcurrentHashMap<String, String> hash = hashes.get(key);
        return hash != null ? (long) hash.size() : 0L;
    }

    @Override
    public List<String> hvals(String key) {
        ConcurrentHashMap<String, String> hash = hashes.get(key);
        return hash != null ? new ArrayList<>(hash.values()) : new ArrayList<>();
    }

    @Override
    public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor) {
        return hscan(key, cursor, new ScanParams().count(100));
    }

    @Override
    public ScanResult<Map.Entry<String, String>> hscan(
            String key, String cursor, ScanParams params) {
        ConcurrentHashMap<String, String> hash = hashes.get(key);
        if (hash == null) {
            return new ScanResult<>("0", new ArrayList<>());
        }
        // Return all entries in one scan (simplified)
        List<Map.Entry<String, String>> entries = new ArrayList<>(hash.entrySet());
        return new ScanResult<>("0", entries);
    }

    // --- Set commands ---

    @Override
    public Long sadd(String key, String... members) {
        Set<String> set = sets.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        long added = 0;
        for (String m : members) {
            if (set.add(m)) added++;
        }
        return added;
    }

    @Override
    public Set<String> smembers(String key) {
        Set<String> set = sets.get(key);
        return set != null ? new HashSet<>(set) : new HashSet<>();
    }

    @Override
    public Long srem(String key, String... members) {
        Set<String> set = sets.get(key);
        if (set == null) return 0L;
        long removed = 0;
        for (String m : members) {
            if (set.remove(m)) removed++;
        }
        return removed;
    }

    @Override
    public Long scard(String key) {
        Set<String> set = sets.get(key);
        return set != null ? (long) set.size() : 0L;
    }

    @Override
    public Boolean sismember(String key, String member) {
        Set<String> set = sets.get(key);
        return set != null && set.contains(member);
    }

    @Override
    public ScanResult<String> sscan(String key, String cursor) {
        return sscan(key, cursor, new ScanParams().count(100));
    }

    @Override
    public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
        Set<String> set = sets.get(key);
        if (set == null) {
            return new ScanResult<>("0", new ArrayList<>());
        }
        return new ScanResult<>("0", new ArrayList<>(set));
    }

    // --- Sorted set commands ---

    @Override
    public Long zadd(String key, double score, String member) {
        ConcurrentSkipListSet<ScoredMember> zset =
                sortedSets.computeIfAbsent(key, k -> new ConcurrentSkipListSet<>());
        ScoredMember sm = new ScoredMember(member, score);
        zset.remove(sm); // Remove old score if present
        return zset.add(sm) ? 1L : 0L;
    }

    @Override
    public Long zadd(String key, Map<String, Double> scores) {
        long added = 0;
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            added += zadd(key, e.getValue(), e.getKey());
        }
        return added;
    }

    @Override
    public Long zadd(String key, double score, String member, ZAddParams params) {
        // Simplified NX support
        ConcurrentSkipListSet<ScoredMember> zset =
                sortedSets.computeIfAbsent(key, k -> new ConcurrentSkipListSet<>());
        ScoredMember sm = new ScoredMember(member, score);
        // NX: only add if not exists
        if (zset.contains(sm)) {
            zset.remove(sm);
        }
        return zset.add(sm) ? 1L : 0L;
    }

    @Override
    public List<String> zrange(String key, long start, long stop) {
        ConcurrentSkipListSet<ScoredMember> zset = sortedSets.get(key);
        if (zset == null) return new ArrayList<>();
        List<ScoredMember> list = new ArrayList<>(zset);
        int size = list.size();
        int from = (int) (start < 0 ? Math.max(0, size + start) : Math.min(start, size));
        int to = (int) (stop < 0 ? size + stop + 1 : Math.min(stop + 1, size));
        if (from >= to) return new ArrayList<>();
        return list.subList(from, to).stream()
                .map(ScoredMember::member)
                .collect(Collectors.toList());
    }

    @Override
    public Long zrem(String key, String... members) {
        ConcurrentSkipListSet<ScoredMember> zset = sortedSets.get(key);
        if (zset == null) return 0L;
        long removed = 0;
        for (String m : members) {
            if (zset.removeIf(sm -> sm.member.equals(m))) removed++;
        }
        return removed;
    }

    @Override
    public List<Tuple> zrangeWithScores(String key, long start, long stop) {
        ConcurrentSkipListSet<ScoredMember> zset = sortedSets.get(key);
        if (zset == null) return new ArrayList<>();
        List<ScoredMember> list = new ArrayList<>(zset);
        int size = list.size();
        int from = (int) (start < 0 ? Math.max(0, size + start) : Math.min(start, size));
        int to = (int) (stop < 0 ? size + stop + 1 : Math.min(stop + 1, size));
        if (from >= to) return new ArrayList<>();
        return list.subList(from, to).stream()
                .map(sm -> new Tuple(sm.member, sm.score))
                .collect(Collectors.toList());
    }

    @Override
    public Long zcard(String key) {
        ConcurrentSkipListSet<ScoredMember> zset = sortedSets.get(key);
        return zset != null ? (long) zset.size() : 0L;
    }

    @Override
    public Long zcount(String key, double min, double max) {
        ConcurrentSkipListSet<ScoredMember> zset = sortedSets.get(key);
        if (zset == null) return 0L;
        return zset.stream().filter(sm -> sm.score >= min && sm.score <= max).count();
    }

    @Override
    public Double zscore(String key, String member) {
        ConcurrentSkipListSet<ScoredMember> zset = sortedSets.get(key);
        if (zset == null) return null;
        return zset.stream()
                .filter(sm -> sm.member.equals(member))
                .findFirst()
                .map(sm -> sm.score)
                .orElse(null);
    }

    @Override
    public List<String> zrangeByScore(String key, double min, double max) {
        ConcurrentSkipListSet<ScoredMember> zset = sortedSets.get(key);
        if (zset == null) return new ArrayList<>();
        return zset.stream()
                .filter(sm -> sm.score >= min && sm.score <= max)
                .map(ScoredMember::member)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> zrangeByScore(String key, double min, double max, int offset, int count) {
        ConcurrentSkipListSet<ScoredMember> zset = sortedSets.get(key);
        if (zset == null) return new ArrayList<>();
        return zset.stream()
                .filter(sm -> sm.score >= min && sm.score <= max)
                .skip(offset)
                .limit(count)
                .map(ScoredMember::member)
                .collect(Collectors.toList());
    }

    @Override
    public List<Tuple> zrangeByScoreWithScores(String key, double min, double max) {
        ConcurrentSkipListSet<ScoredMember> zset = sortedSets.get(key);
        if (zset == null) return new ArrayList<>();
        return zset.stream()
                .filter(sm -> sm.score >= min && sm.score <= max)
                .map(sm -> new Tuple(sm.member, sm.score))
                .collect(Collectors.toList());
    }

    @Override
    public Long zremrangeByScore(String key, String min, String max) {
        ConcurrentSkipListSet<ScoredMember> zset = sortedSets.get(key);
        if (zset == null) return 0L;
        double dMin = Double.parseDouble(min);
        double dMax = Double.parseDouble(max);
        long count = zset.stream().filter(sm -> sm.score >= dMin && sm.score <= dMax).count();
        zset.removeIf(sm -> sm.score >= dMin && sm.score <= dMax);
        return count;
    }

    @Override
    public ScanResult<Tuple> zscan(String key, String cursor) {
        ConcurrentSkipListSet<ScoredMember> zset = sortedSets.get(key);
        if (zset == null) {
            return new ScanResult<>("0", new ArrayList<>());
        }
        List<Tuple> tuples =
                zset.stream()
                        .map(sm -> new Tuple(sm.member, sm.score))
                        .collect(Collectors.toList());
        return new ScanResult<>("0", tuples);
    }

    // --- Scan command ---

    @Override
    public ScanResult<String> scan(String keyPrefix, String cursor, int count) {
        // Scan across all key types matching prefix pattern
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(strings.keySet());
        allKeys.addAll(hashes.keySet());
        allKeys.addAll(sets.keySet());
        allKeys.addAll(sortedSets.keySet());
        allKeys.addAll(bytesStore.keySet());

        String pattern = keyPrefix.replace("*", ".*");
        List<String> matched =
                allKeys.stream()
                        .filter(k -> k.matches(pattern))
                        .limit(count)
                        .collect(Collectors.toList());
        return new ScanResult<>("0", matched);
    }

    // --- List commands ---

    @Override
    public Long llen(String key) {
        LinkedList<String> list = lists.get(key);
        return list != null ? (long) list.size() : 0L;
    }

    @Override
    public Long rpush(String key, String... values) {
        LinkedList<String> list = lists.computeIfAbsent(key, k -> new LinkedList<>());
        for (String value : values) {
            list.addLast(value);
        }
        return (long) list.size();
    }

    @Override
    public List<String> lrange(String key, long start, long end) {
        LinkedList<String> list = lists.get(key);
        if (list == null || list.isEmpty()) return new ArrayList<>();
        int size = list.size();
        int s = (int) Math.max(start, 0);
        int e = end < 0 ? size + (int) end : (int) Math.min(end, size - 1);
        if (s > e) return new ArrayList<>();
        return new ArrayList<>(list.subList(s, e + 1));
    }

    @Override
    public String ltrim(String key, long start, long end) {
        LinkedList<String> list = lists.get(key);
        if (list == null) return "OK";
        int size = list.size();
        int s = (int) Math.max(start, 0);
        int e = end < 0 ? size + (int) end : (int) Math.min(end, size - 1);
        if (s > e) {
            list.clear();
        } else {
            List<String> kept = new ArrayList<>(list.subList(s, e + 1));
            list.clear();
            list.addAll(kept);
        }
        return "OK";
    }

    // --- Lua scripting (no-op for in-memory) ---

    @Override
    public Object evalsha(String sha1, List<String> keys, List<String> args) {
        return null;
    }

    @Override
    public Object evalsha(byte[] sha1, List<byte[]> keys, List<byte[]> args) {
        return null;
    }

    @Override
    public byte[] scriptLoad(byte[] script, byte[] sampleKey) {
        return new byte[0];
    }

    // --- Info / replicas ---

    @Override
    public String info(String command) {
        return "";
    }

    @Override
    public int waitReplicas(String key, int replicas, long timeoutInMillis) {
        return replicas;
    }

    @Override
    public String ping() {
        return "PONG";
    }

    // --- Helpers ---

    @SuppressWarnings("unchecked")
    private <T> T getField(SetParams params, String fieldName) {
        try {
            var field = SetParams.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(params);
        } catch (Exception e) {
            return null;
        }
    }
}
