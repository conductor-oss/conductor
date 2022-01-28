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

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import redis.clients.jedis.GeoUnit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.ListPosition;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.SortingParams;
import redis.clients.jedis.params.GeoRadiusParam;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.params.ZIncrByParams;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JedisSentinelTest {

    private final Jedis jedis = mock(Jedis.class);
    private final JedisSentinelPool jedisPool = mock(JedisSentinelPool.class);
    private final JedisSentinel jedisSentinel = new JedisSentinel(jedisPool);

    @Before
    public void init() {
        when(this.jedisPool.getResource()).thenReturn(this.jedis);
    }

    @Test
    public void testSet() {
        jedisSentinel.set("key", "value");
        jedisSentinel.set("key", "value", SetParams.setParams());
    }

    @Test
    public void testGet() {
        jedisSentinel.get("key");
    }

    @Test
    public void testExists() {
        jedisSentinel.exists("key");
    }

    @Test
    public void testPersist() {
        jedisSentinel.persist("key");
    }

    @Test
    public void testType() {
        jedisSentinel.type("key");
    }

    @Test
    public void testExpire() {
        jedisSentinel.expire("key", 1337);
    }

    @Test
    public void testPexpire() {
        jedisSentinel.pexpire("key", 1337);
    }

    @Test
    public void testExpireAt() {
        jedisSentinel.expireAt("key", 1337);
    }

    @Test
    public void testPexpireAt() {
        jedisSentinel.pexpireAt("key", 1337);
    }

    @Test
    public void testTtl() {
        jedisSentinel.ttl("key");
    }

    @Test
    public void testPttl() {
        jedisSentinel.pttl("key");
    }

    @Test
    public void testSetbit() {
        jedisSentinel.setbit("key", 1337, "value");
        jedisSentinel.setbit("key", 1337, true);
    }

    @Test
    public void testGetbit() {
        jedisSentinel.getbit("key", 1337);
    }

    @Test
    public void testSetrange() {
        jedisSentinel.setrange("key", 1337, "value");
    }

    @Test
    public void testGetrange() {
        jedisSentinel.getrange("key", 1337, 1338);
    }

    @Test
    public void testGetSet() {
        jedisSentinel.getSet("key", "value");
    }

    @Test
    public void testSetnx() {
        jedisSentinel.setnx("test", "value");
    }

    @Test
    public void testSetex() {
        jedisSentinel.setex("key", 1337, "value");
    }

    @Test
    public void testPsetex() {
        jedisSentinel.psetex("key", 1337, "value");
    }

    @Test
    public void testDecrBy() {
        jedisSentinel.decrBy("key", 1337);
    }

    @Test
    public void testDecr() {
        jedisSentinel.decr("key");
    }

    @Test
    public void testIncrBy() {
        jedisSentinel.incrBy("key", 1337);
    }

    @Test
    public void testIncrByFloat() {
        jedisSentinel.incrByFloat("key", 1337);
    }

    @Test
    public void testIncr() {
        jedisSentinel.incr("key");
    }

    @Test
    public void testAppend() {
        jedisSentinel.append("key", "value");
    }

    @Test
    public void testSubstr() {
        jedisSentinel.substr("key", 1337, 1338);
    }

    @Test
    public void testHset() {
        jedisSentinel.hset("key", "field", "value");
    }

    @Test
    public void testHget() {
        jedisSentinel.hget("key", "field");
    }

    @Test
    public void testHsetnx() {
        jedisSentinel.hsetnx("key", "field", "value");
    }

    @Test
    public void testHmset() {
        jedisSentinel.hmset("key", new HashMap<>());
    }

    @Test
    public void testHmget() {
        jedisSentinel.hmget("key", "fields");
    }

    @Test
    public void testHincrBy() {
        jedisSentinel.hincrBy("key", "field", 1337);
    }

    @Test
    public void testHincrByFloat() {
        jedisSentinel.hincrByFloat("key", "field", 1337);
    }

    @Test
    public void testHexists() {
        jedisSentinel.hexists("key", "field");
    }

    @Test
    public void testHdel() {
        jedisSentinel.hdel("key", "field");
    }

    @Test
    public void testHlen() {
        jedisSentinel.hlen("key");
    }

    @Test
    public void testHkeys() {
        jedisSentinel.hkeys("key");
    }

    @Test
    public void testHvals() {
        jedisSentinel.hvals("key");
    }

    @Test
    public void testGgetAll() {
        jedisSentinel.hgetAll("key");
    }

    @Test
    public void testRpush() {
        jedisSentinel.rpush("key", "string");
    }

    @Test
    public void testLpush() {
        jedisSentinel.lpush("key", "string");
    }

    @Test
    public void testLlen() {
        jedisSentinel.llen("key");
    }

    @Test
    public void testLrange() {
        jedisSentinel.lrange("key", 1337, 1338);
    }

    @Test
    public void testLtrim() {
        jedisSentinel.ltrim("key", 1337, 1338);
    }

    @Test
    public void testLindex() {
        jedisSentinel.lindex("key", 1337);
    }

    @Test
    public void testLset() {
        jedisSentinel.lset("key", 1337, "value");
    }

    @Test
    public void testLrem() {
        jedisSentinel.lrem("key", 1337, "value");
    }

    @Test
    public void testLpop() {
        jedisSentinel.lpop("key");
    }

    @Test
    public void testRpop() {
        jedisSentinel.rpop("key");
    }

    @Test
    public void testSadd() {
        jedisSentinel.sadd("key", "member");
    }

    @Test
    public void testSmembers() {
        jedisSentinel.smembers("key");
    }

    @Test
    public void testSrem() {
        jedisSentinel.srem("key", "member");
    }

    @Test
    public void testSpop() {
        jedisSentinel.spop("key");
        jedisSentinel.spop("key", 1337);
    }

    @Test
    public void testScard() {
        jedisSentinel.scard("key");
    }

    @Test
    public void testSismember() {
        jedisSentinel.sismember("key", "member");
    }

    @Test
    public void testSrandmember() {
        jedisSentinel.srandmember("key");
        jedisSentinel.srandmember("key", 1337);
    }

    @Test
    public void testStrlen() {
        jedisSentinel.strlen("key");
    }

    @Test
    public void testZadd() {
        jedisSentinel.zadd("key", new HashMap<>());
        jedisSentinel.zadd("key", new HashMap<>(), ZAddParams.zAddParams());
        jedisSentinel.zadd("key", 1337, "members");
        jedisSentinel.zadd("key", 1337, "members", ZAddParams.zAddParams());
    }

    @Test
    public void testZrange() {
        jedisSentinel.zrange("key", 1337, 1338);
    }

    @Test
    public void testZrem() {
        jedisSentinel.zrem("key", "member");
    }

    @Test
    public void testZincrby() {
        jedisSentinel.zincrby("key", 1337, "member");
        jedisSentinel.zincrby("key", 1337, "member", ZIncrByParams.zIncrByParams());
    }

    @Test
    public void testZrank() {
        jedisSentinel.zrank("key", "member");
    }

    @Test
    public void testZrevrank() {
        jedisSentinel.zrevrank("key", "member");
    }

    @Test
    public void testZrevrange() {
        jedisSentinel.zrevrange("key", 1337, 1338);
    }

    @Test
    public void testZrangeWithScores() {
        jedisSentinel.zrangeWithScores("key", 1337, 1338);
    }

    @Test
    public void testZrevrangeWithScores() {
        jedisSentinel.zrevrangeWithScores("key", 1337, 1338);
    }

    @Test
    public void testZcard() {
        jedisSentinel.zcard("key");
    }

    @Test
    public void testZscore() {
        jedisSentinel.zscore("key", "member");
    }

    @Test
    public void testSort() {
        jedisSentinel.sort("key");
        jedisSentinel.sort("key", new SortingParams());
    }

    @Test
    public void testZcount() {
        jedisSentinel.zcount("key", "min", "max");
        jedisSentinel.zcount("key", 1337, 1338);
    }

    @Test
    public void testZrangeByScore() {
        jedisSentinel.zrangeByScore("key", "min", "max");
        jedisSentinel.zrangeByScore("key", 1337, 1338);
        jedisSentinel.zrangeByScore("key", "min", "max", 1337, 1338);
        jedisSentinel.zrangeByScore("key", 1337, 1338, 1339, 1340);
    }

    @Test
    public void testZrevrangeByScore() {
        jedisSentinel.zrevrangeByScore("key", "max", "min");
        jedisSentinel.zrevrangeByScore("key", 1337, 1338);
        jedisSentinel.zrevrangeByScore("key", "max", "min", 1337, 1338);
        jedisSentinel.zrevrangeByScore("key", 1337, 1338, 1339, 1340);
    }

    @Test
    public void testZrangeByScoreWithScores() {
        jedisSentinel.zrangeByScoreWithScores("key", "min", "max");
        jedisSentinel.zrangeByScoreWithScores("key", "min", "max", 1337, 1338);
        jedisSentinel.zrangeByScoreWithScores("key", 1337, 1338);
        jedisSentinel.zrangeByScoreWithScores("key", 1337, 1338, 1339, 1340);
    }

    @Test
    public void testZrevrangeByScoreWithScores() {
        jedisSentinel.zrevrangeByScoreWithScores("key", "max", "min");
        jedisSentinel.zrevrangeByScoreWithScores("key", "max", "min", 1337, 1338);
        jedisSentinel.zrevrangeByScoreWithScores("key", 1337, 1338);
        jedisSentinel.zrevrangeByScoreWithScores("key", 1337, 1338, 1339, 1340);
    }

    @Test
    public void testZremrangeByRank() {
        jedisSentinel.zremrangeByRank("key", 1337, 1338);
    }

    @Test
    public void testZremrangeByScore() {
        jedisSentinel.zremrangeByScore("key", "start", "end");
        jedisSentinel.zremrangeByScore("key", 1337, 1338);
    }

    @Test
    public void testZlexcount() {
        jedisSentinel.zlexcount("key", "min", "max");
    }

    @Test
    public void testZrangeByLex() {
        jedisSentinel.zrangeByLex("key", "min", "max");
        jedisSentinel.zrangeByLex("key", "min", "max", 1337, 1338);
    }

    @Test
    public void testZrevrangeByLex() {
        jedisSentinel.zrevrangeByLex("key", "max", "min");
        jedisSentinel.zrevrangeByLex("key", "max", "min", 1337, 1338);
    }

    @Test
    public void testZremrangeByLex() {
        jedisSentinel.zremrangeByLex("key", "min", "max");
    }

    @Test
    public void testLinsert() {
        jedisSentinel.linsert("key", ListPosition.AFTER, "pivot", "value");
    }

    @Test
    public void testLpushx() {
        jedisSentinel.lpushx("key", "string");
    }

    @Test
    public void testRpushx() {
        jedisSentinel.rpushx("key", "string");
    }

    @Test
    public void testBlpop() {
        jedisSentinel.blpop(1337, "arg");
    }

    @Test
    public void testBrpop() {
        jedisSentinel.brpop(1337, "arg");
    }

    @Test
    public void testDel() {
        jedisSentinel.del("key");
    }

    @Test
    public void testEcho() {
        jedisSentinel.echo("string");
    }

    @Test
    public void testMove() {
        jedisSentinel.move("key", 1337);
    }

    @Test
    public void testBitcount() {
        jedisSentinel.bitcount("key");
        jedisSentinel.bitcount("key", 1337, 1338);
    }

    @Test
    public void testBitpos() {
        jedisSentinel.bitpos("key", true);
    }

    @Test
    public void testHscan() {
        jedisSentinel.hscan("key", "cursor");
        jedisSentinel.hscan("key", "cursor", new ScanParams());
    }

    @Test
    public void testSscan() {
        jedisSentinel.sscan("key", "cursor");
        jedisSentinel.sscan("key", "cursor", new ScanParams());
    }

    @Test
    public void testZscan() {
        jedisSentinel.zscan("key", "cursor");
        jedisSentinel.zscan("key", "cursor", new ScanParams());
    }

    @Test
    public void testPfadd() {
        jedisSentinel.pfadd("key", "elements");
    }

    @Test
    public void testPfcount() {
        jedisSentinel.pfcount("key");
    }

    @Test
    public void testGeoadd() {
        jedisSentinel.geoadd("key", new HashMap<>());
        jedisSentinel.geoadd("key", 1337, 1338, "member");
    }

    @Test
    public void testGeodist() {
        jedisSentinel.geodist("key", "member1", "member2");
        jedisSentinel.geodist("key", "member1", "member2", GeoUnit.KM);
    }

    @Test
    public void testGeohash() {
        jedisSentinel.geohash("key", "members");
    }

    @Test
    public void testGeopos() {
        jedisSentinel.geopos("key", "members");
    }

    @Test
    public void testGeoradius() {
        jedisSentinel.georadius("key", 1337, 1338, 32, GeoUnit.KM);
        jedisSentinel.georadius("key", 1337, 1338, 32, GeoUnit.KM, GeoRadiusParam.geoRadiusParam());
    }

    @Test
    public void testGeoradiusByMember() {
        jedisSentinel.georadiusByMember("key", "member", 1337, GeoUnit.KM);
        jedisSentinel.georadiusByMember(
                "key", "member", 1337, GeoUnit.KM, GeoRadiusParam.geoRadiusParam());
    }

    @Test
    public void testBitfield() {
        jedisSentinel.bitfield("key", "arguments");
    }
}
