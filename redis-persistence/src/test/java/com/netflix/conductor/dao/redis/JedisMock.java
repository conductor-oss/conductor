/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.dao.redis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.rarefiedredis.redis.IRedisClient;
import org.rarefiedredis.redis.IRedisSortedSet.ZsetPair;
import org.rarefiedredis.redis.RedisMock;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.Tuple;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.ZAddParams;

/**
 * @author Viren
 *
 */
public class JedisMock extends Jedis {

    private IRedisClient redis;

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

    @Override public String set(final String key, String value) {
        try { 
            return redis.set(key, value);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String get(final String key) {
        try {
            return redis.get(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Boolean exists(final String key) {
        try {
            return redis.exists(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long del(final String... keys) {
        try {
            return redis.del(keys);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long del(String key) {
        try {
            return redis.del(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String type(final String key) {
        try {
            return redis.type(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long expire(final String key, final int seconds) {
        try {
            return redis.expire(key, seconds) ? 1L : 0L;
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long expireAt(final String key, final long unixTime) {
        try {
            return redis.expireat(key, unixTime) ? 1L : 0L;
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long ttl(final String key) {
        try {
            return redis.ttl(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }
    
    @Override public Long move(final String key, final int dbIndex) {
        try {
            return redis.move(key, dbIndex);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String getSet(final String key, final String value) {
        try {
            return redis.getset(key, value);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public List<String> mget(final String ... keys) {
        try {
            String[] mget = redis.mget(keys);
            List<String> lst = new ArrayList<String>(mget.length);
            for (String get : mget) {
                lst.add(get);
            }
            return lst;
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long setnx(final String key, final String value) {
        try {
            return redis.setnx(key, value);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String setex(final String key, final int seconds, final String value) {
        try {
            return redis.setex(key, seconds, value);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String mset(final String... keysvalues) {
        try {
            return redis.mset(keysvalues);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long msetnx(final String... keysvalues) {
        try {
            return redis.msetnx(keysvalues) ? 1L : 0L;
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long decrBy(final String key, final long integer) {
        try {
            return redis.decrby(key, integer);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long decr(final String key) {
        try {
            return redis.decr(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long incrBy(final String key, final long integer) {
        try {
            return redis.incrby(key, integer);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Double incrByFloat(final String key, final double value) {
        try {
            return Double.parseDouble(redis.incrbyfloat(key, value));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long incr(final String key) {
        try {
            return redis.incr(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long append(final String key, final String value) {
        try {
            return redis.append(key, value);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String substr(final String key, final int start, final int end) {
        try {
            return redis.getrange(key, start, end);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long hset(final String key, final String field, final String value) {
        try {
            return redis.hset(key, field, value) ? 1L : 0L;
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String hget(final String key, final String field) {
        try {
            return redis.hget(key, field);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long hsetnx(final String key, final String field, final String value) {
        try {
            return redis.hsetnx(key, field, value) ? 1L : 0L;
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String hmset(final String key, final Map<String, String> hash) {
        try {
            String field = null, value = null;
            String[] args = new String[(hash.size() - 1)*2];
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
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public List<String> hmget(final String key, final String... fields) {
        try {
            String field = fields[0];
            String[] f = new String[fields.length - 1];
            for (int idx = 1; idx < fields.length; ++idx) {
                f[idx - 1] = fields[idx];
            }
            return redis.hmget(key, field, f);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long hincrBy(final String key, final String field, final long value) {
        try {
            return redis.hincrby(key, field, value);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Double hincrByFloat(final String key, final String field, final double value) {
        try {
            return Double.parseDouble(redis.hincrbyfloat(key, field, value));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Boolean hexists(final String key, final String field) {
        try {
            return redis.hexists(key, field);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long hdel(final String key, final String... fields) {
        try {
            String field = fields[0];
            String[] f = new String[fields.length - 1];
            for (int idx = 1; idx < fields.length; ++idx) {
                f[idx - 1] = fields[idx];
            }
            return redis.hdel(key, field, f);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long hlen(final String key) {
        try {
            return redis.hlen(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> hkeys(final String key) {
        try {
            return redis.hkeys(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public List<String> hvals(final String key) {
        try {
            return redis.hvals(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Map<String, String> hgetAll(final String key) {
        try {
            return redis.hgetall(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long rpush(final String key, final String... strings) {
        try {
            String element = strings[0];
            String[] elements = new String[strings.length - 1];
            for (int idx = 1; idx < strings.length; ++idx) {
                elements[idx - 1] = strings[idx];
            }
            return redis.rpush(key, element, elements);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long lpush(final String key, final String... strings) {
        try {
            String element = strings[0];
            String[] elements = new String[strings.length - 1];
            for (int idx = 1; idx < strings.length; ++idx) {
                elements[idx - 1] = strings[idx];
            }
            return redis.lpush(key, element, elements);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long llen(final String key) {
        try {
            return redis.llen(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public List<String> lrange(final String key, final long start, final long end) {
        try {
            return redis.lrange(key, start, end);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String ltrim(final String key, final long start, final long end) {
        try {
            return redis.ltrim(key, start, end);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String lindex(final String key, final long index) {
        try {
            return redis.lindex(key, index);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String lset(final String key, final long index, final String value) {
        try {
            return redis.lset(key, index, value);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long lrem(final String key, final long count, final String value) {
        try {
            return redis.lrem(key, count, value);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String lpop(final String key) {
        try {
            return redis.lpop(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String rpop(final String key) {
        try {
            return redis.rpop(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String rpoplpush(final String srckey, final String dstkey) {
        try {
            return redis.rpoplpush(srckey, dstkey);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long sadd(final String key, final String... members) {
        try {
            String member = members[0];
            String[] m = new String[members.length - 1];
            for (int idx = 1; idx < members.length; ++idx) {
                m[idx - 1] = members[idx];
            }
            return redis.sadd(key, member, m);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> smembers(final String key) {
        try {
            return redis.smembers(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long srem(final String key, final String... members) {
        try {
            String member = members[0];
            String[] m = new String[members.length - 1];
            for (int idx = 1; idx < members.length; ++idx) {
                m[idx - 1] = members[idx];
            }
            return redis.srem(key, member, m);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String spop(final String key) {
        try {
            return redis.spop(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long smove(final String srckey, final String dstkey, final String member) {
        try {
            return redis.smove(srckey, dstkey, member) ? 1L : 0L;
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long scard(final String key) {
        try {
            return redis.scard(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Boolean sismember(final String key, final String member) {
        try {
            return redis.sismember(key, member);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> sinter(final String... keys) {
        try {
            String key = keys[0];
            String[] k = new String[keys.length - 1];
            for (int idx = 0; idx < keys.length; ++idx) {
                k[idx - 1] = keys[idx];
            }
            return redis.sinter(key, k);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long sinterstore(final String dstkey, final String... keys) {
        try {
            String key = keys[0];
            String[] k = new String[keys.length - 1];
            for (int idx = 0; idx < keys.length; ++idx) {
                k[idx - 1] = keys[idx];
            }
            return redis.sinterstore(dstkey, key, k);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> sunion(final String... keys) {
        try {
            String key = keys[0];
            String[] k = new String[keys.length - 1];
            for (int idx = 0; idx < keys.length; ++idx) {
                k[idx - 1] = keys[idx];
            }
            return redis.sunion(key, k);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long sunionstore(final String dstkey, final String... keys) {
        try {
            String key = keys[0];
            String[] k = new String[keys.length - 1];
            for (int idx = 0; idx < keys.length; ++idx) {
                k[idx - 1] = keys[idx];
            }
            return redis.sunionstore(dstkey, key, k);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> sdiff(final String... keys) {
        try {
            String key = keys[0];
            String[] k = new String[keys.length - 1];
            for (int idx = 0; idx < keys.length; ++idx) {
                k[idx - 1] = keys[idx];
            }
            return redis.sdiff(key, k);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long sdiffstore(final String dstkey, final String... keys) {
        try {
            String key = keys[0];
            String[] k = new String[keys.length - 1];
            for (int idx = 0; idx < keys.length; ++idx) {
                k[idx - 1] = keys[idx];
            }
            return redis.sdiffstore(dstkey, key, k);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String srandmember(final String key) {
        try {
            return redis.srandmember(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public List<String> srandmember(final String key, final int count) {
        try {
            return redis.srandmember(key, count);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long zadd(final String key, final double score, final String member) {
        try {
            return redis.zadd(key, new ZsetPair(member, score));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }
    
    @Override
	public Long zadd(String key, double score, String member, ZAddParams params) {
		
		try {
			
			if(params.getParam("xx") != null) {
				Double existing = redis.zscore(key, member);
				if(existing == null) {
					return 0L;
				}
				return redis.zadd(key, new ZsetPair(member, score));
			}else {
				return redis.zadd(key, new ZsetPair(member, score));
			}
			
		} catch (Exception e) {
			throw new JedisException(e);
		}
	}
	

    @Override public Long zadd(final String key, final Map<String, Double> scoreMembers) {
        try {
            Double score = null;
            String member = null;
            List<ZsetPair> scoresmembers = new ArrayList<ZsetPair>((scoreMembers.size() - 1)*2);
            for (String m : scoreMembers.keySet()) {
                if (m == null) {
                    member = m;
                    score = scoreMembers.get(m);
                    continue;
                }
                scoresmembers.add(new ZsetPair(m, scoreMembers.get(m)));
            }
            return redis.zadd(key, new ZsetPair(member, score), (ZsetPair[])scoresmembers.toArray());
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> zrange(final String key, final long start, final long end) {
        try {
            return ZsetPair.members(redis.zrange(key, start, end));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long zrem(final String key, final String... members) {
        try {
            String member = members[0];
            String[] ms = new String[members.length - 1];
            for (int idx = 1; idx < members.length; ++idx) {
                ms[idx - 1] = members[idx];
            }
            return redis.zrem(key, member, ms);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Double zincrby(final String key, final double score, final String member) {
        try {
            return Double.parseDouble(redis.zincrby(key, score, member));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long zrank(final String key, final String member) {
        try {
            return redis.zrank(key, member);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long zrevrank(final String key, final String member) {
        try {
            return redis.zrevrank(key, member);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> zrevrange(final String key, final long start, final long end) {
        try {
            return ZsetPair.members(redis.zrevrange(key, start, end));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<Tuple> zrangeWithScores(final String key, final long start, final long end) {
        try {
            return toTupleSet(redis.zrange(key, start, end, "withscores"));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<Tuple> zrevrangeWithScores(final String key, final long start, final long end) {
        try {
            return toTupleSet(redis.zrevrange(key, start, end, "withscores"));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long zcard(final String key) {
        try {
            return redis.zcard(key);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Double zscore(final String key, final String member) {
        try {
            return redis.zscore(key, member);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public String watch(final String ... keys) {
        try {
            for (String key : keys) {
                redis.watch(key);
            }
            return "OK";
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }
    /*
    public List<String> sort(final String key) {
        checkIsInMulti();
        client.sort(key);
        return client.getMultiBulkReply();
    }

    public List<String> sort(final String key, final SortingParams sortingParameters) {
        checkIsInMulti();
        client.sort(key, sortingParameters);
        return client.getMultiBulkReply();
    }

    public List<String> blpop(final int timeout, final String... keys) {
        return blpop(getArgsAddTimeout(timeout, keys));
    }

    private String[] getArgsAddTimeout(int timeout, String[] keys) {
        final int keyCount = keys.length;
        final String[] args = new String[keyCount + 1];
        for (int at = 0; at != keyCount; ++at) {
            args[at] = keys[at];
        }

        args[keyCount] = String.valueOf(timeout);
        return args;
    }

    public List<String> blpop(String... args) {
        checkIsInMulti();
        client.blpop(args);
        client.setTimeoutInfinite();
        try {
            return client.getMultiBulkReply();
        } finally {
            client.rollbackTimeout();
        }
    }

    public List<String> brpop(String... args) {
        checkIsInMulti();
        client.brpop(args);
        client.setTimeoutInfinite();
        try {
            return client.getMultiBulkReply();
        } finally {
            client.rollbackTimeout();
        }
    }

    @Deprecated
    public List<String> blpop(String arg) {
        return blpop(new String[] { arg });
    }

    public List<String> brpop(String arg) {
        return brpop(new String[] { arg });
    }

    public Long sort(final String key, final SortingParams sortingParameters, final String dstkey) {
        checkIsInMulti();
        client.sort(key, sortingParameters, dstkey);
        return client.getIntegerReply();
    }

    public Long sort(final String key, final String dstkey) {
        checkIsInMulti();
        client.sort(key, dstkey);
        return client.getIntegerReply();
    }

    public List<String> brpop(final int timeout, final String... keys) {
        return brpop(getArgsAddTimeout(timeout, keys));
    }
    */
    @Override public Long zcount(final String key, final double min, final double max) {
        try {
            return redis.zcount(key, min, max);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long zcount(final String key, final String min, final String max) {
        try {
            return redis.zcount(key, Double.parseDouble(min), Double.parseDouble(max));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> zrangeByScore(final String key, final double min, final double max) {
        try {
            return ZsetPair.members(redis.zrangebyscore(key, String.valueOf(min), String.valueOf(max)));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> zrangeByScore(final String key, final String min, final String max) {
        try {
            return ZsetPair.members(redis.zrangebyscore(key, min, max));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> zrangeByScore(final String key, final double min, final double max,
                                               final int offset, final int count) {
        try {
            return ZsetPair.members(redis.zrangebyscore(key, String.valueOf(min), String.valueOf(max), "limit", String.valueOf(offset), String.valueOf(count)));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> zrangeByScore(final String key, final String min, final String max,
                                               final int offset, final int count) {
        try {
            return ZsetPair.members(redis.zrangebyscore(key, min, max, "limit", String.valueOf(offset), String.valueOf(count)));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<Tuple> zrangeByScoreWithScores(final String key, final double min, final double max) {
        try {
            return toTupleSet(redis.zrangebyscore(key, String.valueOf(min), String.valueOf(max), "withscores"));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<Tuple> zrangeByScoreWithScores(final String key, final String min, final String max) {
        try {
            return toTupleSet(redis.zrangebyscore(key, min, max, "withscores"));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<Tuple> zrangeByScoreWithScores(final String key, final double min, final double max,
                                                        final int offset, final int count) {
        try {
            return toTupleSet(redis.zrangebyscore(key, String.valueOf(min), String.valueOf(max), "limit", String.valueOf(offset), String.valueOf(count), "withscores"));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<Tuple> zrangeByScoreWithScores(final String key, final String min, final String max,
                                              final int offset, final int count) {
        try {
            return toTupleSet(redis.zrangebyscore(key, min, max, "limit", String.valueOf(offset), String.valueOf(count), "withscores"));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> zrevrangeByScore(final String key, final double max, final double min) {
        try {
            return ZsetPair.members(redis.zrevrangebyscore(key, String.valueOf(max), String.valueOf(min)));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> zrevrangeByScore(final String key, final String max, final String min) {
        try {
            return ZsetPair.members(redis.zrevrangebyscore(key, max, min));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> zrevrangeByScore(final String key, final double max, final double min,
                                                  final int offset, final int count) {
        try {
            return ZsetPair.members(redis.zrevrangebyscore(key, String.valueOf(max), String.valueOf(min), "limit", String.valueOf(offset), String.valueOf(count)));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<Tuple> zrevrangeByScoreWithScores(final String key, final double max, final double min) {
        try {
            return toTupleSet(redis.zrevrangebyscore(key, String.valueOf(max), String.valueOf(min), "withscores"));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<Tuple> zrevrangeByScoreWithScores(final String key, final double max,
                                                           final double min, final int offset, final int count) {
        try {
            return toTupleSet(redis.zrevrangebyscore(key, String.valueOf(max), String.valueOf(min), "limit", String.valueOf(offset), String.valueOf(count), "withscores"));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<Tuple> zrevrangeByScoreWithScores(final String key, final String max,
                                                 final String min, final int offset, final int count) {
        try {
            return toTupleSet(redis.zrevrangebyscore(key, max, min, "limit", String.valueOf(offset), String.valueOf(count), "withscores"));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<String> zrevrangeByScore(final String key, final String max, final String min,
                                                  final int offset, final int count) {
        try {
            return ZsetPair.members(redis.zrevrangebyscore(key, max, min, "limit", String.valueOf(offset), String.valueOf(count)));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Set<Tuple> zrevrangeByScoreWithScores(final String key, final String max, final String min) {
        try {
            return toTupleSet(redis.zrevrangebyscore(key, max, min, "withscores"));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long zremrangeByRank(final String key, final long start, final long end) {
        try {
            return redis.zremrangebyrank(key, start, end);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long zremrangeByScore(final String key, final double start, final double end) {
        try {
            return redis.zremrangebyscore(key, String.valueOf(start), String.valueOf(end));
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long zremrangeByScore(final String key, final String start, final String end) {
        try {
            return redis.zremrangebyscore(key, start, end);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

    @Override public Long zunionstore(final String dstkey, final String... sets) {
        try {
            return redis.zunionstore(dstkey, sets.length, sets);
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }
    
	@Override
	public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
		try {
			org.rarefiedredis.redis.ScanResult<Set<String>> sr = redis.sscan(key, Long.valueOf(cursor), "count", "1000000");
			List<String> list = sr.results.stream().collect(Collectors.toList());
            ScanResult<String> result = new ScanResult<String>("0", list);
            return result;
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
	}

	public ScanResult<Entry<String, String>> hscan(final String key, final String cursor) {
    	try {
            org.rarefiedredis.redis.ScanResult<Map<String, String>> mockr = redis.hscan(key, Long.valueOf(cursor), "count", "1000000");
            Map<String, String> results = mockr.results;
            List<Entry<String, String>> list = results.entrySet().stream().collect(Collectors.toList());
			ScanResult<Entry<String, String>> result = new ScanResult<Entry<String, String>>("0", list);
            
            return result;
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }

	public ScanResult<Tuple> zscan(final String key, final String cursor) {
		try {
			org.rarefiedredis.redis.ScanResult<Set<ZsetPair>> sr = redis.zscan(key, Long.valueOf(cursor), "count", "1000000");
			List<ZsetPair> list = sr.results.stream().collect(Collectors.toList());
			List<Tuple> tl = new LinkedList<Tuple>();
			list.forEach(p -> tl.add(new Tuple(p.member, p.score)));
			ScanResult<Tuple> result = new ScanResult<Tuple>("0", tl);
            return result;
        }
        catch (Exception e) {
            throw new JedisException(e);
        }
    }
    
    
    /*
    public Long zunionstore(final String dstkey, final ZParams params, final String... sets) {
        checkIsInMulti();
        client.zunionstore(dstkey, params, sets);
        return client.getIntegerReply();
    }

    public Long zinterstore(final String dstkey, final String... sets) {
        checkIsInMulti();
        client.zinterstore(dstkey, sets);
        return client.getIntegerReply();
    }

    public Long zinterstore(final String dstkey, final ZParams params, final String... sets) {
        checkIsInMulti();
        client.zinterstore(dstkey, params, sets);
        return client.getIntegerReply();
    }

    @Override
    public Long zlexcount(final String key, final String min, final String max) {
        checkIsInMulti();
        client.zlexcount(key, min, max);
        return client.getIntegerReply();
    }

    @Override
    public Set<String> zrangeByLex(final String key, final String min, final String max) {
        checkIsInMulti();
        client.zrangeByLex(key, min, max);
        final List<String> members = client.getMultiBulkReply();
        if (members == null) {
            return null;
        }
        return new LinkedHashSet<String>(members);
    }

    @Override
    public Set<String> zrangeByLex(final String key, final String min, final String max,
                                   final int offset, final int count) {
        checkIsInMulti();
        client.zrangeByLex(key, min, max, offset, count);
        final List<String> members = client.getMultiBulkReply();
        if (members == null) {
            return null;
        }
        return new LinkedHashSet<String>(members);
    }

    @Override
    public Set<String> zrevrangeByLex(String key, String max, String min) {
        checkIsInMulti();
        client.zrevrangeByLex(key, max, min);
        final List<String> members = client.getMultiBulkReply();
        if (members == null) {
            return null;
        }
        return new LinkedHashSet<String>(members);
    }

    @Override
    public Set<String> zrevrangeByLex(String key, String max, String min, int offset, int count) {
        checkIsInMulti();
        client.zrevrangeByLex(key, max, min, offset, count);
        final List<String> members = client.getMultiBulkReply();
        if (members == null) {
            return null;
        }
        return new LinkedHashSet<String>(members);
    }

    @Override
    public Long zremrangeByLex(final String key, final String min, final String max) {
        checkIsInMulti();
        client.zremrangeByLex(key, min, max);
        return client.getIntegerReply();
    }

    public Long strlen(final String key) {
        client.strlen(key);
        return client.getIntegerReply();
    }

    public Long lpushx(final String key, final String... string) {
        client.lpushx(key, string);
        return client.getIntegerReply();
    }

    public Long persist(final String key) {
        client.persist(key);
        return client.getIntegerReply();
    }

    public Long rpushx(final String key, final String... string) {
        client.rpushx(key, string);
        return client.getIntegerReply();
    }

    public String echo(final String string) {
        client.echo(string);
        return client.getBulkReply();
    }

    public Long linsert(final String key, final LIST_POSITION where, final String pivot,
                        final String value) {
        client.linsert(key, where, pivot, value);
        return client.getIntegerReply();
    }

    public String brpoplpush(String source, String destination, int timeout) {
        client.brpoplpush(source, destination, timeout);
        client.setTimeoutInfinite();
        try {
            return client.getBulkReply();
        } finally {
            client.rollbackTimeout();
        }
    }

    public Boolean setbit(String key, long offset, boolean value) {
        client.setbit(key, offset, value);
        return client.getIntegerReply() == 1;
    }

    public Boolean setbit(String key, long offset, String value) {
        client.setbit(key, offset, value);
        return client.getIntegerReply() == 1;
    }

    public Boolean getbit(String key, long offset) {
        client.getbit(key, offset);
        return client.getIntegerReply() == 1;
    }

    public Long setrange(String key, long offset, String value) {
        client.setrange(key, offset, value);
        return client.getIntegerReply();
    }

    public String getrange(String key, long startOffset, long endOffset) {
        client.getrange(key, startOffset, endOffset);
        return client.getBulkReply();
    }

    public Long bitpos(final String key, final boolean value) {
        return bitpos(key, value, new BitPosParams());
    }

    public Long bitpos(final String key, final boolean value, final BitPosParams params) {
        client.bitpos(key, value, params);
        return client.getIntegerReply();
    }

    public List<String> configGet(final String pattern) {
        client.configGet(pattern);
        return client.getMultiBulkReply();
    }

    public String configSet(final String parameter, final String value) {
        client.configSet(parameter, value);
        return client.getStatusCodeReply();
    }

    public Object eval(String script, int keyCount, String... params) {
        client.setTimeoutInfinite();
        try {
            client.eval(script, keyCount, params);
            return getEvalResult();
        } finally {
            client.rollbackTimeout();
        }
    }

    public void subscribe(final JedisPubSub jedisPubSub, final String... channels) {
        client.setTimeoutInfinite();
        try {
            jedisPubSub.proceed(client, channels);
        } finally {
            client.rollbackTimeout();
        }
    }

    public Long publish(final String channel, final String message) {
        checkIsInMulti();
        connect();
        client.publish(channel, message);
        return client.getIntegerReply();
    }

    public void psubscribe(final JedisPubSub jedisPubSub, final String... patterns) {
        checkIsInMulti();
        client.setTimeoutInfinite();
        try {
            jedisPubSub.proceedWithPatterns(client, patterns);
        } finally {
            client.rollbackTimeout();
        }
    }

    protected static String[] getParams(List<String> keys, List<String> args) {
        int keyCount = keys.size();
        int argCount = args.size();

        String[] params = new String[keyCount + args.size()];

        for (int i = 0; i < keyCount; i++)
            params[i] = keys.get(i);

        for (int i = 0; i < argCount; i++)
            params[keyCount + i] = args.get(i);

        return params;
    }

    public Object eval(String script, List<String> keys, List<String> args) {
        return eval(script, keys.size(), getParams(keys, args));
    }

    public Object eval(String script) {
        return eval(script, 0);
    }

    public Object evalsha(String script) {
        return evalsha(script, 0);
    }

    private Object getEvalResult() {
        return evalResult(client.getOne());
    }

    private Object evalResult(Object result) {
        if (result instanceof byte[]) return SafeEncoder.encode((byte[]) result);

        if (result instanceof List<?>) {
            List<?> list = (List<?>) result;
            List<Object> listResult = new ArrayList<Object>(list.size());
            for (Object bin : list) {
                listResult.add(evalResult(bin));
            }

            return listResult;
        }

        return result;
    }

    public Object evalsha(String sha1, List<String> keys, List<String> args) {
        return evalsha(sha1, keys.size(), getParams(keys, args));
    }

    public Object evalsha(String sha1, int keyCount, String... params) {
        checkIsInMulti();
        client.evalsha(sha1, keyCount, params);
        return getEvalResult();
    }

    public Boolean scriptExists(String sha1) {
        String[] a = new String[1];
        a[0] = sha1;
        return scriptExists(a).get(0);
    }

    public List<Boolean> scriptExists(String... sha1) {
        client.scriptExists(sha1);
        List<Long> result = client.getIntegerMultiBulkReply();
        List<Boolean> exists = new ArrayList<Boolean>();

        for (Long value : result)
            exists.add(value == 1);

        return exists;
    }

    public String scriptLoad(String script) {
        client.scriptLoad(script);
        return client.getBulkReply();
    }

    public List<Slowlog> slowlogGet() {
        client.slowlogGet();
        return Slowlog.from(client.getObjectMultiBulkReply());
    }

    public List<Slowlog> slowlogGet(long entries) {
        client.slowlogGet(entries);
        return Slowlog.from(client.getObjectMultiBulkReply());
    }

    public Long objectRefcount(String string) {
        client.objectRefcount(string);
        return client.getIntegerReply();
    }

    public String objectEncoding(String string) {
        client.objectEncoding(string);
        return client.getBulkReply();
    }

    public Long objectIdletime(String string) {
        client.objectIdletime(string);
        return client.getIntegerReply();
    }

    public Long bitcount(final String key) {
        client.bitcount(key);
        return client.getIntegerReply();
    }

    public Long bitcount(final String key, long start, long end) {
        client.bitcount(key, start, end);
        return client.getIntegerReply();
    }

    public Long bitop(BitOP op, final String destKey, String... srcKeys) {
        client.bitop(op, destKey, srcKeys);
        return client.getIntegerReply();
    }

    @SuppressWarnings("rawtypes")
    public List<Map<String, String>> sentinelMasters() {
        client.sentinel(Protocol.SENTINEL_MASTERS);
        final List<Object> reply = client.getObjectMultiBulkReply();

        final List<Map<String, String>> masters = new ArrayList<Map<String, String>>();
        for (Object obj : reply) {
            masters.add(BuilderFactory.STRING_MAP.build((List) obj));
        }
        return masters;
    }

    public List<String> sentinelGetMasterAddrByName(String masterName) {
        client.sentinel(Protocol.SENTINEL_GET_MASTER_ADDR_BY_NAME, masterName);
        final List<Object> reply = client.getObjectMultiBulkReply();
        return BuilderFactory.STRING_LIST.build(reply);
    }

    public Long sentinelReset(String pattern) {
        client.sentinel(Protocol.SENTINEL_RESET, pattern);
        return client.getIntegerReply();
    }

    @SuppressWarnings("rawtypes")
    public List<Map<String, String>> sentinelSlaves(String masterName) {
        client.sentinel(Protocol.SENTINEL_SLAVES, masterName);
        final List<Object> reply = client.getObjectMultiBulkReply();

        final List<Map<String, String>> slaves = new ArrayList<Map<String, String>>();
        for (Object obj : reply) {
            slaves.add(BuilderFactory.STRING_MAP.build((List) obj));
        }
        return slaves;
    }

    public String sentinelFailover(String masterName) {
        client.sentinel(Protocol.SENTINEL_FAILOVER, masterName);
        return client.getStatusCodeReply();
    }

    public String sentinelMonitor(String masterName, String ip, int port, int quorum) {
        client.sentinel(Protocol.SENTINEL_MONITOR, masterName, ip, String.valueOf(port),
                        String.valueOf(quorum));
        return client.getStatusCodeReply();
    }

    public String sentinelRemove(String masterName) {
        client.sentinel(Protocol.SENTINEL_REMOVE, masterName);
        return client.getStatusCodeReply();
    }

    public String sentinelSet(String masterName, Map<String, String> parameterMap) {
        int index = 0;
        int paramsLength = parameterMap.size() * 2 + 2;
        String[] params = new String[paramsLength];

        params[index++] = Protocol.SENTINEL_SET;
        params[index++] = masterName;
        for (Entry<String, String> entry : parameterMap.entrySet()) {
            params[index++] = entry.getKey();
            params[index++] = entry.getValue();
        }

        client.sentinel(params);
        return client.getStatusCodeReply();
    }

    public byte[] dump(final String key) {
        checkIsInMulti();
        client.dump(key);
        return client.getBinaryBulkReply();
    }

    public String restore(final String key, final int ttl, final byte[] serializedValue) {
        checkIsInMulti();
        client.restore(key, ttl, serializedValue);
        return client.getStatusCodeReply();
    }

    public Long pexpire(final String key, final long milliseconds) {
        checkIsInMulti();
        client.pexpire(key, milliseconds);
        return client.getIntegerReply();
    }

    public Long pexpireAt(final String key, final long millisecondsTimestamp) {
        checkIsInMulti();
        client.pexpireAt(key, millisecondsTimestamp);
        return client.getIntegerReply();
    }

    public Long pttl(final String key) {
        checkIsInMulti();
        client.pttl(key);
        return client.getIntegerReply();
    }

    public String psetex(final String key, final long milliseconds, final String value) {
        checkIsInMulti();
        client.psetex(key, milliseconds, value);
        return client.getStatusCodeReply();
    }

    public String set(final String key, final String value, final String nxxx) {
        checkIsInMulti();
        client.set(key, value, nxxx);
        return client.getStatusCodeReply();
    }

    public String set(final String key, final String value, final String nxxx, final String expx,
                      final int time) {
        checkIsInMulti();
        client.set(key, value, nxxx, expx, time);
        return client.getStatusCodeReply();
    }

    public String clientKill(final String client) {
        checkIsInMulti();
        this.client.clientKill(client);
        return this.client.getStatusCodeReply();
    }

    public String clientSetname(final String name) {
        checkIsInMulti();
        client.clientSetname(name);
        return client.getStatusCodeReply();
    }

    public String migrate(final String host, final int port, final String key,
                          final int destinationDb, final int timeout) {
        checkIsInMulti();
        client.migrate(host, port, key, destinationDb, timeout);
        return client.getStatusCodeReply();
    }

    public ScanResult<String> scan(final String cursor) {
        return scan(cursor, new ScanParams());
    }

    public ScanResult<String> scan(final String cursor, final ScanParams params) {
        checkIsInMulti();
        client.scan(cursor, params);
        List<Object> result = client.getObjectMultiBulkReply();
        String newcursor = new String((byte[]) result.get(0));
        List<String> results = new ArrayList<String>();
        List<byte[]> rawResults = (List<byte[]>) result.get(1);
        for (byte[] bs : rawResults) {
            results.add(SafeEncoder.encode(bs));
        }
        return new ScanResult<String>(newcursor, results);
    }

    public ScanResult<Map.Entry<String, String>> hscan(final String key, final String cursor) {
        return hscan(key, cursor, new ScanParams());
    }

    public ScanResult<Map.Entry<String, String>> hscan(final String key, final String cursor,
                                                       final ScanParams params) {
        checkIsInMulti();
        client.hscan(key, cursor, params);
        List<Object> result = client.getObjectMultiBulkReply();
        String newcursor = new String((byte[]) result.get(0));
        List<Map.Entry<String, String>> results = new ArrayList<Map.Entry<String, String>>();
        List<byte[]> rawResults = (List<byte[]>) result.get(1);
        Iterator<byte[]> iterator = rawResults.iterator();
        while (iterator.hasNext()) {
            results.add(new AbstractMap.SimpleEntry<String, String>(SafeEncoder.encode(iterator.next()),
                                                                    SafeEncoder.encode(iterator.next())));
        }
        return new ScanResult<Map.Entry<String, String>>(newcursor, results);
    }

    public ScanResult<String> sscan(final String key, final String cursor) {
        return sscan(key, cursor, new ScanParams());
    }

    public ScanResult<String> sscan(final String key, final String cursor, final ScanParams params) {
        checkIsInMulti();
        client.sscan(key, cursor, params);
        List<Object> result = client.getObjectMultiBulkReply();
        String newcursor = new String((byte[]) result.get(0));
        List<String> results = new ArrayList<String>();
        List<byte[]> rawResults = (List<byte[]>) result.get(1);
        for (byte[] bs : rawResults) {
            results.add(SafeEncoder.encode(bs));
        }
        return new ScanResult<String>(newcursor, results);
    }

    

    public ScanResult<Tuple> zscan(final String key, final String cursor, final ScanParams params) {
        checkIsInMulti();
        client.zscan(key, cursor, params);
        List<Object> result = client.getObjectMultiBulkReply();
        String newcursor = new String((byte[]) result.get(0));
        List<Tuple> results = new ArrayList<Tuple>();
        List<byte[]> rawResults = (List<byte[]>) result.get(1);
        Iterator<byte[]> iterator = rawResults.iterator();
        while (iterator.hasNext()) {
            results.add(new Tuple(SafeEncoder.encode(iterator.next()), Double.valueOf(SafeEncoder
                                                                                      .encode(iterator.next()))));
        }
        return new ScanResult<Tuple>(newcursor, results);
    }

    public String clusterNodes() {
        checkIsInMulti();
        client.clusterNodes();
        return client.getBulkReply();
    }

    public String clusterMeet(final String ip, final int port) {
        checkIsInMulti();
        client.clusterMeet(ip, port);
        return client.getStatusCodeReply();
    }

    public String clusterReset(final Reset resetType) {
        checkIsInMulti();
        client.clusterReset(resetType);
        return client.getStatusCodeReply();
    }

    public String clusterAddSlots(final int... slots) {
        checkIsInMulti();
        client.clusterAddSlots(slots);
        return client.getStatusCodeReply();
    }

    public String clusterDelSlots(final int... slots) {
        checkIsInMulti();
        client.clusterDelSlots(slots);
        return client.getStatusCodeReply();
    }

    public String clusterInfo() {
        checkIsInMulti();
        client.clusterInfo();
        return client.getStatusCodeReply();
    }

    public List<String> clusterGetKeysInSlot(final int slot, final int count) {
        checkIsInMulti();
        client.clusterGetKeysInSlot(slot, count);
        return client.getMultiBulkReply();
    }

    public String clusterSetSlotNode(final int slot, final String nodeId) {
        checkIsInMulti();
        client.clusterSetSlotNode(slot, nodeId);
        return client.getStatusCodeReply();
    }

    public String clusterSetSlotMigrating(final int slot, final String nodeId) {
        checkIsInMulti();
        client.clusterSetSlotMigrating(slot, nodeId);
        return client.getStatusCodeReply();
    }

    public String clusterSetSlotImporting(final int slot, final String nodeId) {
        checkIsInMulti();
        client.clusterSetSlotImporting(slot, nodeId);
        return client.getStatusCodeReply();
    }

    public String clusterSetSlotStable(final int slot) {
        checkIsInMulti();
        client.clusterSetSlotStable(slot);
        return client.getStatusCodeReply();
    }

    public String clusterForget(final String nodeId) {
        checkIsInMulti();
        client.clusterForget(nodeId);
        return client.getStatusCodeReply();
    }

    public String clusterFlushSlots() {
        checkIsInMulti();
        client.clusterFlushSlots();
        return client.getStatusCodeReply();
    }

    public Long clusterKeySlot(final String key) {
        checkIsInMulti();
        client.clusterKeySlot(key);
        return client.getIntegerReply();
    }

    public Long clusterCountKeysInSlot(final int slot) {
        checkIsInMulti();
        client.clusterCountKeysInSlot(slot);
        return client.getIntegerReply();
    }

    public String clusterSaveConfig() {
        checkIsInMulti();
        client.clusterSaveConfig();
        return client.getStatusCodeReply();
    }

    public String clusterReplicate(final String nodeId) {
        checkIsInMulti();
        client.clusterReplicate(nodeId);
        return client.getStatusCodeReply();
    }

    public List<String> clusterSlaves(final String nodeId) {
        checkIsInMulti();
        client.clusterSlaves(nodeId);
        return client.getMultiBulkReply();
    }

    public String clusterFailover() {
        checkIsInMulti();
        client.clusterFailover();
        return client.getStatusCodeReply();
    }

    @Override
    public List<Object> clusterSlots() {
        checkIsInMulti();
        client.clusterSlots();
        return client.getObjectMultiBulkReply();
    }

    public String asking() {
        checkIsInMulti();
        client.asking();
        return client.getStatusCodeReply();
    }

    public List<String> pubsubChannels(String pattern) {
        checkIsInMulti();
        client.pubsubChannels(pattern);
        return client.getMultiBulkReply();
    }

    public Long pubsubNumPat() {
        checkIsInMulti();
        client.pubsubNumPat();
        return client.getIntegerReply();
    }

    public Map<String, String> pubsubNumSub(String... channels) {
        checkIsInMulti();
        client.pubsubNumSub(channels);
        return BuilderFactory.PUBSUB_NUMSUB_MAP.build(client.getBinaryMultiBulkReply());
    }

    @Override
    public void close() {
        if (dataSource != null) {
            if (client.isBroken()) {
                this.dataSource.returnBrokenResource(this);
            } else {
                this.dataSource.returnResource(this);
            }
        } else {
            client.close();
        }
    }

    public void setDataSource(JedisPoolAbstract jedisPool) {
        this.dataSource = jedisPool;
    }

    public Long pfadd(final String key, final String... elements) {
        checkIsInMulti();
        client.pfadd(key, elements);
        return client.getIntegerReply();
    }

    public long pfcount(final String key) {
        checkIsInMulti();
        client.pfcount(key);
        return client.getIntegerReply();
    }

    @Override
    public long pfcount(String... keys) {
        checkIsInMulti();
        client.pfcount(keys);
        return client.getIntegerReply();
    }

    public String pfmerge(final String destkey, final String... sourcekeys) {
        checkIsInMulti();
        client.pfmerge(destkey, sourcekeys);
        return client.getStatusCodeReply();
    }

    @Override
    public List<String> blpop(int timeout, String key) {
        return blpop(key, String.valueOf(timeout));
    }

    @Override
    public List<String> brpop(int timeout, String key) {
        return brpop(key, String.valueOf(timeout));
    }
    */
}
