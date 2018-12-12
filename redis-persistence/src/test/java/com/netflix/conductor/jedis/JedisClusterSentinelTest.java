package com.netflix.conductor.jedis;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import redis.clients.jedis.BinaryClient.LIST_POSITION;
import redis.clients.jedis.GeoUnit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.SortingParams;
import redis.clients.jedis.params.geo.GeoRadiusParam;
import redis.clients.jedis.params.sortedset.ZAddParams;
import redis.clients.jedis.params.sortedset.ZIncrByParams;

public class JedisClusterSentinelTest {
  private final Jedis jedis = mock(Jedis.class);
  private final JedisSentinelPool jedisPool = mock(JedisSentinelPool.class);
  private final JedisClusterSentinel jedisCluster = new JedisClusterSentinel(jedisPool);

  @Before
  public void init() {
    when(this.jedisPool.getResource()).thenReturn(this.jedis);
  }

  @Test
  public void testSet() throws Exception {
    jedisCluster.set("key", "value");
    jedisCluster.set("key", "value", "nxxx");
    jedisCluster.set("key", "value", "nxxx", "expx", 1337);
  }

  @Test
  public void testGet() {
    jedisCluster.get("key");
  }

  @Test
  public void testExists() {
    jedisCluster.exists("key");
  }

  @Test
  public void testPersist() {
    jedisCluster.persist("key");
  }

  @Test
  public void testType() {
    jedisCluster.type("key");
  }

  @Test
  public void testExpire() {
    jedisCluster.expire("key", 1337);
  }

  @Test
  public void testPexpire() {
    jedisCluster.pexpire("key", 1337);
  }

  @Test
  public void testExpireAt() {
    jedisCluster.expireAt("key", 1337);
  }

  @Test
  public void testPexpireAt() {
    jedisCluster.pexpireAt("key", 1337);
  }

  @Test
  public void testTtl() {
    jedisCluster.ttl("key");
  }

  @Test
  public void testPttl() {
    jedisCluster.pttl("key");
  }

  @Test
  public void testSetbit() {
    jedisCluster.setbit("key", 1337, "value");
    jedisCluster.setbit("key", 1337, true);
  }

  @Test
  public void testGetbit() {
    jedisCluster.getbit("key", 1337);
  }

  @Test
  public void testSetrange() {
    jedisCluster.setrange("key", 1337, "value");
  }

  @Test
  public void testGetrange() {
    jedisCluster.getrange("key", 1337, 1338);
  }

  @Test
  public void testGetSet() {
    jedisCluster.getSet("key", "value");
  }

  @Test
  public void testSetnx() {
    jedisCluster.setnx("test", "value");
  }

  @Test
  public void testSetex() {
    jedisCluster.setex("key", 1337, "value");
  }

  @Test
  public void testPsetex() {
    jedisCluster.psetex("key", 1337, "value");
  }

  @Test
  public void testDecrBy() {
    jedisCluster.decrBy("key", 1337);
  }

  @Test
  public void testDecr() {
    jedisCluster.decr("key");
  }

  @Test
  public void testIncrBy() {
    jedisCluster.incrBy("key", 1337);
  }

  @Test
  public void testIncrByFloat() {
    jedisCluster.incrByFloat("key", 1337);
  }

  @Test
  public void testIncr() {
    jedisCluster.incr("key");
  }

  @Test
  public void testAppend() {
    jedisCluster.append("key", "value");
  }

  @Test
  public void testSubstr() {
    jedisCluster.substr("key", 1337, 1338);
  }

  @Test
  public void testHset() {
    jedisCluster.hset("key", "field", "value");
  }

  @Test
  public void testHget() {
    jedisCluster.hget("key", "field");
  }

  @Test
  public void testHsetnx() {
    jedisCluster.hsetnx("key", "field", "value");
  }

  @Test
  public void testHmset() {
    jedisCluster.hmset("key", new HashMap<String, String>());
  }

  @Test
  public void testHmget() {
    jedisCluster.hmget("key", "fields");
  }

  @Test
  public void testHincrBy() {
    jedisCluster.hincrBy("key", "field", 1337);
  }

  @Test
  public void testHincrByFloat() {
    jedisCluster.hincrByFloat("key", "field", 1337);
  }

  @Test
  public void testHexists() {
    jedisCluster.hexists("key", "field");
  }

  @Test
  public void testHdel() {
    jedisCluster.hdel("key", "field");
  }

  @Test
  public void testHlen() {
    jedisCluster.hlen("key");
  }

  @Test
  public void testHkeys() {
    jedisCluster.hkeys("key");
  }

  @Test
  public void testHvals() {
    jedisCluster.hvals("key");
  }

