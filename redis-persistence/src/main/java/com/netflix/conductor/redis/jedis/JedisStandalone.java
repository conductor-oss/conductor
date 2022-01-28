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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import redis.clients.jedis.BitPosParams;
import redis.clients.jedis.GeoCoordinate;
import redis.clients.jedis.GeoRadiusResponse;
import redis.clients.jedis.GeoUnit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.ListPosition;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.SortingParams;
import redis.clients.jedis.StreamConsumersInfo;
import redis.clients.jedis.StreamEntry;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.StreamGroupInfo;
import redis.clients.jedis.StreamInfo;
import redis.clients.jedis.StreamPendingEntry;
import redis.clients.jedis.Tuple;
import redis.clients.jedis.commands.JedisCommands;
import redis.clients.jedis.params.GeoRadiusParam;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.params.ZIncrByParams;

/** A {@link JedisCommands} implementation that delegates to {@link JedisPool}. */
public class JedisStandalone implements JedisCommands {

    private final JedisPool jedisPool;

    public JedisStandalone(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    private <R> R executeInJedis(Function<Jedis, R> function) {
        try (Jedis jedis = jedisPool.getResource()) {
            return function.apply(jedis);
        }
    }

    @Override
    public String set(String key, String value) {
        return executeInJedis(jedis -> jedis.set(key, value));
    }

    @Override
    public String set(String key, String value, SetParams params) {
        return executeInJedis(jedis -> jedis.set(key, value, params));
    }

    @Override
    public String get(String key) {
        return executeInJedis(jedis -> jedis.get(key));
    }

    @Override
    public Boolean exists(String key) {
        return executeInJedis(jedis -> jedis.exists(key));
    }

    @Override
    public Long persist(String key) {
        return executeInJedis(jedis -> jedis.persist(key));
    }

    @Override
    public String type(String key) {
        return executeInJedis(jedis -> jedis.type(key));
    }

    @Override
    public byte[] dump(String key) {
        return executeInJedis(jedis -> jedis.dump(key));
    }

    @Override
    public String restore(String key, int ttl, byte[] serializedValue) {
        return executeInJedis(jedis -> jedis.restore(key, ttl, serializedValue));
    }

    @Override
    public String restoreReplace(String key, int ttl, byte[] serializedValue) {
        return executeInJedis(jedis -> jedis.restoreReplace(key, ttl, serializedValue));
    }

    @Override
    public Long expire(String key, int seconds) {
        return executeInJedis(jedis -> jedis.expire(key, seconds));
    }

    @Override
    public Long pexpire(String key, long milliseconds) {
        return executeInJedis(jedis -> jedis.pexpire(key, milliseconds));
    }

    @Override
    public Long expireAt(String key, long unixTime) {
        return executeInJedis(jedis -> jedis.expireAt(key, unixTime));
    }

    @Override
    public Long pexpireAt(String key, long millisecondsTimestamp) {
        return executeInJedis(jedis -> jedis.pexpireAt(key, millisecondsTimestamp));
    }

    @Override
    public Long ttl(String key) {
        return executeInJedis(jedis -> jedis.ttl(key));
    }

    @Override
    public Long pttl(String key) {
        return executeInJedis(jedis -> jedis.pttl(key));
    }

    @Override
    public Long touch(String key) {
        return executeInJedis(jedis -> jedis.touch(key));
    }

    @Override
    public Boolean setbit(String key, long offset, boolean value) {
        return executeInJedis(jedis -> jedis.setbit(key, offset, value));
    }

    @Override
    public Boolean setbit(String key, long offset, String value) {
        return executeInJedis(jedis -> jedis.setbit(key, offset, value));
    }

    @Override
    public Boolean getbit(String key, long offset) {
        return executeInJedis(jedis -> jedis.getbit(key, offset));
    }

    @Override
    public Long setrange(String key, long offset, String value) {
        return executeInJedis(jedis -> jedis.setrange(key, offset, value));
    }

    @Override
    public String getrange(String key, long startOffset, long endOffset) {
        return executeInJedis(jedis -> jedis.getrange(key, startOffset, endOffset));
    }

    @Override
    public String getSet(String key, String value) {
        return executeInJedis(jedis -> jedis.getSet(key, value));
    }

    @Override
    public Long setnx(String key, String value) {
        return executeInJedis(jedis -> jedis.setnx(key, value));
    }

    @Override
    public String setex(String key, int seconds, String value) {
        return executeInJedis(jedis -> jedis.setex(key, seconds, value));
    }

    @Override
    public String psetex(String key, long milliseconds, String value) {
        return executeInJedis(jedis -> jedis.psetex(key, milliseconds, value));
    }

    @Override
    public Long decrBy(String key, long decrement) {
        return executeInJedis(jedis -> jedis.decrBy(key, decrement));
    }

    @Override
    public Long decr(String key) {
        return executeInJedis(jedis -> jedis.decr(key));
    }

    @Override
    public Long incrBy(String key, long increment) {
        return executeInJedis(jedis -> jedis.incrBy(key, increment));
    }

    @Override
    public Double incrByFloat(String key, double increment) {
        return executeInJedis(jedis -> jedis.incrByFloat(key, increment));
    }

    @Override
    public Long incr(String key) {
        return executeInJedis(jedis -> jedis.incr(key));
    }

    @Override
    public Long append(String key, String value) {
        return executeInJedis(jedis -> jedis.append(key, value));
    }

    @Override
    public String substr(String key, int start, int end) {
        return executeInJedis(jedis -> jedis.substr(key, start, end));
    }

    @Override
    public Long hset(String key, String field, String value) {
        return executeInJedis(jedis -> jedis.hset(key, field, value));
    }

    @Override
    public Long hset(String key, Map<String, String> hash) {
        return executeInJedis(jedis -> jedis.hset(key, hash));
    }

    @Override
    public String hget(String key, String field) {
        return executeInJedis(jedis -> jedis.hget(key, field));
    }

    @Override
    public Long hsetnx(String key, String field, String value) {
        return executeInJedis(jedis -> jedis.hsetnx(key, field, value));
    }

    @Override
    public String hmset(String key, Map<String, String> hash) {
        return executeInJedis(jedis -> jedis.hmset(key, hash));
    }

    @Override
    public List<String> hmget(String key, String... fields) {
        return executeInJedis(jedis -> jedis.hmget(key, fields));
    }

    @Override
    public Long hincrBy(String key, String field, long value) {
        return executeInJedis(jedis -> jedis.hincrBy(key, field, value));
    }

    @Override
    public Double hincrByFloat(String key, String field, double value) {
        return executeInJedis(jedis -> jedis.hincrByFloat(key, field, value));
    }

    @Override
    public Boolean hexists(String key, String field) {
        return executeInJedis(jedis -> jedis.hexists(key, field));
    }

    @Override
    public Long hdel(String key, String... field) {
        return executeInJedis(jedis -> jedis.hdel(key, field));
    }

    @Override
    public Long hlen(String key) {
        return executeInJedis(jedis -> jedis.hlen(key));
    }

    @Override
    public Set<String> hkeys(String key) {
        return executeInJedis(jedis -> jedis.hkeys(key));
    }

    @Override
    public List<String> hvals(String key) {
        return executeInJedis(jedis -> jedis.hvals(key));
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        return executeInJedis(jedis -> jedis.hgetAll(key));
    }

    @Override
    public Long rpush(String key, String... string) {
        return executeInJedis(jedis -> jedis.rpush(key));
    }

    @Override
    public Long lpush(String key, String... string) {
        return executeInJedis(jedis -> jedis.lpush(key, string));
    }

    @Override
    public Long llen(String key) {
        return executeInJedis(jedis -> jedis.llen(key));
    }

    @Override
    public List<String> lrange(String key, long start, long stop) {
        return executeInJedis(jedis -> jedis.lrange(key, start, stop));
    }

    @Override
    public String ltrim(String key, long start, long stop) {
        return executeInJedis(jedis -> jedis.ltrim(key, start, stop));
    }

    @Override
    public String lindex(String key, long index) {
        return executeInJedis(jedis -> jedis.lindex(key, index));
    }

    @Override
    public String lset(String key, long index, String value) {
        return executeInJedis(jedis -> jedis.lset(key, index, value));
    }

    @Override
    public Long lrem(String key, long count, String value) {
        return executeInJedis(jedis -> jedis.lrem(key, count, value));
    }

    @Override
    public String lpop(String key) {
        return executeInJedis(jedis -> jedis.lpop(key));
    }

    @Override
    public String rpop(String key) {
        return executeInJedis(jedis -> jedis.rpop(key));
    }

    @Override
    public Long sadd(String key, String... member) {
        return executeInJedis(jedis -> jedis.sadd(key, member));
    }

    @Override
    public Set<String> smembers(String key) {
        return executeInJedis(jedis -> jedis.smembers(key));
    }

    @Override
    public Long srem(String key, String... member) {
        return executeInJedis(jedis -> jedis.srem(key, member));
    }

    @Override
    public String spop(String key) {
        return executeInJedis(jedis -> jedis.spop(key));
    }

    @Override
    public Set<String> spop(String key, long count) {
        return executeInJedis(jedis -> jedis.spop(key, count));
    }

    @Override
    public Long scard(String key) {
        return executeInJedis(jedis -> jedis.scard(key));
    }

    @Override
    public Boolean sismember(String key, String member) {
        return executeInJedis(jedis -> jedis.sismember(key, member));
    }

    @Override
    public String srandmember(String key) {
        return executeInJedis(jedis -> jedis.srandmember(key));
    }

    @Override
    public List<String> srandmember(String key, int count) {
        return executeInJedis(jedis -> jedis.srandmember(key, count));
    }

    @Override
    public Long strlen(String key) {
        return executeInJedis(jedis -> jedis.strlen(key));
    }

    @Override
    public Long zadd(String key, double score, String member) {
        return executeInJedis(jedis -> jedis.zadd(key, score, member));
    }

    @Override
    public Long zadd(String key, double score, String member, ZAddParams params) {
        return executeInJedis(jedis -> jedis.zadd(key, score, member, params));
    }

    @Override
    public Long zadd(String key, Map<String, Double> scoreMembers) {
        return executeInJedis(jedis -> jedis.zadd(key, scoreMembers));
    }

    @Override
    public Long zadd(String key, Map<String, Double> scoreMembers, ZAddParams params) {
        return executeInJedis(jedis -> jedis.zadd(key, scoreMembers, params));
    }

    @Override
    public Set<String> zrange(String key, long start, long stop) {
        return executeInJedis(jedis -> jedis.zrange(key, start, stop));
    }

    @Override
    public Long zrem(String key, String... members) {
        return executeInJedis(jedis -> jedis.zrem(key, members));
    }

    @Override
    public Double zincrby(String key, double increment, String member) {
        return executeInJedis(jedis -> jedis.zincrby(key, increment, member));
    }

    @Override
    public Double zincrby(String key, double increment, String member, ZIncrByParams params) {
        return executeInJedis(jedis -> jedis.zincrby(key, increment, member, params));
    }

    @Override
    public Long zrank(String key, String member) {
        return executeInJedis(jedis -> jedis.zrank(key, member));
    }

    @Override
    public Long zrevrank(String key, String member) {
        return executeInJedis(jedis -> jedis.zrevrank(key, member));
    }

    @Override
    public Set<String> zrevrange(String key, long start, long stop) {
        return executeInJedis(jedis -> jedis.zrevrange(key, start, stop));
    }

    @Override
    public Set<Tuple> zrangeWithScores(String key, long start, long stop) {
        return executeInJedis(jedis -> jedis.zrangeWithScores(key, start, stop));
    }

    @Override
    public Set<Tuple> zrevrangeWithScores(String key, long start, long stop) {
        return executeInJedis(jedis -> jedis.zrevrangeWithScores(key, start, stop));
    }

    @Override
    public Long zcard(String key) {
        return executeInJedis(jedis -> jedis.zcard(key));
    }

    @Override
    public Double zscore(String key, String member) {
        return executeInJedis(jedis -> jedis.zscore(key, member));
    }

    @Override
    public Tuple zpopmax(String key) {
        return executeInJedis(jedis -> jedis.zpopmax(key));
    }

    @Override
    public Set<Tuple> zpopmax(String key, int count) {
        return executeInJedis(jedis -> jedis.zpopmax(key, count));
    }

    @Override
    public Tuple zpopmin(String key) {
        return executeInJedis(jedis -> jedis.zpopmin(key));
    }

    @Override
    public Set<Tuple> zpopmin(String key, int count) {
        return executeInJedis(jedis -> jedis.zpopmin(key, count));
    }

    @Override
    public List<String> sort(String key) {
        return executeInJedis(jedis -> jedis.sort(key));
    }

    @Override
    public List<String> sort(String key, SortingParams sortingParameters) {
        return executeInJedis(jedis -> jedis.sort(key, sortingParameters));
    }

    @Override
    public Long zcount(String key, double min, double max) {
        return executeInJedis(jedis -> jedis.zcount(key, min, max));
    }

    @Override
    public Long zcount(String key, String min, String max) {
        return executeInJedis(jedis -> jedis.zcount(key, min, max));
    }

    @Override
    public Set<String> zrangeByScore(String key, double min, double max) {
        return executeInJedis(jedis -> jedis.zrangeByScore(key, min, max));
    }

    @Override
    public Set<String> zrangeByScore(String key, String min, String max) {
        return executeInJedis(jedis -> jedis.zrangeByScore(key, min, max));
    }

    @Override
    public Set<String> zrevrangeByScore(String key, double max, double min) {
        return executeInJedis(jedis -> jedis.zrevrangeByScore(key, max, min));
    }

    @Override
    public Set<String> zrangeByScore(String key, double min, double max, int offset, int count) {
        return executeInJedis(jedis -> jedis.zrangeByScore(key, min, max, offset, count));
    }

    @Override
    public Set<String> zrevrangeByScore(String key, String max, String min) {
        return executeInJedis(jedis -> jedis.zrevrangeByScore(key, max, min));
    }

    @Override
    public Set<String> zrangeByScore(String key, String min, String max, int offset, int count) {
        return executeInJedis(jedis -> jedis.zrangeByScore(key, min, max, offset, count));
    }

    @Override
    public Set<String> zrevrangeByScore(String key, double max, double min, int offset, int count) {
        return executeInJedis(jedis -> jedis.zrevrangeByScore(key, max, min, offset, count));
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(String key, double min, double max) {
        return executeInJedis(jedis -> jedis.zrangeByScoreWithScores(key, min, max));
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(String key, double max, double min) {
        return executeInJedis(jedis -> jedis.zrevrangeByScoreWithScores(key, max, min));
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(
            String key, double min, double max, int offset, int count) {
        return executeInJedis(jedis -> jedis.zrangeByScoreWithScores(key, min, max, offset, count));
    }

    @Override
    public Set<String> zrevrangeByScore(String key, String max, String min, int offset, int count) {
        return executeInJedis(jedis -> jedis.zrevrangeByScore(key, max, min, offset, count));
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(String key, String min, String max) {
        return executeInJedis(jedis -> jedis.zrangeByScoreWithScores(key, min, max));
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(String key, String max, String min) {
        return executeInJedis(jedis -> jedis.zrevrangeByScoreWithScores(key, max, min));
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(
            String key, String min, String max, int offset, int count) {
        return executeInJedis(jedis -> jedis.zrangeByScoreWithScores(key, min, max, offset, count));
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(
            String key, double max, double min, int offset, int count) {
        return executeInJedis(
                jedis -> jedis.zrevrangeByScoreWithScores(key, max, min, offset, count));
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(
            String key, String max, String min, int offset, int count) {
        return executeInJedis(
                jedis -> jedis.zrevrangeByScoreWithScores(key, max, min, offset, count));
    }

    @Override
    public Long zremrangeByRank(String key, long start, long stop) {
        return executeInJedis(jedis -> jedis.zremrangeByRank(key, start, stop));
    }

    @Override
    public Long zremrangeByScore(String key, double min, double max) {
        return executeInJedis(jedis -> jedis.zremrangeByScore(key, min, max));
    }

    @Override
    public Long zremrangeByScore(String key, String min, String max) {
        return executeInJedis(jedis -> jedis.zremrangeByScore(key, min, max));
    }

    @Override
    public Long zlexcount(String key, String min, String max) {
        return executeInJedis(jedis -> jedis.zlexcount(key, min, max));
    }

    @Override
    public Set<String> zrangeByLex(String key, String min, String max) {
        return executeInJedis(jedis -> jedis.zrangeByLex(key, min, max));
    }

    @Override
    public Set<String> zrangeByLex(String key, String min, String max, int offset, int count) {
        return executeInJedis(jedis -> jedis.zrangeByLex(key, min, max, offset, count));
    }

    @Override
    public Set<String> zrevrangeByLex(String key, String max, String min) {
        return executeInJedis(jedis -> jedis.zrevrangeByLex(key, max, min));
    }

    @Override
    public Set<String> zrevrangeByLex(String key, String max, String min, int offset, int count) {
        return executeInJedis(jedis -> jedis.zrevrangeByLex(key, max, min, offset, count));
    }

    @Override
    public Long zremrangeByLex(String key, String min, String max) {
        return executeInJedis(jedis -> jedis.zremrangeByLex(key, min, max));
    }

    @Override
    public Long linsert(String key, ListPosition where, String pivot, String value) {
        return executeInJedis(jedis -> jedis.linsert(key, where, pivot, value));
    }

    @Override
    public Long lpushx(String key, String... string) {
        return executeInJedis(jedis -> jedis.lpushx(key, string));
    }

    @Override
    public Long rpushx(String key, String... string) {
        return executeInJedis(jedis -> jedis.rpushx(key, string));
    }

    @Override
    public List<String> blpop(int timeout, String key) {
        return executeInJedis(jedis -> jedis.blpop(timeout, key));
    }

    @Override
    public List<String> brpop(int timeout, String key) {
        return executeInJedis(jedis -> jedis.brpop(timeout, key));
    }

    @Override
    public Long del(String key) {
        return executeInJedis(jedis -> jedis.del(key));
    }

    @Override
    public Long unlink(String key) {
        return executeInJedis(jedis -> jedis.unlink(key));
    }

    @Override
    public String echo(String string) {
        return executeInJedis(jedis -> jedis.echo(string));
    }

    @Override
    public Long move(String key, int dbIndex) {
        return executeInJedis(jedis -> jedis.move(key, dbIndex));
    }

    @Override
    public Long bitcount(String key) {
        return executeInJedis(jedis -> jedis.bitcount(key));
    }

    @Override
    public Long bitcount(String key, long start, long end) {
        return executeInJedis(jedis -> jedis.bitcount(key, start, end));
    }

    @Override
    public Long bitpos(String key, boolean value) {
        return executeInJedis(jedis -> jedis.bitpos(key, value));
    }

    @Override
    public Long bitpos(String key, boolean value, BitPosParams params) {
        return executeInJedis(jedis -> jedis.bitpos(key, value, params));
    }

    @Override
    public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor) {
        return executeInJedis(jedis -> jedis.hscan(key, cursor));
    }

    @Override
    public ScanResult<Map.Entry<String, String>> hscan(
            String key, String cursor, ScanParams params) {
        return executeInJedis(jedis -> jedis.hscan(key, cursor, params));
    }

    @Override
    public ScanResult<String> sscan(String key, String cursor) {
        return executeInJedis(jedis -> jedis.sscan(key, cursor));
    }

    @Override
    public ScanResult<Tuple> zscan(String key, String cursor) {
        return executeInJedis(jedis -> jedis.zscan(key, cursor));
    }

    @Override
    public ScanResult<Tuple> zscan(String key, String cursor, ScanParams params) {
        return executeInJedis(jedis -> jedis.zscan(key, cursor, params));
    }

    @Override
    public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
        return executeInJedis(jedis -> jedis.sscan(key, cursor, params));
    }

    @Override
    public Long pfadd(String key, String... elements) {
        return executeInJedis(jedis -> jedis.pfadd(key, elements));
    }

    @Override
    public long pfcount(String key) {
        return executeInJedis(jedis -> jedis.pfcount(key));
    }

    @Override
    public Long geoadd(String key, double longitude, double latitude, String member) {
        return executeInJedis(jedis -> jedis.geoadd(key, longitude, latitude, member));
    }

    @Override
    public Long geoadd(String key, Map<String, GeoCoordinate> memberCoordinateMap) {
        return executeInJedis(jedis -> jedis.geoadd(key, memberCoordinateMap));
    }

    @Override
    public Double geodist(String key, String member1, String member2) {
        return executeInJedis(jedis -> jedis.geodist(key, member1, member2));
    }

    @Override
    public Double geodist(String key, String member1, String member2, GeoUnit unit) {
        return executeInJedis(jedis -> jedis.geodist(key, member1, member2, unit));
    }

    @Override
    public List<String> geohash(String key, String... members) {
        return executeInJedis(jedis -> jedis.geohash(key, members));
    }

    @Override
    public List<GeoCoordinate> geopos(String key, String... members) {
        return executeInJedis(jedis -> jedis.geopos(key, members));
    }

    @Override
    public List<GeoRadiusResponse> georadius(
            String key, double longitude, double latitude, double radius, GeoUnit unit) {
        return executeInJedis(jedis -> jedis.georadius(key, longitude, latitude, radius, unit));
    }

    @Override
    public List<GeoRadiusResponse> georadiusReadonly(
            String key, double longitude, double latitude, double radius, GeoUnit unit) {
        return executeInJedis(
                jedis -> jedis.georadiusReadonly(key, longitude, latitude, radius, unit));
    }

    @Override
    public List<GeoRadiusResponse> georadius(
            String key,
            double longitude,
            double latitude,
            double radius,
            GeoUnit unit,
            GeoRadiusParam param) {
        return executeInJedis(
                jedis -> jedis.georadius(key, longitude, latitude, radius, unit, param));
    }

    @Override
    public List<GeoRadiusResponse> georadiusReadonly(
            String key,
            double longitude,
            double latitude,
            double radius,
            GeoUnit unit,
            GeoRadiusParam param) {
        return executeInJedis(
                jedis -> jedis.georadiusReadonly(key, longitude, latitude, radius, unit, param));
    }

    @Override
    public List<GeoRadiusResponse> georadiusByMember(
            String key, String member, double radius, GeoUnit unit) {
        return executeInJedis(jedis -> jedis.georadiusByMember(key, member, radius, unit));
    }

    @Override
    public List<GeoRadiusResponse> georadiusByMemberReadonly(
            String key, String member, double radius, GeoUnit unit) {
        return executeInJedis(jedis -> jedis.georadiusByMemberReadonly(key, member, radius, unit));
    }

    @Override
    public List<GeoRadiusResponse> georadiusByMember(
            String key, String member, double radius, GeoUnit unit, GeoRadiusParam param) {
        return executeInJedis(jedis -> jedis.georadiusByMember(key, member, radius, unit, param));
    }

    @Override
    public List<GeoRadiusResponse> georadiusByMemberReadonly(
            String key, String member, double radius, GeoUnit unit, GeoRadiusParam param) {
        return executeInJedis(
                jedis -> jedis.georadiusByMemberReadonly(key, member, radius, unit, param));
    }

    @Override
    public List<Long> bitfield(String key, String... arguments) {
        return executeInJedis(jedis -> jedis.bitfield(key, arguments));
    }

    @Override
    public List<Long> bitfieldReadonly(String key, String... arguments) {
        return executeInJedis(jedis -> jedis.bitfieldReadonly(key, arguments));
    }

    @Override
    public Long hstrlen(String key, String field) {
        return executeInJedis(jedis -> jedis.hstrlen(key, field));
    }

    @Override
    public StreamEntryID xadd(String key, StreamEntryID id, Map<String, String> hash) {
        return executeInJedis(jedis -> jedis.xadd(key, id, hash));
    }

    @Override
    public StreamEntryID xadd(
            String key,
            StreamEntryID id,
            Map<String, String> hash,
            long maxLen,
            boolean approximateLength) {
        return executeInJedis(jedis -> jedis.xadd(key, id, hash, maxLen, approximateLength));
    }

    @Override
    public Long xlen(String key) {
        return executeInJedis(jedis -> jedis.xlen(key));
    }

    @Override
    public List<StreamEntry> xrange(String key, StreamEntryID start, StreamEntryID end, int count) {
        return executeInJedis(jedis -> jedis.xrange(key, start, end, count));
    }

    @Override
    public List<StreamEntry> xrevrange(
            String key, StreamEntryID end, StreamEntryID start, int count) {
        return executeInJedis(jedis -> jedis.xrevrange(key, end, start, count));
    }

    @Override
    public long xack(String key, String group, StreamEntryID... ids) {
        return executeInJedis(jedis -> jedis.xack(key, group, ids));
    }

    @Override
    public String xgroupCreate(String key, String groupname, StreamEntryID id, boolean makeStream) {
        return executeInJedis(jedis -> jedis.xgroupCreate(key, groupname, id, makeStream));
    }

    @Override
    public String xgroupSetID(String key, String groupname, StreamEntryID id) {
        return executeInJedis(jedis -> jedis.xgroupSetID(key, groupname, id));
    }

    @Override
    public long xgroupDestroy(String key, String groupname) {
        return executeInJedis(jedis -> jedis.xgroupDestroy(key, groupname));
    }

    @Override
    public Long xgroupDelConsumer(String key, String groupname, String consumername) {
        return executeInJedis(jedis -> jedis.hsetnx(key, groupname, consumername));
    }

    @Override
    public List<StreamPendingEntry> xpending(
            String key,
            String groupname,
            StreamEntryID start,
            StreamEntryID end,
            int count,
            String consumername) {
        return executeInJedis(
                jedis -> jedis.xpending(key, groupname, start, end, count, consumername));
    }

    @Override
    public long xdel(String key, StreamEntryID... ids) {
        return executeInJedis(jedis -> jedis.xdel(key, ids));
    }

    @Override
    public long xtrim(String key, long maxLen, boolean approximate) {
        return executeInJedis(jedis -> jedis.xtrim(key, maxLen, approximate));
    }

    @Override
    public List<StreamEntry> xclaim(
            String key,
            String group,
            String consumername,
            long minIdleTime,
            long newIdleTime,
            int retries,
            boolean force,
            StreamEntryID... ids) {
        return executeInJedis(
                jedis ->
                        jedis.xclaim(
                                key,
                                group,
                                consumername,
                                minIdleTime,
                                newIdleTime,
                                retries,
                                force,
                                ids));
    }

    @Override
    public StreamInfo xinfoStream(String key) {
        return executeInJedis(jedis -> jedis.xinfoStream(key));
    }

    @Override
    public List<StreamGroupInfo> xinfoGroup(String key) {
        return executeInJedis(jedis -> jedis.xinfoGroup(key));
    }

    @Override
    public List<StreamConsumersInfo> xinfoConsumers(String key, String group) {
        return executeInJedis(jedis -> jedis.xinfoConsumers(key, group));
    }
}
