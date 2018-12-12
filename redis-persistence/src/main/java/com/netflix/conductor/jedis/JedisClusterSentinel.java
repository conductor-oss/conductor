package com.netflix.conductor.jedis;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import redis.clients.jedis.BinaryClient.LIST_POSITION;
import redis.clients.jedis.BitPosParams;
import redis.clients.jedis.GeoCoordinate;
import redis.clients.jedis.GeoRadiusResponse;
import redis.clients.jedis.GeoUnit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCommands;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.SortingParams;
import redis.clients.jedis.Tuple;
import redis.clients.jedis.params.geo.GeoRadiusParam;
import redis.clients.jedis.params.sortedset.ZAddParams;
import redis.clients.jedis.params.sortedset.ZIncrByParams;

public class JedisClusterSentinel implements JedisCommands {

  private final JedisSentinelPool jedisPool;

  public JedisClusterSentinel(JedisSentinelPool jedisPool) {
    this.jedisPool = jedisPool;
  }

  @Override
  public String set(String key, String value) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.set(key, value);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public String set(String key, String value, String nxxx, String expx, long time) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.set(key, value, nxxx, expx, time);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public String set(String key, String value, String nxxx) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.set(key, value, nxxx);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public String get(String key) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.get(key);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public Boolean exists(String key) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.exists(key);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public Long persist(String key) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.persist(key);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public String type(String key) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.type(key);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public Long expire(String key, int seconds) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.expire(key, seconds);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public Long pexpire(String key, long milliseconds) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.pexpire(key, milliseconds);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public Long expireAt(String key, long unixTime) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.expireAt(key, unixTime);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public Long pexpireAt(String key, long millisecondsTimestamp) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.pexpireAt(key, millisecondsTimestamp);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public Long ttl(String key) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.ttl(key);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public Long pttl(String key) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.pttl(key);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public Boolean setbit(String key, long offset, boolean value) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.setbit(key, offset, value);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public Boolean setbit(String key, long offset, String value) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.setbit(key, offset, value);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public Boolean getbit(String key, long offset) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.getbit(key, offset);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public Long setrange(String key, long offset, String value) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.setrange(key, offset, value);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public String getrange(String key, long startOffset, long endOffset) {
    Jedis jedis = null;
    try {
      jedis = jedisPool.getResource();
      return jedis.getrange(key, startOffset, endOffset);
    } finally {
      if (jedis != null)
        jedis.close();
    }
  }