  @Test
  public void testGgetAll() {
    jedisCluster.hgetAll("key");
  }

  @Test
  public void testRpush() {
    jedisCluster.rpush("key", "string");
  }

  @Test
  public void testLpush() {
    jedisCluster.lpush("key", "string");
  }

  @Test
  public void testLlen() {
    jedisCluster.llen("key");
  }

  @Test
  public void testLrange() {
    jedisCluster.lrange("key", 1337, 1338);
  }

  @Test
  public void testLtrim() {
    jedisCluster.ltrim("key", 1337, 1338);
  }

  @Test
  public void testLindex() {
    jedisCluster.lindex("key", 1337);
  }

  @Test
  public void testLset() {
    jedisCluster.lset("key", 1337, "value");
  }

  @Test
  public void testLrem() {
    jedisCluster.lrem("key", 1337, "value");
  }

  @Test
  public void testLpop() {
    jedisCluster.lpop("key");
  }

  @Test
  public void testRpop() {
    jedisCluster.rpop("key");
  }

  @Test
  public void testSadd() {
    jedisCluster.sadd("key", "member");
  }

  @Test
  public void testSmembers() {
    jedisCluster.smembers("key");
  }

  @Test
  public void testSrem() {
    jedisCluster.srem("key", "member");
  }

  @Test
  public void testSpop() {
    jedisCluster.spop("key");
    jedisCluster.spop("key", 1337);
  }

  @Test
  public void testScard() {
    jedisCluster.scard("key");
  }

  @Test
  public void testSismember() {
    jedisCluster.sismember("key", "member");
  }

  @Test
  public void testSrandmember() {
    jedisCluster.srandmember("key");
    jedisCluster.srandmember("key", 1337);
  }

  @Test
  public void testStrlen() {
    jedisCluster.strlen("key");
  }

  @Test
  public void testZadd() {
    jedisCluster.zadd("key", new HashMap<>());
    jedisCluster.zadd("key", new HashMap<>(), ZAddParams.zAddParams());
    jedisCluster.zadd("key", 1337, "members");
    jedisCluster.zadd("key", 1337, "members", ZAddParams.zAddParams());
  }

  @Test
  public void testZrange() {
    jedisCluster.zrange("key", 1337, 1338);
  }

  @Test
  public void testZrem() {
    jedisCluster.zrem("key", "member");
  }

  @Test
  public void testZincrby() {
    jedisCluster.zincrby("key", 1337, "member");
    jedisCluster.zincrby("key", 1337, "member", ZIncrByParams.zIncrByParams());
  }

  @Test
  public void testZrank() {
    jedisCluster.zrank("key", "member");
  }

  @Test
  public void testZrevrank() {
    jedisCluster.zrevrank("key", "member");
  }

  @Test
  public void testZrevrange() {
    jedisCluster.zrevrange("key", 1337, 1338);
  }

  @Test
  public void testZrangeWithScores() {
    jedisCluster.zrangeWithScores("key", 1337, 1338);
  }

  @Test
  public void testZrevrangeWithScores() {
    jedisCluster.zrevrangeWithScores("key", 1337, 1338);
  }

  @Test
  public void testZcard() {
    jedisCluster.zcard("key");
  }

  @Test
  public void testZscore() {
    jedisCluster.zscore("key", "member");
  }

  @Test
  public void testSort() {
    jedisCluster.sort("key");
    jedisCluster.sort("key", new SortingParams());
  }

  @Test
  public void testZcount() {
    jedisCluster.zcount("key", "min", "max");
    jedisCluster.zcount("key", 1337, 1338);
  }

  @Test
  public void testZrangeByScore() {
    jedisCluster.zrangeByScore("key", "min", "max");
    jedisCluster.zrangeByScore("key", 1337, 1338);
    jedisCluster.zrangeByScore("key", "min", "max", 1337, 1338);
    jedisCluster.zrangeByScore("key", 1337, 1338, 1339, 1340);
  }


  @Test
  public void testZrevrangeByScore() {
    jedisCluster.zrevrangeByScore("key", "max", "min");
    jedisCluster.zrevrangeByScore("key", 1337, 1338);
    jedisCluster.zrevrangeByScore("key", "max", "min", 1337, 1338);
    jedisCluster.zrevrangeByScore("key", 1337, 1338, 1339, 1340);
  }

  @Test
  public void testZrangeByScoreWithScores() {
    jedisCluster.zrangeByScoreWithScores("key", "min", "max");
    jedisCluster.zrangeByScoreWithScores("key", "min", "max", 1337, 1338);
    jedisCluster.zrangeByScoreWithScores("key", 1337, 1338);
    jedisCluster.zrangeByScoreWithScores("key", 1337, 1338, 1339, 1340);
  }

