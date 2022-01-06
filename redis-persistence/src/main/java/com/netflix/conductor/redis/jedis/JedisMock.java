/*
 * Copyright 2020 Netflix, Inc.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.rarefiedredis.redis.IRedisClient;
import org.rarefiedredis.redis.IRedisSortedSet.ZsetPair;
import org.rarefiedredis.redis.RedisMock;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.Tuple;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.ZAddParams;

public class JedisMock extends Jedis {

    private final IRedisClient redis;

    public JedisMock() {
        super("");
        this.redis = new RedisMock();
    }

    private Set<Tuple> toTupleSet(Set<ZsetPair> pairs) {
        Set<Tuple> set = new HashSet<>();
        for (ZsetPair pair : pairs) {
            set.add(new Tuple(pair.member, pair.score));
        }
        return set;
    }

    @Override
    public String set(final String key, String value) {
        try {
            return redis.set(key, value);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String get(final String key) {
        try {
            return redis.get(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Boolean exists(final String key) {
        try {
            return redis.exists(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long del(final String... keys) {
        try {
            return redis.del(keys);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long del(String key) {
        try {
            return redis.del(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String type(final String key) {
        try {
            return redis.type(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long expire(final String key, final int seconds) {
        try {
            return redis.expire(key, seconds) ? 1L : 0L;
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long expireAt(final String key, final long unixTime) {
        try {
            return redis.expireat(key, unixTime) ? 1L : 0L;
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long ttl(final String key) {
        try {
            return redis.ttl(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long move(final String key, final int dbIndex) {
        try {
            return redis.move(key, dbIndex);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String getSet(final String key, final String value) {
        try {
            return redis.getset(key, value);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public List<String> mget(final String... keys) {
        try {
            String[] mget = redis.mget(keys);
            List<String> lst = new ArrayList<>(mget.length);
            for (String get : mget) {
                lst.add(get);
            }
            return lst;
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long setnx(final String key, final String value) {
        try {
            return redis.setnx(key, value);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String setex(final String key, final int seconds, final String value) {
        try {
            return redis.setex(key, seconds, value);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String mset(final String... keysvalues) {
        try {
            return redis.mset(keysvalues);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long msetnx(final String... keysvalues) {
        try {
            return redis.msetnx(keysvalues) ? 1L : 0L;
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long decrBy(final String key, final long integer) {
        try {
            return redis.decrby(key, integer);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long decr(final String key) {
        try {
            return redis.decr(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long incrBy(final String key, final long integer) {
        try {
            return redis.incrby(key, integer);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Double incrByFloat(final String key, final double value) {
        try {
            return Double.parseDouble(redis.incrbyfloat(key, value));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long incr(final String key) {
        try {
            return redis.incr(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long append(final String key, final String value) {
        try {
            return redis.append(key, value);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String substr(final String key, final int start, final int end) {
        try {
            return redis.getrange(key, start, end);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long hset(final String key, final String field, final String value) {
        try {
            return redis.hset(key, field, value) ? 1L : 0L;
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String hget(final String key, final String field) {
        try {
            return redis.hget(key, field);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long hsetnx(final String key, final String field, final String value) {
        try {
            return redis.hsetnx(key, field, value) ? 1L : 0L;
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String hmset(final String key, final Map<String, String> hash) {
        try {
            String field = null, value = null;
            String[] args = new String[(hash.size() - 1) * 2];
            int idx = 0;
            for (String f : hash.keySet()) {
                if (field == null) {
                    field = f;
                    value = hash.get(f);
                    continue;
                }
                args[idx] = f;
                args[idx + 1] = hash.get(f);
                idx += 2;
            }
            return redis.hmset(key, field, value, args);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public List<String> hmget(final String key, final String... fields) {
        try {
            String field = fields[0];
            String[] f = new String[fields.length - 1];
            for (int idx = 1; idx < fields.length; ++idx) {
                f[idx - 1] = fields[idx];
            }
            return redis.hmget(key, field, f);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long hincrBy(final String key, final String field, final long value) {
        try {
            return redis.hincrby(key, field, value);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Double hincrByFloat(final String key, final String field, final double value) {
        try {
            return Double.parseDouble(redis.hincrbyfloat(key, field, value));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Boolean hexists(final String key, final String field) {
        try {
            return redis.hexists(key, field);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long hdel(final String key, final String... fields) {
        try {
            String field = fields[0];
            String[] f = new String[fields.length - 1];
            for (int idx = 1; idx < fields.length; ++idx) {
                f[idx - 1] = fields[idx];
            }
            return redis.hdel(key, field, f);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long hlen(final String key) {
        try {
            return redis.hlen(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> hkeys(final String key) {
        try {
            return redis.hkeys(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public List<String> hvals(final String key) {
        try {
            return redis.hvals(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Map<String, String> hgetAll(final String key) {
        try {
            return redis.hgetall(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long rpush(final String key, final String... strings) {
        try {
            String element = strings[0];
            String[] elements = new String[strings.length - 1];
            for (int idx = 1; idx < strings.length; ++idx) {
                elements[idx - 1] = strings[idx];
            }
            return redis.rpush(key, element, elements);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long lpush(final String key, final String... strings) {
        try {
            String element = strings[0];
            String[] elements = new String[strings.length - 1];
            for (int idx = 1; idx < strings.length; ++idx) {
                elements[idx - 1] = strings[idx];
            }
            return redis.lpush(key, element, elements);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long llen(final String key) {
        try {
            return redis.llen(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public List<String> lrange(final String key, final long start, final long end) {
        try {
            return redis.lrange(key, start, end);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String ltrim(final String key, final long start, final long end) {
        try {
            return redis.ltrim(key, start, end);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String lindex(final String key, final long index) {
        try {
            return redis.lindex(key, index);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String lset(final String key, final long index, final String value) {
        try {
            return redis.lset(key, index, value);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long lrem(final String key, final long count, final String value) {
        try {
            return redis.lrem(key, count, value);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String lpop(final String key) {
        try {
            return redis.lpop(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String rpop(final String key) {
        try {
            return redis.rpop(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String rpoplpush(final String srckey, final String dstkey) {
        try {
            return redis.rpoplpush(srckey, dstkey);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long sadd(final String key, final String... members) {
        try {
            String member = members[0];
            String[] m = new String[members.length - 1];
            for (int idx = 1; idx < members.length; ++idx) {
                m[idx - 1] = members[idx];
            }
            return redis.sadd(key, member, m);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> smembers(final String key) {
        try {
            return redis.smembers(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long srem(final String key, final String... members) {
        try {
            String member = members[0];
            String[] m = new String[members.length - 1];
            for (int idx = 1; idx < members.length; ++idx) {
                m[idx - 1] = members[idx];
            }
            return redis.srem(key, member, m);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String spop(final String key) {
        try {
            return redis.spop(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long smove(final String srckey, final String dstkey, final String member) {
        try {
            return redis.smove(srckey, dstkey, member) ? 1L : 0L;
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long scard(final String key) {
        try {
            return redis.scard(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Boolean sismember(final String key, final String member) {
        try {
            return redis.sismember(key, member);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> sinter(final String... keys) {
        try {
            String key = keys[0];
            String[] k = new String[keys.length - 1];
            for (int idx = 0; idx < keys.length; ++idx) {
                k[idx - 1] = keys[idx];
            }
            return redis.sinter(key, k);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long sinterstore(final String dstkey, final String... keys) {
        try {
            String key = keys[0];
            String[] k = new String[keys.length - 1];
            for (int idx = 0; idx < keys.length; ++idx) {
                k[idx - 1] = keys[idx];
            }
            return redis.sinterstore(dstkey, key, k);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> sunion(final String... keys) {
        try {
            String key = keys[0];
            String[] k = new String[keys.length - 1];
            for (int idx = 0; idx < keys.length; ++idx) {
                k[idx - 1] = keys[idx];
            }
            return redis.sunion(key, k);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long sunionstore(final String dstkey, final String... keys) {
        try {
            String key = keys[0];
            String[] k = new String[keys.length - 1];
            for (int idx = 0; idx < keys.length; ++idx) {
                k[idx - 1] = keys[idx];
            }
            return redis.sunionstore(dstkey, key, k);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> sdiff(final String... keys) {
        try {
            String key = keys[0];
            String[] k = new String[keys.length - 1];
            for (int idx = 0; idx < keys.length; ++idx) {
                k[idx - 1] = keys[idx];
            }
            return redis.sdiff(key, k);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long sdiffstore(final String dstkey, final String... keys) {
        try {
            String key = keys[0];
            String[] k = new String[keys.length - 1];
            for (int idx = 0; idx < keys.length; ++idx) {
                k[idx - 1] = keys[idx];
            }
            return redis.sdiffstore(dstkey, key, k);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String srandmember(final String key) {
        try {
            return redis.srandmember(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public List<String> srandmember(final String key, final int count) {
        try {
            return redis.srandmember(key, count);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long zadd(final String key, final double score, final String member) {
        try {
            return redis.zadd(key, new ZsetPair(member, score));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long zadd(String key, double score, String member, ZAddParams params) {

        try {

            if (params.getParam("xx") != null) {
                Double existing = redis.zscore(key, member);
                if (existing == null) {
                    return 0L;
                }
                return redis.zadd(key, new ZsetPair(member, score));
            } else {
                return redis.zadd(key, new ZsetPair(member, score));
            }

        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long zadd(final String key, final Map<String, Double> scoreMembers) {
        try {
            Double score = null;
            String member = null;
            List<ZsetPair> scoresmembers = new ArrayList<>((scoreMembers.size() - 1) * 2);
            for (String m : scoreMembers.keySet()) {
                if (m == null) {
                    member = m;
                    score = scoreMembers.get(m);
                    continue;
                }
                scoresmembers.add(new ZsetPair(m, scoreMembers.get(m)));
            }
            return redis.zadd(
                    key, new ZsetPair(member, score), (ZsetPair[]) scoresmembers.toArray());
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> zrange(final String key, final long start, final long end) {
        try {
            return ZsetPair.members(redis.zrange(key, start, end));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long zrem(final String key, final String... members) {
        try {
            String member = members[0];
            String[] ms = new String[members.length - 1];
            for (int idx = 1; idx < members.length; ++idx) {
                ms[idx - 1] = members[idx];
            }
            return redis.zrem(key, member, ms);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Double zincrby(final String key, final double score, final String member) {
        try {
            return Double.parseDouble(redis.zincrby(key, score, member));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long zrank(final String key, final String member) {
        try {
            return redis.zrank(key, member);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long zrevrank(final String key, final String member) {
        try {
            return redis.zrevrank(key, member);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> zrevrange(final String key, final long start, final long end) {
        try {
            return ZsetPair.members(redis.zrevrange(key, start, end));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<Tuple> zrangeWithScores(final String key, final long start, final long end) {
        try {
            return toTupleSet(redis.zrange(key, start, end, "withscores"));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<Tuple> zrevrangeWithScores(final String key, final long start, final long end) {
        try {
            return toTupleSet(redis.zrevrange(key, start, end, "withscores"));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long zcard(final String key) {
        try {
            return redis.zcard(key);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Double zscore(final String key, final String member) {
        try {
            return redis.zscore(key, member);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public String watch(final String... keys) {
        try {
            for (String key : keys) {
                redis.watch(key);
            }
            return "OK";
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long zcount(final String key, final double min, final double max) {
        try {
            return redis.zcount(key, min, max);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long zcount(final String key, final String min, final String max) {
        try {
            return redis.zcount(key, Double.parseDouble(min), Double.parseDouble(max));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> zrangeByScore(final String key, final double min, final double max) {
        try {
            return ZsetPair.members(
                    redis.zrangebyscore(key, String.valueOf(min), String.valueOf(max)));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> zrangeByScore(final String key, final String min, final String max) {
        try {
            return ZsetPair.members(redis.zrangebyscore(key, min, max));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> zrangeByScore(
            final String key,
            final double min,
            final double max,
            final int offset,
            final int count) {
        try {
            return ZsetPair.members(
                    redis.zrangebyscore(
                            key,
                            String.valueOf(min),
                            String.valueOf(max),
                            "limit",
                            String.valueOf(offset),
                            String.valueOf(count)));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> zrangeByScore(
            final String key,
            final String min,
            final String max,
            final int offset,
            final int count) {
        try {
            return ZsetPair.members(
                    redis.zrangebyscore(
                            key, min, max, "limit", String.valueOf(offset), String.valueOf(count)));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(
            final String key, final double min, final double max) {
        try {
            return toTupleSet(
                    redis.zrangebyscore(
                            key, String.valueOf(min), String.valueOf(max), "withscores"));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(
            final String key, final String min, final String max) {
        try {
            return toTupleSet(redis.zrangebyscore(key, min, max, "withscores"));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(
            final String key,
            final double min,
            final double max,
            final int offset,
            final int count) {
        try {
            return toTupleSet(
                    redis.zrangebyscore(
                            key,
                            String.valueOf(min),
                            String.valueOf(max),
                            "limit",
                            String.valueOf(offset),
                            String.valueOf(count),
                            "withscores"));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(
            final String key,
            final String min,
            final String max,
            final int offset,
            final int count) {
        try {
            return toTupleSet(
                    redis.zrangebyscore(
                            key,
                            min,
                            max,
                            "limit",
                            String.valueOf(offset),
                            String.valueOf(count),
                            "withscores"));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> zrevrangeByScore(final String key, final double max, final double min) {
        try {
            return ZsetPair.members(
                    redis.zrevrangebyscore(key, String.valueOf(max), String.valueOf(min)));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> zrevrangeByScore(final String key, final String max, final String min) {
        try {
            return ZsetPair.members(redis.zrevrangebyscore(key, max, min));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> zrevrangeByScore(
            final String key,
            final double max,
            final double min,
            final int offset,
            final int count) {
        try {
            return ZsetPair.members(
                    redis.zrevrangebyscore(
                            key,
                            String.valueOf(max),
                            String.valueOf(min),
                            "limit",
                            String.valueOf(offset),
                            String.valueOf(count)));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(
            final String key, final double max, final double min) {
        try {
            return toTupleSet(
                    redis.zrevrangebyscore(
                            key, String.valueOf(max), String.valueOf(min), "withscores"));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(
            final String key,
            final double max,
            final double min,
            final int offset,
            final int count) {
        try {
            return toTupleSet(
                    redis.zrevrangebyscore(
                            key,
                            String.valueOf(max),
                            String.valueOf(min),
                            "limit",
                            String.valueOf(offset),
                            String.valueOf(count),
                            "withscores"));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(
            final String key,
            final String max,
            final String min,
            final int offset,
            final int count) {
        try {
            return toTupleSet(
                    redis.zrevrangebyscore(
                            key,
                            max,
                            min,
                            "limit",
                            String.valueOf(offset),
                            String.valueOf(count),
                            "withscores"));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<String> zrevrangeByScore(
            final String key,
            final String max,
            final String min,
            final int offset,
            final int count) {
        try {
            return ZsetPair.members(
                    redis.zrevrangebyscore(
                            key, max, min, "limit", String.valueOf(offset), String.valueOf(count)));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(
            final String key, final String max, final String min) {
        try {
            return toTupleSet(redis.zrevrangebyscore(key, max, min, "withscores"));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long zremrangeByRank(final String key, final long start, final long end) {
        try {
            return redis.zremrangebyrank(key, start, end);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long zremrangeByScore(final String key, final double start, final double end) {
        try {
            return redis.zremrangebyscore(key, String.valueOf(start), String.valueOf(end));
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long zremrangeByScore(final String key, final String start, final String end) {
        try {
            return redis.zremrangebyscore(key, start, end);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public Long zunionstore(final String dstkey, final String... sets) {
        try {
            return redis.zunionstore(dstkey, sets.length, sets);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override
    public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
        try {
            org.rarefiedredis.redis.ScanResult<Set<String>> sr =
                    redis.sscan(key, Long.parseLong(cursor), "count", "1000000");
            List<String> list = new ArrayList<>(sr.results);
            return new ScanResult<>("0", list);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    public ScanResult<Entry<String, String>> hscan(final String key, final String cursor) {
        try {
            org.rarefiedredis.redis.ScanResult<Map<String, String>> mockr =
                    redis.hscan(key, Long.parseLong(cursor), "count", "1000000");
            Map<String, String> results = mockr.results;
            List<Entry<String, String>> list = new ArrayList<>(results.entrySet());
            return new ScanResult<>("0", list);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }

    public ScanResult<Tuple> zscan(final String key, final String cursor) {
        try {
            org.rarefiedredis.redis.ScanResult<Set<ZsetPair>> sr =
                    redis.zscan(key, Long.parseLong(cursor), "count", "1000000");
            List<ZsetPair> list = new ArrayList<>(sr.results);
            List<Tuple> tl = new LinkedList<>();
            list.forEach(p -> tl.add(new Tuple(p.member, p.score)));
            return new ScanResult<>("0", tl);
        } catch (Exception e) {
            throw new JedisException(e);
        }
    }
}