  @Override
  public String getSet(String key, String value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.getSet(key, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long setnx(String key, String value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.setnx(key, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public String setex(String key, int seconds, String value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.setex(key, seconds, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public String psetex(String key, long milliseconds, String value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.psetex(key, milliseconds, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long decrBy(String key, long integer) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.decrBy(key, integer);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long decr(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.decr(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long incrBy(String key, long integer) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.incrBy(key, integer);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Double incrByFloat(String key, double value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.incrByFloat(key, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long incr(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.incr(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long append(String key, String value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.append(key, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public String substr(String key, int start, int end) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.substr(key, start, end);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long hset(String key, String field, String value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hset(key, field, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public String hget(String key, String field) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hget(key, field);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long hsetnx(String key, String field, String value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hsetnx(key, field, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public String hmset(String key, Map<String, String> hash) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hmset(key, hash);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public List<String> hmget(String key, String... fields) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hmget(key, fields);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long hincrBy(String key, String field, long value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hincrBy(key, field, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Double hincrByFloat(String key, String field, double value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hincrByFloat(key, field, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Boolean hexists(String key, String field) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hexists(key, field);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long hdel(String key, String... field) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hdel(key, field);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long hlen(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hlen(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> hkeys(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hkeys(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public List<String> hvals(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hvals(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Map<String, String> hgetAll(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hgetAll(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long rpush(String key, String... string) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.rpush(key, string);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long lpush(String key, String... string) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.lpush(key, string);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long llen(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.llen(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public List<String> lrange(String key, long start, long end) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.lrange(key, start, end);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public String ltrim(String key, long start, long end) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.ltrim(key, start, end);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public String lindex(String key, long index) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.lindex(key, index);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public String lset(String key, long index, String value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.lset(key, index, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long lrem(String key, long count, String value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.lrem(key, count, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public String lpop(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.lpop(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public String rpop(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.rpop(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long sadd(String key, String... member) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.sadd(key, member);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> smembers(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.smembers(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long srem(String key, String... member) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.srem(key, member);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public String spop(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.spop(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> spop(String key, long count) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.spop(key, count);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long scard(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.scard(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Boolean sismember(String key, String member) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.sismember(key, member);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public String srandmember(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.srandmember(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public List<String> srandmember(String key, int count) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.srandmember(key, count);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long strlen(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.strlen(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zadd(String key, double score, String member) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zadd(key, score, member);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zadd(String key, double score, String member, ZAddParams params) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zadd(key, score, member, params);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zadd(String key, Map<String, Double> scoreMembers) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zadd(key, scoreMembers);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zadd(String key, Map<String, Double> scoreMembers, ZAddParams params) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zadd(key, scoreMembers, params);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrange(String key, long start, long end) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrange(key, start, end);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zrem(String key, String... member) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrem(key, member);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Double zincrby(String key, double score, String member) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zincrby(key, score, member);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Double zincrby(String key, double score, String member, ZIncrByParams params) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zincrby(key, score, member, params);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zrank(String key, String member) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrank(key, member);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zrevrank(String key, String member) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrevrank(key, member);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrevrange(String key, long start, long end) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrevrange(key, start, end);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<Tuple> zrangeWithScores(String key, long start, long end) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrangeWithScores(key, start, end);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<Tuple> zrevrangeWithScores(String key, long start, long end) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrevrangeWithScores(key, start, end);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zcard(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zcard(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Double zscore(String key, String member) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zscore(key, member);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public List<String> sort(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.sort(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public List<String> sort(String key, SortingParams sortingParameters) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.sort(key, sortingParameters);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zcount(String key, double min, double max) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zcount(key, min, max);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zcount(String key, String min, String max) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zcount(key, min, max);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrangeByScore(String key, double min, double max) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrangeByScore(key, min, max);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrangeByScore(String key, String min, String max) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrangeByScore(key, min, max);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrevrangeByScore(String key, double max, double min) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrevrangeByScore(key, max, min);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrangeByScore(String key, double min, double max, int offset, int count) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrangeByScore(key, min, max, offset, count);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrevrangeByScore(String key, String max, String min) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrevrangeByScore(key, max, min);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrangeByScore(String key, String min, String max, int offset, int count) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrangeByScore(key, min, max, offset, count);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrevrangeByScore(String key, double max, double min, int offset, int count) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrevrangeByScore(key, max, min, offset, count);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(String key, double min, double max) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrangeByScoreWithScores(key, min, max);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(String key, double max, double min) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrevrangeByScoreWithScores(key, max, min);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(String key, double min, double max, int offset, int count) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrangeByScoreWithScores(key, min, max, offset, count);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrevrangeByScore(String key, String max, String min, int offset, int count) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrevrangeByScore(key, max, min, offset, count);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(String key, String min, String max) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrangeByScoreWithScores(key, min, max);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(String key, String max, String min) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrevrangeByScoreWithScores(key, max, min);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(String key, String min, String max, int offset, int count) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrangeByScoreWithScores(key, min, max, offset, count);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(String key, double max, double min, int offset, int count) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrevrangeByScoreWithScores(key, max, min, offset, count);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(String key, String max, String min, int offset, int count) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrevrangeByScoreWithScores(key, max, min, offset, count);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zremrangeByRank(String key, long start, long end) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zremrangeByRank(key, start, end);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zremrangeByScore(String key, double start, double end) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zremrangeByScore(key, start, end);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zremrangeByScore(String key, String start, String end) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zremrangeByScore(key, start, end);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zlexcount(String key, String min, String max) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zlexcount(key, min, max);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrangeByLex(String key, String min, String max) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrangeByLex(key, min, max);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrangeByLex(String key, String min, String max, int offset, int count) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrangeByLex(key, min, max, offset, count);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrevrangeByLex(String key, String max, String min) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrevrangeByLex(key, max, min);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Set<String> zrevrangeByLex(String key, String max, String min, int offset, int count) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zrevrangeByLex(key, max, min, offset, count);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long zremrangeByLex(String key, String min, String max) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zremrangeByLex(key, min, max);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long linsert(String key, LIST_POSITION where, String pivot, String value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.linsert(key, where, pivot, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long lpushx(String key, String... string) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.lpushx(key, string);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long rpushx(String key, String... string) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.rpushx(key, string);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  @Deprecated
  public List<String> blpop(String arg) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.blpop(arg);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public List<String> blpop(int timeout, String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.blpop(timeout, key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  @Deprecated
  public List<String> brpop(String arg) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.brpop(arg);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public List<String> brpop(int timeout, String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.brpop(timeout, key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long del(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.del(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public String echo(String string) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.echo(string);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long move(String key, int dbIndex) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.move(key, dbIndex);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long bitcount(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.bitcount(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long bitcount(String key, long start, long end) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.bitcount(key, start, end);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long bitpos(String key, boolean value) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.bitpos(key, value);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long bitpos(String key, boolean value, BitPosParams params) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.bitpos(key, value, params);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  @Deprecated
  public ScanResult<Entry<String, String>> hscan(String key, int cursor) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hscan(key, cursor);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  @Deprecated
  public ScanResult<String> sscan(String key, int cursor) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.sscan(key, cursor);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  @Deprecated
  public ScanResult<Tuple> zscan(String key, int cursor) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zscan(key, cursor);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public ScanResult<Entry<String, String>> hscan(String key, String cursor) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hscan(key, cursor);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public ScanResult<Entry<String, String>> hscan(String key, String cursor, ScanParams params) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.hscan(key, cursor, params);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public ScanResult<String> sscan(String key, String cursor) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.sscan(key, cursor);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.sscan(key, cursor, params);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public ScanResult<Tuple> zscan(String key, String cursor) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zscan(key, cursor);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public ScanResult<Tuple> zscan(String key, String cursor, ScanParams params) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.zscan(key, cursor, params);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long pfadd(String key, String... elements) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.pfadd(key, elements);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public long pfcount(String key) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.pfcount(key);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long geoadd(String key, double longitude, double latitude, String member) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.geoadd(key, longitude, latitude, member);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Long geoadd(String key, Map<String, GeoCoordinate> memberCoordinateMap) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.geoadd(key, memberCoordinateMap);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Double geodist(String key, String member1, String member2) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.geodist(key, member1, member2);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public Double geodist(String key, String member1, String member2, GeoUnit unit) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.geodist(key, member1, member2, unit);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public List<String> geohash(String key, String... members) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.geohash(key, members);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public List<GeoCoordinate> geopos(String key, String... members) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.geopos(key, members);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public List<GeoRadiusResponse> georadius(String key, double longitude, double latitude, double radius, GeoUnit unit) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.georadius(key, longitude, latitude, radius, unit);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public List<GeoRadiusResponse> georadius(String key, double longitude, double latitude, double radius, GeoUnit unit,
      GeoRadiusParam param) {
    return null;
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMember(String key, String member, double radius, GeoUnit unit) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.georadiusByMember(key, member, radius, unit);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMember(String key, String member, double radius, GeoUnit unit,
      GeoRadiusParam param) {
    return null;
  }

  @Override
  public List<Long> bitfield(String key, String... arguments) {
    Jedis jedis = null;
     try {
       jedis = jedisPool.getResource();
       return jedis.bitfield(key, arguments);
     } finally {
       if (jedis != null)
         jedis.close();
     }
  }

}