  @Test
  public void testZrevrangeByScoreWithScores() {
    jedisCluster.zrevrangeByScoreWithScores("key", "max", "min");
    jedisCluster.zrevrangeByScoreWithScores("key", "max", "min", 1337, 1338);
    jedisCluster.zrevrangeByScoreWithScores("key", 1337, 1338);
    jedisCluster.zrevrangeByScoreWithScores("key", 1337, 1338, 1339, 1340);
  }

  @Test
  public void testZremrangeByRank() {
    jedisCluster.zremrangeByRank("key", 1337, 1338);
  }

  @Test
  public void testZremrangeByScore() {
    jedisCluster.zremrangeByScore("key", "start", "end");
    jedisCluster.zremrangeByScore("key", 1337, 1338);
  }

  @Test
  public void testZlexcount() {
    jedisCluster.zlexcount("key", "min", "max");
  }

  @Test
  public void testZrangeByLex() {
    jedisCluster.zrangeByLex("key", "min", "max");
    jedisCluster.zrangeByLex("key", "min", "max", 1337, 1338);
  }

  @Test
  public void testZrevrangeByLex() {
    jedisCluster.zrevrangeByLex("key", "max", "min");
    jedisCluster.zrevrangeByLex("key", "max", "min", 1337, 1338);
  }

  @Test
  public void testZremrangeByLex() {
    jedisCluster.zremrangeByLex("key", "min", "max");
  }

  @Test
  public void testLinsert() {
    jedisCluster.linsert("key", LIST_POSITION.AFTER, "pivot", "value");
  }

  @Test
  public void testLpushx() {
    jedisCluster.lpushx("key", "string");
  }

  @Test
  public void testRpushx() {
    jedisCluster.rpushx("key", "string");
  }

  @Test
  public void testBlpop() {
    jedisCluster.blpop("arg");
    jedisCluster.blpop(1337, "arg");
  }

  @Test
  public void testBrpop() {
    jedisCluster.brpop("arg");
    jedisCluster.brpop(1337, "arg");
  }

  @Test
  public void testDel() {
    jedisCluster.del("key");
  }

  @Test
  public void testEcho() {
    jedisCluster.echo("string");
  }

  @Test
  public void testMove() {
    jedisCluster.move("key", 1337);
  }

  @Test
  public void testBitcount() {
    jedisCluster.bitcount("key");
    jedisCluster.bitcount("key", 1337, 1338);
  }

  @Test
  public void testBitpos() {
    jedisCluster.bitpos("key", true);
  }

  @Test
  public void testHscan() {
    jedisCluster.hscan("key", "cursor");
    jedisCluster.hscan("key", "cursor", new ScanParams());
    jedisCluster.hscan("key", 1337);
  }

  @Test
  public void testSscan() {
    jedisCluster.sscan("key", "cursor");
    jedisCluster.sscan("key", "cursor", new ScanParams());
    jedisCluster.sscan("key", 1337);
  }

  @Test
  public void testZscan() {
    jedisCluster.zscan("key", "cursor");
    jedisCluster.zscan("key", "cursor", new ScanParams());
    jedisCluster.zscan("key", 1337);
  }

  @Test
  public void testPfadd() {
    jedisCluster.pfadd("key", "elements");
  }

  @Test
  public void testPfcount() {
    jedisCluster.pfcount("key");
  }

  @Test
  public void testGeoadd() {
    jedisCluster.geoadd("key", new HashMap<>());
    jedisCluster.geoadd("key", 1337, 1338, "member");
  }

  @Test
  public void testGeodist() {
    jedisCluster.geodist("key", "member1", "member2");
    jedisCluster.geodist("key", "member1", "member2", GeoUnit.KM);
  }

  @Test
  public void testGeohash() {
    jedisCluster.geohash("key", "members");
  }

  @Test
  public void testGeopos() {
    jedisCluster.geopos("key", "members");
  }

  @Test
  public void testGeoradius() {
    jedisCluster.georadius("key", 1337, 1338, 32, GeoUnit.KM);
    jedisCluster.georadius("key", 1337, 1338, 32, GeoUnit.KM, GeoRadiusParam.geoRadiusParam());
  }

  @Test
  public void testGeoradiusByMember() {
    jedisCluster.georadiusByMember("key", "member", 1337, GeoUnit.KM);
    jedisCluster.georadiusByMember("key", "member", 1337, GeoUnit.KM, GeoRadiusParam.geoRadiusParam());
  }

  @Test
  public void testBitfield() {
    jedisCluster.bitfield("key", "arguments");
  }
}