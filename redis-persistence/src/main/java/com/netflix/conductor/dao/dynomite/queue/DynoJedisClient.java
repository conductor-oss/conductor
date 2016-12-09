/*******************************************************************************
 * Copyright 2011 Netflix
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.netflix.conductor.dao.dynomite.queue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.dyno.connectionpool.CompressionOperation;
import com.netflix.dyno.connectionpool.ConnectionContext;
import com.netflix.dyno.connectionpool.ConnectionPool;
import com.netflix.dyno.connectionpool.ConnectionPoolConfiguration;
import com.netflix.dyno.connectionpool.ConnectionPoolConfiguration.CompressionStrategy;
import com.netflix.dyno.connectionpool.CursorBasedResult;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.Operation;
import com.netflix.dyno.connectionpool.OperationResult;
import com.netflix.dyno.connectionpool.TopologyView;
import com.netflix.dyno.connectionpool.exception.DynoConnectException;
import com.netflix.dyno.connectionpool.exception.DynoException;
import com.netflix.dyno.connectionpool.exception.NoAvailableHostsException;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolImpl;
import com.netflix.dyno.connectionpool.impl.lb.HttpEndpointBasedTokenMapSupplier;
import com.netflix.dyno.connectionpool.impl.utils.CollectionUtils;
import com.netflix.dyno.connectionpool.impl.utils.ZipUtils;
import com.netflix.dyno.contrib.ArchaiusConnectionPoolConfiguration;
import com.netflix.dyno.contrib.DynoCPMonitor;
import com.netflix.dyno.contrib.DynoOPMonitor;
import com.netflix.dyno.contrib.EurekaHostsSupplier;
import com.netflix.dyno.jedis.CursorBasedResultImpl;
import com.netflix.dyno.jedis.DynoDualWriterClient;
import com.netflix.dyno.jedis.DynoJedisPipeline;
import com.netflix.dyno.jedis.DynoJedisPipelineMonitor;
import com.netflix.dyno.jedis.JedisConnectionFactory;
import com.netflix.dyno.jedis.OpName;

import redis.clients.jedis.BinaryClient;
import redis.clients.jedis.BinaryClient.LIST_POSITION;
import redis.clients.jedis.BinaryJedisCommands;
import redis.clients.jedis.BitOP;
import redis.clients.jedis.BitPosParams;
import redis.clients.jedis.GeoCoordinate;
import redis.clients.jedis.GeoRadiusResponse;
import redis.clients.jedis.GeoUnit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCommands;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.MultiKeyCommands;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.SortingParams;
import redis.clients.jedis.Tuple;
import redis.clients.jedis.ZParams;
import redis.clients.jedis.params.geo.GeoRadiusParam;
import redis.clients.jedis.params.sortedset.ZAddParams;
import redis.clients.jedis.params.sortedset.ZIncrByParams;


public class DynoJedisClient implements JedisCommands, BinaryJedisCommands, MultiKeyCommands {

    private static final Logger Logger = org.slf4j.LoggerFactory.getLogger(DynoJedisClient.class);

    private final String appName;
    private final String clusterName;
    private final ConnectionPool<Jedis> connPool;
    private final AtomicReference<DynoJedisPipelineMonitor> pipelineMonitor = new AtomicReference<DynoJedisPipelineMonitor>();
    private final EnumSet<OpName> compressionOperations = EnumSet.of(OpName.APPEND);

    protected final DynoOPMonitor opMonitor;

    public DynoJedisClient(String name, String clusterName, ConnectionPool<Jedis> pool, DynoOPMonitor operationMonitor) {
        this.appName = name;
        this.clusterName = clusterName;
        this.connPool = pool;
        this.opMonitor = operationMonitor;
    }

    public ConnectionPoolImpl<Jedis> getConnPool() {
        return (ConnectionPoolImpl<Jedis>) connPool;
    }

    public String getApplicationName() {
        return appName;
    }

    public String getClusterName() {
        return clusterName;
    }

    private abstract class BaseKeyOperation<T> implements Operation<Jedis, T> {

        private final String key;
        private final byte[] binaryKey;
        private final OpName op;

        private BaseKeyOperation(final String k, final OpName o) {
            this.key = k;
            this.binaryKey = null;
            this.op = o;
        }
        
        private BaseKeyOperation(final byte[] k, final OpName o) {
        	this.key = null;
        	this.binaryKey = null;
        	this.op = o;
        }
        
        @Override
        public String getName() {
            return op.name();
        }

        @Override
        public String getKey() {
            return this.key;
        }
        
        public byte[] getBinaryKey() {
        	return this.binaryKey;
        }
        
    }
 


    /**
     * The following commands are supported
     *
     * <ul>
     *     <lh>String Operations</lh>
     *     <li>{@link #get(String) GET}</li>
     *     <li>{@link #getSet(String, String) GETSET}</li>
     *     <li>{@link #set(String, String) SET}</li>
     *     <li>{@link #setex(String, int, String) SETEX}</li>
     * </ul>
     * <ul>
     *     <lh>Hash Operations</lh>
     *     <li>{@link #hget(String, String) HGET}</li>
     *     <li>{@link #hgetAll(String) HGETALL}</li>
     *     <li>{@link #hmget(String, String...) HMGET}</li>
     *     <li>{@link #hmset(String, Map) HMSET}</li>
     *     <li>{@link #hscan(String, String) HSCAN}</li>
     *     <li>{@link #hset(String, String, String) HSET}</li>
     *     <li>{@link #hsetnx(String, String, String) HSETNX}</li>
     *     <li>{@link #hvals(String) HVALS}</li>
     * </ul>
     * 
     * <ul>
     *     <li>{@link #get(byte[]) GET}</li>
     *     <li>{@link #set(byte[], byte[]) SET}</li>
     *     <li>{@link #setex(byte[], int, byte[]) SETEX}</li>
     * </ul>
     *
     * @param <T> the parameterized type
     */
    private abstract class CompressionValueOperation<T> extends BaseKeyOperation<T> implements CompressionOperation<Jedis, T> {

        private CompressionValueOperation(String k, OpName o) {
            super(k, o);
        }

        /**
         * Compresses the value based on the threshold defined by
         * {@link ConnectionPoolConfiguration#getValueCompressionThreshold()}
         *
         * @param value
         * @return 
         */
        @Override
        public String compressValue(String value, ConnectionContext ctx) {
            String result = value;
            int thresholdBytes = connPool.getConfiguration().getValueCompressionThreshold();

            try {
                // prefer speed over accuracy here so rather than using getBytes() to get the actual size
                // just estimate using 2 bytes per character
                if ((2 * value.length()) > thresholdBytes) {
                    result = ZipUtils.compressStringToBase64String(value);
                    ctx.setMetadata("compression", true);
                }
            } catch (IOException e) {
                Logger.warn("UNABLE to compress [" + value + "] for key [" + getKey() + "]; sending value uncompressed");
            }

            return result;
        }

        @Override
        public String decompressValue(String value, ConnectionContext ctx) {
            try {
                if (ZipUtils.isCompressed(value)) {
                    ctx.setMetadata("decompression", true);
                    return ZipUtils.decompressFromBase64String(value);
                }
            } catch (IOException e) {
                Logger.warn("Unable to decompress value [" + value + "]");
            }

            return value;
        }

    }

    public TopologyView getTopologyView() {
        return this.getConnPool();
    }

    @Override
    public Long append(final String key, final String value) {
        return d_append(key, value).getResult();
    }

    public OperationResult<Long> d_append(final String key, final String value) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.APPEND) {
            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.append(key, value);
            }
        });
    }

    @Override
    public Long decr(final String key) {
        return d_decr(key).getResult();
    }

    public OperationResult<Long> d_decr(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.DECR) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.decr(key);
            }

        });
    }

    @Override
    public Long decrBy(final String key, final long delta) {
        return d_decrBy(key, delta).getResult();
    }

    public OperationResult<Long> d_decrBy(final String key, final Long delta) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.DECRBY) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.decrBy(key, delta);
            }

        });
    }

    @Override
    public Long del(final String key) {
        return d_del(key).getResult();
    }

    public OperationResult<Long> d_del(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.DEL) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.del(key);
            }

        });
    }

    public byte[] dump(final String key) {
        return d_dump(key).getResult();
    }

    public OperationResult<byte[]> d_dump(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<byte[]>(key, OpName.DUMP) {

            @Override
            public byte[] execute(Jedis client, ConnectionContext state) {
                return client.dump(key);
            }

        });
    }

    @Override
    public Boolean exists(final String key) {
        return d_exists(key).getResult();
    }

    public OperationResult<Boolean> d_exists(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Boolean>(key, OpName.EXISTS) {

            @Override
            public Boolean execute(Jedis client, ConnectionContext state) {
                return client.exists(key);
            }

        });
    }

    @Override
    public Long expire(final String key, final int seconds) {
        return d_expire(key, seconds).getResult();
    }
    
    public OperationResult<Long> d_expire(final String key, final int seconds) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.EXPIRE) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.expire(key, seconds);
            }

        });
    }


    @Override
    public Long expireAt(final String key, final long unixTime) {
        return d_expireAt(key, unixTime).getResult();
    }

    public OperationResult<Long> d_expireAt(final String key, final long unixTime) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.EXPIREAT) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.expireAt(key, unixTime);
            }

        });
    }

    @Override
    public String get(final String key) {
        return d_get(key).getResult();
    }

    public OperationResult<String> d_get(final String key) {

        if (CompressionStrategy.NONE == connPool.getConfiguration().getCompressionStrategy()) {
            return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.GET) {
                @Override
                public String execute(Jedis client, ConnectionContext state) throws DynoException {
                    return client.get(key);
                }
            });
        } else {
            return connPool.executeWithFailover(new CompressionValueOperation<String>(key, OpName.GET) {
                @Override
                public String execute(final Jedis client, final ConnectionContext state) throws DynoException {
                    return decompressValue(client.get(key), state);
                }
            });
        }
    }

    @Override
    public Boolean getbit(final String key, final long offset) {
        return d_getbit(key, offset).getResult();
    }

    public OperationResult<Boolean> d_getbit(final String key, final Long offset) {

        return connPool.executeWithFailover(new BaseKeyOperation<Boolean>(key, OpName.GETBIT) {

            @Override
            public Boolean execute(Jedis client, ConnectionContext state) {
                return client.getbit(key, offset);
            }

        });
    }

    @Override
    public String getrange(final String key, final long startOffset, final long endOffset) {
        return d_getrange(key, startOffset, endOffset).getResult();
    }

    public OperationResult<String> d_getrange(final String key, final Long startOffset, final Long endOffset) {

        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.GETRANGE) {

            @Override
            public String execute(Jedis client, ConnectionContext state) {
                return client.getrange(key, startOffset, endOffset);
            }

        });
    }

    @Override
    public String getSet(final String key, final String value) {
        return d_getSet(key, value).getResult();
    }

    public OperationResult<String> d_getSet(final String key, final String value) {
        if (CompressionStrategy.NONE == connPool.getConfiguration().getCompressionStrategy()) {
            return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.GETSET) {
                @Override
                public String execute(Jedis client, ConnectionContext state) throws DynoException {
                    return client.getSet(key, value);
                }
            });
        } else {
            return connPool.executeWithFailover(new CompressionValueOperation<String>(key, OpName.GETSET) {
                @Override
                public String execute(Jedis client, ConnectionContext state) throws DynoException {
                    return decompressValue(client.getSet(key, compressValue(value, state)), state);
                }
            });
        }
    }

    @Override
    public Long hdel(final String key, final String... fields) {
        return d_hdel(key, fields).getResult();
    }

    public OperationResult<Long> d_hdel(final String key, final String... fields) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.HDEL) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.hdel(key, fields);
            }

        });
    }

    @Override
    public Boolean hexists(final String key, final String field) {
        return d_hexists(key, field).getResult();
    }

    public OperationResult<Boolean> d_hexists(final String key, final String field) {

        return connPool.executeWithFailover(new BaseKeyOperation<Boolean>(key, OpName.HEXISTS) {

            @Override
            public Boolean execute(Jedis client, ConnectionContext state) {
                return client.hexists(key, field);
            }

        });
    }

    @Override
    public String hget(final String key, final String field) {
        return d_hget(key, field).getResult();
    }

    public OperationResult<String> d_hget(final String key, final String field) {
        if (CompressionStrategy.NONE == connPool.getConfiguration().getCompressionStrategy()) {
            return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.HGET) {
                @Override
                public String execute(Jedis client, ConnectionContext state) throws DynoException {
                    return client.hget(key, field);
                }
            });
        } else {
            return connPool.executeWithFailover(new CompressionValueOperation<String>(key, OpName.HGET) {
                @Override
                public String execute(final Jedis client, final ConnectionContext state) throws DynoException {
                    return decompressValue(client.hget(key, field), state);
                }
            });
        }
    }

    @Override
    public Map<String, String> hgetAll(final String key) {
        return d_hgetAll(key).getResult();
    }

    public OperationResult<Map<String, String>> d_hgetAll(final String key) {
       if (CompressionStrategy.NONE == connPool.getConfiguration().getCompressionStrategy()) {
           return connPool.executeWithFailover(new BaseKeyOperation<Map<String, String>>(key, OpName.HGETALL) {
                @Override
                public Map<String, String> execute(Jedis client, ConnectionContext state) throws DynoException {
                    return client.hgetAll(key);
                }
           });
        } else {
            return connPool.executeWithFailover(new CompressionValueOperation<Map<String, String>>(key, OpName.HGETALL) {
                @Override
                public Map<String, String> execute(final Jedis client, final ConnectionContext state) {
                    return CollectionUtils.transform(
                            client.hgetAll(key),
                            new CollectionUtils.MapEntryTransform<String, String, String>() {
                                @Override
                                public String get(String key, String val) {
                                    return decompressValue(val, state);
                                }
                            });
                }
            });
        }
    }

    @Override
    public Long hincrBy(final String key, final String field, final long value) {
        return d_hincrBy(key, field, value).getResult();
    }

    public OperationResult<Long> d_hincrBy(final String key, final String field, final long value) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.HINCRBY) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.hincrBy(key, field, value);
            }

        });
    }
    
    /* not supported by RedisPipeline 2.7.3 */
    public Double hincrByFloat(final String key, final String field, final double value) {
        return d_hincrByFloat(key, field, value).getResult();
    }

    public OperationResult<Double> d_hincrByFloat(final String key, final String field, final double value) {

        return connPool.executeWithFailover(new BaseKeyOperation<Double>(key, OpName.HINCRBYFLOAT) {

            @Override
            public Double execute(Jedis client, ConnectionContext state) {
                return client.hincrByFloat(key, field, value);
            }

        });
    }

    @Override
    public Long hsetnx(final String key, final String field, final String value) {
        return d_hsetnx(key, field, value).getResult();
    }

    public OperationResult<Long> d_hsetnx(final String key, final String field, final String value) {
        if (CompressionStrategy.NONE == connPool.getConfiguration().getCompressionStrategy()) {
            return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.HSETNX) {
                @Override
                public Long execute(Jedis client, ConnectionContext state) {
                    return client.hsetnx(key, field, value);
                }

            });
        } else {
            return connPool.executeWithFailover(new CompressionValueOperation<Long>(key, OpName.HSETNX) {
                @Override
                public Long execute(final Jedis client, final ConnectionContext state) throws DynoException {
                    return client.hsetnx(key, field, compressValue(value, state));
                }
            });
        }
    }

    @Override
    public Set<String> hkeys(final String key) {
        return d_hkeys(key).getResult();
    }

    public OperationResult<Set<String>> d_hkeys(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<String>>(key, OpName.HKEYS) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) {
                return client.hkeys(key);
            }

        });
    }


    @Override
    public ScanResult<Map.Entry<String, String>> hscan(final String key, final int cursor) {
        throw new UnsupportedOperationException("This function is deprecated, use hscan(String, String)");
    }

    @Override
    public ScanResult<Map.Entry<String, String>> hscan(final String key, final String cursor) {
        return d_hscan(key, cursor).getResult();
    }

    public OperationResult<ScanResult<Map.Entry<String, String>>> d_hscan(final String key, final String cursor){
        if (CompressionStrategy.NONE == connPool.getConfiguration().getCompressionStrategy()) {
            return connPool.executeWithFailover(new BaseKeyOperation<ScanResult<Map.Entry<String, String>>>(key, OpName.HSCAN) {
                @Override
                public ScanResult<Map.Entry<String, String>> execute(Jedis client, ConnectionContext state) {
                    return client.hscan(key,cursor);
                }
            });
        } else {
            return connPool.executeWithFailover(new CompressionValueOperation<ScanResult<Map.Entry<String, String>>>(key, OpName.HSCAN) {
                @Override
                public ScanResult<Map.Entry<String, String>> execute(final Jedis client, final ConnectionContext state) {
                    return  new ScanResult<>(cursor, new ArrayList(CollectionUtils.transform(
                            client.hscan(key,cursor).getResult(),
                            new CollectionUtils.Transform<Map.Entry<String,String>, Map.Entry<String,String>>() {
                                @Override
                                public Map.Entry<String,String> get(Map.Entry<String,String> entry) {
                                    entry.setValue(decompressValue(entry.getValue(),state));
                                    return entry;
                                }
                            })));
                }
            });
        }
    }

    private String getCursorValue(final ConnectionContext state, final CursorBasedResult cursor) {
        if (state != null && state.getMetadata("host") != null && cursor != null) {
            return cursor.getCursorForHost(state.getMetadata("host").toString());
        }

        return "0";
    }

    private List<OperationResult<ScanResult<String>>> scatterGatherScan(final CursorBasedResult<String> cursor, final String... pattern) {
        return new ArrayList<>(
                connPool.executeWithRing(new BaseKeyOperation<ScanResult<String>>("SCAN", OpName.SCAN) {
                    @Override
                    public ScanResult<String> execute(final Jedis client, final ConnectionContext state) throws DynoException {
                        if (pattern != null) {
                            ScanParams sp = new ScanParams();
                            for (String s: pattern) {
                                sp.match(s);
                            }
                            return client.scan(getCursorValue(state, cursor), sp);
                        } else {
                            return client.scan(getCursorValue(state, cursor));
                        }
                    }
                }));
    }

    @Override
    public Long hlen(final String key) {
        return d_hlen(key).getResult();
    }

    public OperationResult<Long> d_hlen(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.HLEN) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.hlen(key);
            }

        });
    }

    @Override
    public List<String> hmget(final String key, final String... fields) {
        return d_hmget(key, fields).getResult();
    }


    public OperationResult<List<String>> d_hmget(final String key, final String... fields) {
        if (CompressionStrategy.NONE == connPool.getConfiguration().getCompressionStrategy()) {
            return connPool.executeWithFailover(new BaseKeyOperation<List<String>>(key, OpName.HMGET) {
                @Override
                public List<String> execute(Jedis client, ConnectionContext state) {
                    return client.hmget(key, fields);
                }
            });
        } else {
            return connPool.executeWithFailover(new CompressionValueOperation<List<String>>(key, OpName.HMGET) {
                @Override
                public List<String> execute(final Jedis client, final ConnectionContext state) throws DynoException {
                    return new ArrayList<String>(CollectionUtils.transform(client.hmget(key, fields),
                            new CollectionUtils.Transform<String, String>() {
                        @Override
                        public String get(String s) {
                            return decompressValue(s, state);
                        }
                    }));
                }
            });
        }
    }

    @Override
    public String hmset(final String key, final Map<String, String> hash) {
        return d_hmset(key, hash).getResult();
    }

    public OperationResult<String> d_hmset(final String key, final Map<String, String> hash) {
        if (CompressionStrategy.NONE == connPool.getConfiguration().getCompressionStrategy()) {
            return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.HMSET) {
                @Override
                public String execute(Jedis client, ConnectionContext state) {
                    return client.hmset(key, hash);
                }
            });
        } else {
            return connPool.executeWithFailover(new CompressionValueOperation<String>(key, OpName.HMSET) {
                @Override
                public String execute(final Jedis client, final ConnectionContext state) throws DynoException {
                    return client.hmset(key,
                            CollectionUtils.transform(hash, new CollectionUtils.MapEntryTransform<String, String, String>() {
                                @Override
                                public String get(String key, String val) {
                                    return compressValue(val, state);
                                }
                            })
                    );
                }
            });
        }
    }


    @Override
    public Long hset(final String key, final String field, final String value) {
        return d_hset(key, field, value).getResult();
    }

    public OperationResult<Long> d_hset(final String key, final String field, final String value) {
        if (CompressionStrategy.NONE == connPool.getConfiguration().getCompressionStrategy()) {
            return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.HSET) {
                @Override
                public Long execute(Jedis client, ConnectionContext state) {
                    return client.hset(key, field, value);
                }

            });
        } else {
            return connPool.executeWithFailover(new CompressionValueOperation<Long>(key, OpName.HSET) {
                @Override
                public Long execute(final Jedis client, final ConnectionContext state) throws DynoException {
                    return client.hset(key, field, compressValue(value, state));
                }
            });
        }
    }

    @Override
    public List<String> hvals(final String key) {
        return d_hvals(key).getResult();
    }

    public OperationResult<List<String>> d_hvals(final String key) {
        if (CompressionStrategy.NONE == connPool.getConfiguration().getCompressionStrategy()) {
            return connPool.executeWithFailover(new BaseKeyOperation<List<String>>(key, OpName.HVALS) {
                @Override
                public List<String> execute(Jedis client, ConnectionContext state) {
                    return client.hvals(key);
                }

            });
        } else {
            return connPool.executeWithFailover(new CompressionValueOperation<List<String>>(key, OpName.HVALS) {
                @Override
                public List<String> execute(final Jedis client, final ConnectionContext state) throws DynoException {
                    return new ArrayList<String>(CollectionUtils.transform(client.hvals(key),
                            new CollectionUtils.Transform<String, String>() {
                                @Override
                                public String get(String s) {
                                    return decompressValue(s, state);
                                }
                            }));
                }
            });
        }
    }

    @Override
    public Long incr(final String key) {
        return d_incr(key).getResult();
    }

    public OperationResult<Long> d_incr(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.INCR) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.incr(key);
            }

        });
    }

    @Override
    public Long incrBy(final String key, final long delta) {
        return d_incrBy(key, delta).getResult();
    }

    public OperationResult<Long> d_incrBy(final String key, final Long delta) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.INCRBY) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.incrBy(key, delta);
            }

        });
    }

    public Double incrByFloat(final String key, final double increment) {
        return d_incrByFloat(key, increment).getResult();
    }

    public OperationResult<Double> d_incrByFloat(final String key, final Double increment) {

        return connPool.executeWithFailover(new BaseKeyOperation<Double>(key, OpName.INCRBYFLOAT) {

            @Override
            public Double execute(Jedis client, ConnectionContext state) {
                return client.incrByFloat(key, increment);
            }

        });
    }

    @Override
    public String lindex(final String key, final long index) {
        return d_lindex(key, index).getResult();
    }

    public OperationResult<String> d_lindex(final String key, final Long index) {

        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.LINDEX) {

            @Override
            public String execute(Jedis client, ConnectionContext state) {
                return client.lindex(key, index);
            }

        });
    }

    @Override
    public Long linsert(final String key, final LIST_POSITION where, final String pivot, final String value) {
        return d_linsert(key, where, pivot, value).getResult();
    }

    public OperationResult<Long> d_linsert(final String key, final LIST_POSITION where, final String pivot, final String value) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.LINSERT) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.linsert(key, where, pivot, value);
            }

        });
    }

    @Override
    public Long llen(final String key) {
        return d_llen(key).getResult();
    }

    public OperationResult<Long> d_llen(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.LLEN) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.llen(key);
            }

        });
    }

    @Override
    public String lpop(final String key) {
        return d_lpop(key).getResult();
    }

    public OperationResult<String> d_lpop(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.LPOP) {

            @Override
            public String execute(Jedis client, ConnectionContext state) {
                return client.lpop(key);
            }

        });
    }

    @Override
    public Long lpush(final String key, final String... values) {
        return d_lpush(key, values).getResult();
    }

    public OperationResult<Long> d_lpush(final String key, final String... values) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.LPUSH) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.lpush(key, values);
            }

        });
    }

    @Override
    public Long lpushx(final String key, final String... values) {
        return d_lpushx(key, values).getResult();
    }

    public OperationResult<Long> d_lpushx(final String key, final String... values) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.LPUSHX) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.lpushx(key, values);
            }

        });
    }

    @Override
    public List<String> lrange(final String key, final long start, final long end) {
        return d_lrange(key, start, end).getResult();
    }

    public OperationResult<List<String>> d_lrange(final String key, final Long start, final Long end) {

        return connPool.executeWithFailover(new BaseKeyOperation<List<String>>(key, OpName.LRANGE) {

            @Override
            public List<String> execute(Jedis client, ConnectionContext state) {
                return client.lrange(key, start, end);
            }

        });
    }

    @Override
    public Long lrem(final String key, final long count, final String value) {
        return d_lrem(key, count, value).getResult();
    }

    public OperationResult<Long> d_lrem(final String key, final Long count, final String value) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.LREM) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.lrem(key, count, value);
            }

        });
    }

    @Override
    public String lset(final String key, final long index, final String value) {
        return d_lset(key, index, value).getResult();
    }

    public OperationResult<String> d_lset(final String key, final Long index, final String value) {

        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.LSET) {

            @Override
            public String execute(Jedis client, ConnectionContext state) {
                return client.lset(key, index, value);
            }

        });
    }

    @Override
    public String ltrim(final String key, final long start, final long end) {
        return d_ltrim(key, start, end).getResult();
    }

    public OperationResult<String> d_ltrim(final String key, final long start, final long end) {

        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.LTRIM) {

            @Override
            public String execute(Jedis client, ConnectionContext state) {
                return client.ltrim(key, start, end);
            }

        });
    }

    @Override
    public Long persist(final String key) {
        return d_persist(key).getResult();
    }

    public OperationResult<Long> d_persist(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.PERSIST) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.persist(key);
            }

        });
    }

    public Long pexpireAt(final String key, final long millisecondsTimestamp) {
        return d_pexpireAt(key, millisecondsTimestamp).getResult();
    }

    public OperationResult<Long> d_pexpireAt(final String key, final Long millisecondsTimestamp) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.PEXPIREAT) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.pexpireAt(key, millisecondsTimestamp);
            }

        });
    }


    public Long pttl(final String key) {
        return d_pttl(key).getResult();
    }

    public OperationResult<Long> d_pttl(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.PTTL) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.pttl(key);
            }

        });
    }
    
    @Override
    public String rename(String oldkey, String newkey) {
        return d_rename(oldkey, newkey).getResult();
    }
   
    public OperationResult<String> d_rename(final String oldkey, final String newkey) {

    	   return connPool.executeWithFailover(new BaseKeyOperation<String>(oldkey, OpName.RENAME) {

               @Override
               public String execute(Jedis client, ConnectionContext state) {
                   return client.rename(oldkey, newkey);
               }

           });
    }
    
    @Override
    public Long renamenx(String oldkey, String newkey) {
        return d_renamenx(oldkey, newkey).getResult();
    }
   
    public OperationResult<Long> d_renamenx(final String oldkey, final String newkey) {

    	   return connPool.executeWithFailover(new BaseKeyOperation<Long>(oldkey, OpName.RENAMENX) {

               @Override
               public Long execute(Jedis client, ConnectionContext state) {
                   return client.renamenx(oldkey, newkey);
               }

           });
    }

    public String restore(final String key, final Integer ttl, final byte[] serializedValue) {
        return d_restore(key, ttl, serializedValue).getResult();
    }

    public OperationResult<String> d_restore(final String key, final Integer ttl, final byte[] serializedValue) {

        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.RESTORE) {

            @Override
            public String execute(Jedis client, ConnectionContext state) {
                return client.restore(key, ttl, serializedValue);
            }

        });
    }

    public String rpop(final String key) {
        return d_rpop(key).getResult();
    }

    public OperationResult<String> d_rpop(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.RPOP) {

            @Override
            public String execute(Jedis client, ConnectionContext state) {
                return client.rpop(key);
            }

        });
    }

    public String rpoplpush(final String srckey, final String dstkey) {
        return d_rpoplpush(srckey, dstkey).getResult();
    }

    public OperationResult<String> d_rpoplpush(final String srckey, final String dstkey) {

        return connPool.executeWithFailover(new BaseKeyOperation<String>(srckey, OpName.RPOPLPUSH) {

            @Override
            public String execute(Jedis client, ConnectionContext state) {
                return client.rpoplpush(srckey, dstkey);
            }

        });
    }

    public Long rpush(final String key, final String... values) {
        return d_rpush(key, values).getResult();
    }

    public OperationResult<Long> d_rpush(final String key, final String... values) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.RPUSH) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.rpush(key, values);
            }

        });
    }

    @Override
    public Long rpushx(final String key, final String... values) {
        return d_rpushx(key, values).getResult();
    }

    public OperationResult<Long> d_rpushx(final String key, final String... values) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.RPUSHX) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.rpushx(key, values);
            }

        });
    }

    @Override
    public Long sadd(final String key, final String... members) {
        return d_sadd(key, members).getResult();
    }

    public OperationResult<Long> d_sadd(final String key, final String... members) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.SADD) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.sadd(key, members);
            }

        });
    }

    @Override
    public Long scard(final String key) {
        return d_scard(key).getResult();
    }

    public OperationResult<Long> d_scard(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.SCARD) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.scard(key);
            }

        });
    }

    public Set<String> sdiff(final String... keys) {
        return d_sdiff(keys).getResult();
    }

    public OperationResult<Set<String>> d_sdiff(final String... keys) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<String>>(keys[0], OpName.SDIFF) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) {
                return client.sdiff(keys);
            }

        });
    }

    public Long sdiffstore(final String dstkey, final String... keys) {
        return d_sdiffstore(dstkey, keys).getResult();
    }

    public OperationResult<Long> d_sdiffstore(final String dstkey, final String... keys) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(dstkey, OpName.SDIFFSTORE) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.sdiffstore(dstkey, keys);
            }

        });
    }

    @Override
    public String set(final String key, final String value) {
        return d_set(key, value).getResult();
    }

    @Override
    public String set(String key, String value, String nxxx, String expx, long time) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public OperationResult<String> d_set(final String key, final String value) {
        if (CompressionStrategy.NONE == connPool.getConfiguration().getCompressionStrategy()) {
            return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.SET) {
                @Override
                public String execute(Jedis client, ConnectionContext state) throws DynoException {
                    return client.set(key, value);
                }
            });
        } else {
            return connPool.executeWithFailover(new CompressionValueOperation<String>(key, OpName.SET) {
                @Override
                public String execute(final Jedis client, final ConnectionContext state) throws DynoException {
                    return client.set(key, compressValue(value, state));
                }
            });
        }

    }

    @Override
    public Boolean setbit(final String key, final long offset, final boolean value) {
        return d_setbit(key, offset, value).getResult();
    }

    public OperationResult<Boolean> d_setbit(final String key, final Long offset, final Boolean value) {

        return connPool.executeWithFailover(new BaseKeyOperation<Boolean>(key, OpName.SETBIT) {

            @Override
            public Boolean execute(Jedis client, ConnectionContext state) {
                return client.setbit(key, offset, value);
            }

        });
    }

    @Override
    public Boolean setbit(final String key, final long offset, final String value) {
        return d_setbit(key, offset, value).getResult();
    }

    public OperationResult<Boolean> d_setbit(final String key, final Long offset, final String value) {

        return connPool.executeWithFailover(new BaseKeyOperation<Boolean>(key, OpName.SETBIT) {

            @Override
            public Boolean execute(Jedis client, ConnectionContext state) {
                return client.setbit(key, offset, value);
            }

        });
    }

    @Override
    public String setex(final String key, final int seconds, final String value) {
        return d_setex(key, seconds, value).getResult();
    }

    public OperationResult<String> d_setex(final String key, final Integer seconds, final String value) {
        if (CompressionStrategy.NONE == connPool.getConfiguration().getCompressionStrategy()) {
            return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.SETEX) {
                @Override
                public String execute(Jedis client, ConnectionContext state) throws DynoException {
                    return client.setex(key, seconds, value);
                }
            });
        } else {
            return connPool.executeWithFailover(new CompressionValueOperation<String>(key, OpName.SETEX) {
                @Override
                public String execute(final Jedis client, final ConnectionContext state) throws DynoException {
                    return client.setex(key, seconds, compressValue(value, state));
                }
            });
        }
    }

    @Override
    public Long setnx(final String key, final String value) {
        return d_setnx(key, value).getResult();
    }

    public OperationResult<Long> d_setnx(final String key, final String value) {
        if (CompressionStrategy.NONE == connPool.getConfiguration().getCompressionStrategy()) {
            return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.SETNX) {
                @Override
                public Long execute(Jedis client, ConnectionContext state) throws DynoException {
                    return client.setnx(key, value);
                }
            });
        } else {
            return connPool.executeWithFailover(new CompressionValueOperation<Long>(key, OpName.SETNX) {
                @Override
                public Long execute(final Jedis client, final ConnectionContext state) {
                    return client.setnx(key, compressValue(value, state));
                }
            });
        }
    }

    @Override
    public Long setrange(final String key, final long offset, final String value) {
        return d_setrange(key, offset, value).getResult();
    }

    public OperationResult<Long> d_setrange(final String key, final Long offset, final String value) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.SETRANGE) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.setrange(key, offset, value);
            }

        });
    }

    @Override
    public Boolean sismember(final String key, final String member) {
        return d_sismember(key, member).getResult();
    }

    public OperationResult<Boolean> d_sismember(final String key, final String member) {

        return connPool.executeWithFailover(new BaseKeyOperation<Boolean>(key, OpName.SISMEMBER) {

            @Override
            public Boolean execute(Jedis client, ConnectionContext state) {
                return client.sismember(key, member);
            }

        });
    }

    @Override
    public Set<String> smembers(final String key) {
        return d_smembers(key).getResult();
    }

    public OperationResult<Set<String>> d_smembers(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<String>>(key, OpName.SMEMBERS) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) {
                return client.smembers(key);
            }

        });
    }

    public Long smove(final String srckey, final String dstkey, final String member) {
        return d_smove(srckey, dstkey, member).getResult();
    }

    public OperationResult<Long> d_smove(final String srckey, final String dstkey, final String member) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(srckey, OpName.SMOVE) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.smove(srckey, dstkey, member);
            }

        });
    }

    @Override
    public List<String> sort(String key) {
        return d_sort(key).getResult();
    }

    public OperationResult<List<String>> d_sort(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<List<String>>(key, OpName.SORT) {

            @Override
            public List<String> execute(Jedis client, ConnectionContext state) {
                return client.sort(key);
            }

        });
    }

    @Override
    public List<String> sort(String key, SortingParams sortingParameters) {
        return d_sort(key, sortingParameters).getResult();
    }

    public OperationResult<List<String>> d_sort(final String key, final SortingParams sortingParameters) {

        return connPool.executeWithFailover(new BaseKeyOperation<List<String>>(key, OpName.SORT) {

            @Override
            public List<String> execute(Jedis client, ConnectionContext state) {
                return client.sort(key, sortingParameters);
            }

        });
    }

    @Override
    public String spop(final String key) {
        return d_spop(key).getResult();
    }

    public OperationResult<String> d_spop(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.SPOP) {

            @Override
            public String execute(Jedis client, ConnectionContext state) {
                return client.spop(key);
            }

        });
    }
    
    @Override
    public Set<String> spop(String key, long count) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public String srandmember(final String key) {
        return d_srandmember(key).getResult();
    }

    @Override
    public List<String> srandmember(String key, int count) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public OperationResult<String> d_srandmember(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.SRANDMEMBER) {

            @Override
            public String execute(Jedis client, ConnectionContext state) {
                return client.srandmember(key);
            }

        });
    }

    @Override
    public Long srem(final String key, final String... members) {
        return d_srem(key, members).getResult();
    }

    public OperationResult<Long> d_srem(final String key, final String... members) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.SREM) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.srem(key, members);
            }

        });
    }
    
    
    @Override
    public ScanResult<String> sscan(final String key, final int cursor) {
        return d_sscan(key, cursor).getResult();
    }
    
    public OperationResult<ScanResult<String>> d_sscan(final String key, final int cursor) {

        return connPool.executeWithFailover(new BaseKeyOperation<ScanResult<String>>(key, OpName.SSCAN) {

            @Override
            public ScanResult<String> execute(Jedis client, ConnectionContext state) {
                return client.sscan(key, cursor);
            }

        });
    }
    
    @Override
    public ScanResult<String> sscan(final String key, final String cursor) {
        return d_sscan(key, cursor).getResult();
    }
    
    public OperationResult<ScanResult<String>> d_sscan(final String key, final String cursor) {

        return connPool.executeWithFailover(new BaseKeyOperation<ScanResult<String>>(key, OpName.SSCAN) {

            @Override
            public ScanResult<String> execute(Jedis client, ConnectionContext state) {
                return client.sscan(key, cursor);
            }

        });
    }
    
	@Override
	public ScanResult<String> sscan(final String key, final String cursor, final ScanParams params) {
        return d_sscan(key, cursor, params).getResult();
	}
	
    public OperationResult<ScanResult<String>> d_sscan(final String key, final String cursor, final ScanParams params) {

        return connPool.executeWithFailover(new BaseKeyOperation<ScanResult<String>>(key, OpName.SSCAN) {

            @Override
            public ScanResult<String> execute(Jedis client, ConnectionContext state) {
                return client.sscan(key, cursor, params);
            }

        });
    }

    @Override
    public Long strlen(final String key) {
        return d_strlen(key).getResult();
    }

    public OperationResult<Long> d_strlen(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.STRLEN) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.strlen(key);
            }

        });
    }

    @Override
    public String substr(String key, int start, int end) {
        return d_substr(key, start, end).getResult();
    }

    public OperationResult<String> d_substr(final String key, final Integer start, final Integer end) {

        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.SUBSTR) {

            @Override
            public String execute(Jedis client, ConnectionContext state) {
                return client.substr(key, start, end);
            }

        });
    }

    @Override
    public Long ttl(final String key) {
        return d_ttl(key).getResult();
    }

    public OperationResult<Long> d_ttl(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.TTL) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.ttl(key);
            }

        });
    }

    @Override
    public String type(final String key) {
        return d_type(key).getResult();
    }

    public OperationResult<String> d_type(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.TYPE) {

            @Override
            public String execute(Jedis client, ConnectionContext state) {
                return client.type(key);
            }

        });
    }

    @Override
    public Long zadd(String key, double score, String member) {
        return d_zadd(key, score, member).getResult();
    }

    public OperationResult<Long> d_zadd(final String key, final Double score, final String member) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.ZADD) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.zadd(key, score, member);
            }

        });
    }

    @Override
    public Long zadd(String key, Map<String, Double> scoreMembers) {
        return d_zadd(key, scoreMembers).getResult();
    }

    public OperationResult<Long> d_zadd(final String key, final Map<String, Double> scoreMembers) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.ZADD) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.zadd(key, scoreMembers);
            }

        });
    }
    
    @Override
   	public Long zadd(String key, double score, String member, ZAddParams params) {
   		return d_zadd(key, score, member, params).getResult();
   	}
       
       public OperationResult<Long> d_zadd(final String key, final double score, final String member, final ZAddParams params) {
       	
       	return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.ZADD) {

               @Override
               public Long execute(Jedis client, ConnectionContext state) {
               	return client.zadd(key, score, member, params);
               }

           });
       }

    @Override
    public Long zcard(final String key) {
        return d_zcard(key).getResult();
    }

    public OperationResult<Long> d_zcard(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.ZCARD) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.zcard(key);
            }

        });
    }

    @Override
    public Long zcount(final String key, final double min, final double max) {
        return d_zcount(key, min, max).getResult();
    }

    public OperationResult<Long> d_zcount(final String key, final Double min, final Double max) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.ZCOUNT) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.zcount(key, min, max);
            }

        });
    }

    @Override
    public Long zcount(String key, String min, String max) {
        return d_zcount(key, min, max).getResult();
    }

    public OperationResult<Long> d_zcount(final String key, final String min, final String max) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.ZCOUNT) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.zcount(key, min, max);
            }

        });
    }

    @Override
    public Double zincrby(final String key, final double score, final String member) {
        return d_zincrby(key, score, member).getResult();
    }

    public OperationResult<Double> d_zincrby(final String key, final Double score, final String member) {

        return connPool.executeWithFailover(new BaseKeyOperation<Double>(key, OpName.ZINCRBY) {

            @Override
            public Double execute(Jedis client, ConnectionContext state) {
                return client.zincrby(key, score, member);
            }

        });
    }

    @Override
    public Set<String> zrange(String key, long start, long end) {
        return d_zrange(key, start, end).getResult();
    }

    public OperationResult<Set<String>> d_zrange(final String key, final Long start, final Long end) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<String>>(key, OpName.ZRANGE) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) {
                return client.zrange(key, start, end);
            }

        });
    }

    @Override
    public Long zrank(final String key, final String member) {
        return d_zrank(key, member).getResult();
    }

    public OperationResult<Long> d_zrank(final String key, final String member) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.ZRANK) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.zrank(key, member);
            }

        });
    }

    @Override
    public Long zrem(String key, String... member) {
        return d_zrem(key, member).getResult();
    }

    public OperationResult<Long> d_zrem(final String key, final String... member) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.ZREM) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.zrem(key, member);
            }

        });
    }

    @Override
    public Long zremrangeByRank(final String key, final long start, final long end) {
        return d_zremrangeByRank(key, start, end).getResult();
    }

    public OperationResult<Long> d_zremrangeByRank(final String key, final Long start, final Long end) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.ZREMRANGEBYRANK) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.zremrangeByRank(key, start, end);
            }

        });
    }

    @Override
    public Long zremrangeByScore(final String key, final double start, final double end) {
        return d_zremrangeByScore(key, start, end).getResult();
    }

    public OperationResult<Long> d_zremrangeByScore(final String key, final Double start, final Double end) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.ZREMRANGEBYSCORE) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.zremrangeByScore(key, start, end);
            }

        });
    }

    @Override
    public Set<String> zrevrange(String key, long start, long end) {
        return d_zrevrange(key, start, end).getResult();
    }

    public OperationResult<Set<String>> d_zrevrange(final String key, final Long start, final Long end) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<String>>(key, OpName.ZREVRANGE) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) {
                return client.zrevrange(key, start, end);
            }

        });
    }

    @Override
    public Long zrevrank(final String key, final String member) {
        return d_zrevrank(key, member).getResult();
    }

    public OperationResult<Long> d_zrevrank(final String key, final String member) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.ZREVRANK) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.zrevrank(key, member);
            }

        });
    }

    @Override
    public Set<Tuple> zrangeWithScores(String key, long start, long end) {
        return d_zrangeWithScores(key, start, end).getResult();
    }

    public OperationResult<Set<Tuple>> d_zrangeWithScores(final String key, final Long start, final Long end) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<Tuple>>(key, OpName.ZRANGEWITHSCORES) {

            @Override
            public Set<Tuple> execute(Jedis client, ConnectionContext state) {
                return client.zrangeWithScores(key, start, end);
            }

        });
    }

    @Override
    public Set<Tuple> zrevrangeWithScores(String key, long start, long end) {
        return d_zrevrangeWithScores(key, start, end).getResult();
    }

    public OperationResult<Set<Tuple>> d_zrevrangeWithScores(final String key, final Long start, final Long end) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<Tuple>>(key, OpName.ZREVRANGEWITHSCORES) {

            @Override
            public Set<Tuple> execute(Jedis client, ConnectionContext state) {
                return client.zrevrangeWithScores(key, start, end);
            }

        });
    }

    @Override
    public Double zscore(final String key, final String member) {
        return d_zscore(key, member).getResult();
    }

    public OperationResult<Double> d_zscore(final String key, final String member) {

        return connPool.executeWithFailover(new BaseKeyOperation<Double>(key, OpName.ZSCORE) {

            @Override
            public Double execute(Jedis client, ConnectionContext state) {
                return client.zscore(key, member);
            }

        });
    }
    
    @Override
    public ScanResult<Tuple> zscan(final String key, final int cursor) {
    	return d_zscan(key, cursor).getResult();
    }
    
    public OperationResult<ScanResult<Tuple>> d_zscan(final String key, final int cursor){
    	
        return connPool.executeWithFailover(new BaseKeyOperation<ScanResult<Tuple>>(key, OpName.ZSCAN) {
        	 @Override
             public ScanResult<Tuple> execute(Jedis client, ConnectionContext state) {
                 return client.zscan(key, cursor);
             }

         });
    }
    
    @Override
    public ScanResult<Tuple> zscan(final String key, final String cursor) {
    	return d_zscan(key, cursor).getResult();
    }
    
    public OperationResult<ScanResult<Tuple>> d_zscan(final String key, final String cursor){
    	
        return connPool.executeWithFailover(new BaseKeyOperation<ScanResult<Tuple>>(key, OpName.ZSCAN) {
        	 @Override
             public ScanResult<Tuple> execute(Jedis client, ConnectionContext state) {
                 return client.zscan(key, cursor);
             }

         });
    }


    @Override
    public Set<String> zrangeByScore(String key, double min, double max) {
        return d_zrangeByScore(key, min, max).getResult();
    }

    public OperationResult<Set<String>> d_zrangeByScore(final String key, final Double min, final Double max) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<String>>(key, OpName.ZRANGEBYSCORE) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) {
                return client.zrangeByScore(key, min, max);
            }

        });
    }

    @Override
    public Set<String> zrangeByScore(String key, String min, String max) {
        return d_zrangeByScore(key, min, max).getResult();
    }

    public OperationResult<Set<String>> d_zrangeByScore(final String key, final String min, final String max) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<String>>(key, OpName.ZRANGEBYSCORE) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) {
                return client.zrangeByScore(key, min, max);
            }

        });
    }

    @Override
    public Set<String> zrangeByScore(String key, double min, double max, int offset, int count) {
        return d_zrangeByScore(key, min, max, offset, count).getResult();
    }

    public OperationResult<Set<String>> d_zrangeByScore(final String key, final Double min, final Double max, final Integer offset, final Integer count) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<String>>(key, OpName.ZRANGEBYSCORE) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) {
                return client.zrangeByScore(key, min, max, offset, count);
            }

        });
    }

    @Override
    public Set<String> zrevrangeByScore(String key, String max, String min) {
        return d_zrevrangeByScore(key, max, min).getResult();
    }

    public OperationResult<Set<String>> d_zrevrangeByScore(final String key, final String max, final String min) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<String>>(key, OpName.ZREVRANGEBYSCORE) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) {
                return client.zrevrangeByScore(key, max, min);
            }

        });
    }

    @Override
    public Set<String> zrangeByScore(String key, String min, String max, int offset, int count) {
        return d_zrangeByScore(key, min, max, offset, count).getResult();
    }

    public OperationResult<Set<String>> d_zrangeByScore(final String key, final String min, final String max, final Integer offset, final Integer count) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<String>>(key, OpName.ZRANGEBYSCORE) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) {
                return client.zrangeByScore(key, min, max, offset, count);
            }

        });
    }

    @Override
    public Set<String> zrevrangeByScore(String key, double max, double min, int offset, int count) {
        return d_zrevrangeByScore(key, max, min, offset, count).getResult();
    }

    public OperationResult<Set<String>> d_zrevrangeByScore(final String key, final Double max, final Double min, final Integer offset, final Integer count) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<String>>(key, OpName.ZREVRANGEBYSCORE) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) {
                return client.zrevrangeByScore(key, max, min, offset, count);
            }

        });
    }

    @Override
    public Set<String> zrevrangeByScore(String key, double max, double min) {
        return d_zrevrangeByScore(key, max, min).getResult();
    }

    public OperationResult<Set<String>> d_zrevrangeByScore(final String key, final Double max, final Double min) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<String>>(key, OpName.ZREVRANGEBYSCORE) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) {
                return client.zrevrangeByScore(key, max, min);
            }

        });
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(String key, double min, double max) {
        return d_zrangeByScoreWithScores(key, min, max).getResult();
    }

    public OperationResult<Set<Tuple>> d_zrangeByScoreWithScores(final String key, final Double min, final Double max) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<Tuple>>(key, OpName.ZREVRANGEBYSCORE) {

            @Override
            public Set<Tuple> execute(Jedis client, ConnectionContext state) {
                return client.zrangeByScoreWithScores(key, min, max);
            }

        });
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(String key, double max, double min) {
        return d_zrevrangeByScoreWithScores(key, min, max).getResult();
    }

    public OperationResult<Set<Tuple>> d_zrevrangeByScoreWithScores(final String key, final Double max, final Double min) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<Tuple>>(key, OpName.ZREVRANGEBYSCOREWITHSCORES) {

            @Override
            public Set<Tuple> execute(Jedis client, ConnectionContext state) {
                return client.zrevrangeByScoreWithScores(key, max, min);
            }

        });
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(String key, double min, double max, int offset, int count) {
        return d_zrangeByScoreWithScores(key, min, max, offset, count).getResult();
    }

    public OperationResult<Set<Tuple>> d_zrangeByScoreWithScores(final String key, final Double min, final Double max, final Integer offset, final Integer count) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<Tuple>>(key, OpName.ZRANGEBYSCOREWITHSCORES) {

            @Override
            public Set<Tuple> execute(Jedis client, ConnectionContext state) {
                return client.zrangeByScoreWithScores(key, min, max, offset, count);
            }

        });
    }

    @Override
    public Set<String> zrevrangeByScore(String key, String max, String min, int offset, int count) {
        return d_zrevrangeByScore(key, max, min, offset, count).getResult();
    }

    public OperationResult<Set<String>> d_zrevrangeByScore(final String key, final String max, final String min, final Integer offset, final Integer count) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<String>>(key, OpName.ZREVRANGEBYSCORE) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) {
                return client.zrevrangeByScore(key, max, min, offset, count);
            }

        });
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(String key, String min, String max) {
        return d_zrangeByScoreWithScores(key, min, max).getResult();
    }

    public OperationResult<Set<Tuple>> d_zrangeByScoreWithScores(final String key, final String min, final String max) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<Tuple>>(key, OpName.ZRANGEBYSCOREWITHSCORES) {

            @Override
            public Set<Tuple> execute(Jedis client, ConnectionContext state) {
                return client.zrangeByScoreWithScores(key, min, max);
            }

        });
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(String key, String max, String min) {
        return d_zrevrangeByScoreWithScores(key, max, min).getResult();
    }

    public OperationResult<Set<Tuple>> d_zrevrangeByScoreWithScores(final String key, final String max, final String min) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<Tuple>>(key, OpName.ZREVRANGEBYSCOREWITHSCORES) {

            @Override
            public Set<Tuple> execute(Jedis client, ConnectionContext state) {
                return client.zrevrangeByScoreWithScores(key, max, min);
            }

        });
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(String key, String min, String max, int offset, int count) {
        return d_zrangeByScoreWithScores(key, min, max, offset, count).getResult();
    }

    public OperationResult<Set<Tuple>> d_zrangeByScoreWithScores(final String key, final String min, final String max, final Integer offset, final Integer count) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<Tuple>>(key, OpName.ZRANGEBYSCOREWITHSCORES) {

            @Override
            public Set<Tuple> execute(Jedis client, ConnectionContext state) {
                return client.zrangeByScoreWithScores(key, min, max, offset, count);
            }

        });
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(String key, double max, double min, int offset, int count) {
        return d_zrevrangeByScoreWithScores(key, max, min, offset, count).getResult();
    }

    public OperationResult<Set<Tuple>> d_zrevrangeByScoreWithScores(final String key, final Double max, final Double min, final Integer offset, final Integer count) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<Tuple>>(key, OpName.ZREVRANGEBYSCOREWITHSCORES) {

            @Override
            public Set<Tuple> execute(Jedis client, ConnectionContext state) {
                return client.zrevrangeByScoreWithScores(key, max, min, offset, count);
            }

        });
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(String key, String max, String min, int offset, int count) {
        return d_zrevrangeByScoreWithScores(key, max, min, offset, count).getResult();
    }

    public OperationResult<Set<Tuple>> d_zrevrangeByScoreWithScores(final String key, final String max, final String min, final Integer offset, final Integer count) {

        return connPool.executeWithFailover(new BaseKeyOperation<Set<Tuple>>(key, OpName.ZREVRANGEBYSCOREWITHSCORES) {

            @Override
            public Set<Tuple> execute(Jedis client, ConnectionContext state) {
                return client.zrevrangeByScoreWithScores(key, max, min, offset, count);
            }

        });
    }
    
    @Override
    public Long zremrangeByScore(String key, String start, String end) {
        return d_zremrangeByScore(key, start, end).getResult();
    }

    @Override
    public Long zlexcount(String key, String min, String max) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<String> zrangeByLex(String key, String min, String max) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<String> zrangeByLex(String key, String min, String max, int offset, int count) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zremrangeByLex(String key, String min, String max) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public OperationResult<Long> d_zremrangeByScore(final String key, final String start, final String end) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.ZREMRANGEBYSCORE) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.zremrangeByScore(key, start, end);
            }

        });
    }
    

    @Override
    public List<String> blpop(String arg) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<String> blpop(int timeout, String key) {
        return d_blpop(timeout,key).getResult();
    }

    public OperationResult<List<String>> d_blpop(final int timeout, final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<List<String>>(key, OpName.BLPOP) {

            @Override
            public List<String> execute(Jedis client, ConnectionContext state) {
                return client.blpop(timeout,key);
            }

        });
    }

    @Override
    public List<String> brpop(String arg) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<String> brpop(int timeout, String key) {
        return d_brpop(timeout,key).getResult();
    }

    public OperationResult<List<String>> d_brpop(final int timeout, final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<List<String>>(key, OpName.BRPOP) {

            @Override
            public List<String> execute(Jedis client, ConnectionContext state) {
                return client.brpop(timeout,key);
            }

        });
    }


    @Override
    public String echo(String string) {
        return d_echo(string).getResult();
    }

    public OperationResult<String> d_echo(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.ECHO) {

            @Override
            public String execute(Jedis client, ConnectionContext state) {
                return client.echo(key);
            }

        });
    }

    @Override
    public Long move(String key, int dbIndex) {
        return d_move(key, dbIndex).getResult();
    }

    public OperationResult<Long> d_move(final String key, final Integer dbIndex) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.MOVE) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.move(key, dbIndex);
            }

        });
    }

    @Override
    public Long bitcount(String key) {
        return d_bitcount(key).getResult();
    }

    public OperationResult<Long> d_bitcount(final String key) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.BITCOUNT) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.bitcount(key);
            }

        });
    }

    @Override
    public Long bitcount(String key, long start, long end) {
        return d_bitcount(key, start, end).getResult();
    }

    @Override
    public Long pfadd(String key, String... elements) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public long pfcount(String key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public OperationResult<Long> d_bitcount(final String key, final Long start, final Long end) {

        return connPool.executeWithFailover(new BaseKeyOperation<Long>(key, OpName.BITCOUNT) {

            @Override
            public Long execute(Jedis client, ConnectionContext state) {
                return client.bitcount(key, start, end);
            }

        });
    }

    /** MULTI-KEY COMMANDS */

    @Override
    public Long del(String... keys) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<String> blpop(int timeout, String... keys) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<String> brpop(int timeout, String... keys) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<String> blpop(String... args) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<String> brpop(String... args) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<String> keys(String pattern) {

        Set<String> allResults = new HashSet<String>();
        Collection<OperationResult<Set<String>>> results = d_keys(pattern);
        for (OperationResult<Set<String>> result : results) {
            allResults.addAll(result.getResult());
        }
        return allResults;
    }

    /**
     * Use this with care, especially in the context of production databases.
     *
     * @param pattern Specifies the mach set for keys
     * @return a collection of operation results
     * @see <a href="http://redis.io/commands/KEYS">keys</a>
     */
    public Collection<OperationResult<Set<String>>> d_keys(final String pattern) {

        Logger.warn("Executing d_keys for pattern: " + pattern);

        Collection<OperationResult<Set<String>>> results = connPool.executeWithRing(new BaseKeyOperation<Set<String>>(pattern, OpName.KEYS) {

            @Override
            public Set<String> execute(Jedis client, ConnectionContext state) throws DynoException {
                return client.keys(pattern);
            }
        });

        return results;
    }
    
    @Override
    public Long pexpire(String key, long milliseconds) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<String> mget(String... keys) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public String mset(String... keysvalues) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long msetnx(String... keysvalues) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<String> sinter(String... keys) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public Long sinterstore(final String dstkey, final String... keys) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long sort(String key, SortingParams sortingParameters, String dstkey) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long sort(String key, String dstkey) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<String> sunion(String... keys) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long sunionstore(String dstkey, String... keys) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public String watch(String... keys) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public String unwatch() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zinterstore(String dstkey, String... sets) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zinterstore(String dstkey, ZParams params, String... sets) {
        throw new UnsupportedOperationException("not yet implemented");
    }
    
    @Override
    public  Set<String> zrevrangeByLex(String key, String max, String min) {
        throw new UnsupportedOperationException("not yet implemented");
    }
    
    @Override
    public  Set<String> zrevrangeByLex(String key, String max, String min, int offset, int count) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zunionstore(String dstkey, String... sets) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zunionstore(String dstkey, ZParams params, String... sets) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public String brpoplpush(String source, String destination, int timeout) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long publish(String channel, String message) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public void subscribe(JedisPubSub jedisPubSub, String... channels) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public void psubscribe(JedisPubSub jedisPubSub, String... patterns) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public String randomKey() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long bitop(BitOP op, String destKey, String... srcKeys) {
        throw new UnsupportedOperationException("not yet implemented");
    }
    
    /******************* Jedis Binary Commands **************/
    @Override
    public String set(final byte[] key, final byte[] value) {
        return d_set(key,value).getResult();
    }
    
    
    public OperationResult<String> d_set(final byte[] key, final byte[] value) {
        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.SET) {
           @Override
           public String execute(Jedis client, ConnectionContext state) throws DynoException {
                return client.set(key, value);
           }
         });
    }
    
    @Override
    public byte[] get(final byte[] key) {
        return d_get(key).getResult();
    }

    public OperationResult<byte[]> d_get(final byte[] key) {
        return connPool.executeWithFailover(new BaseKeyOperation<byte[]>(key, OpName.GET) {
            @Override
            public byte[] execute(Jedis client, ConnectionContext state) throws DynoException {
                return client.get(key);
            }
        });
      
    }
    
    @Override
    public String setex(final byte[] key, final int seconds, final byte[] value) {
        return d_setex(key, seconds, value).getResult();
    }


    public OperationResult<String> d_setex(final byte[] key, final Integer seconds, final byte[] value) {
        return connPool.executeWithFailover(new BaseKeyOperation<String>(key, OpName.SETEX) {
            @Override
            public String execute(Jedis client, ConnectionContext state) throws DynoException {
                 return client.setex(key, seconds, value);
            }
         });
    }
    
    @Override
    public String set(byte[] key, byte[] value, byte[] nxxx, byte[] expx, long time) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Boolean exists(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long persist(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public String type(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long expire(byte[] key, int seconds) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long pexpire(byte[] key, final long milliseconds) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long expireAt(byte[] key, long unixTime) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long pexpireAt(byte[] key, long millisecondsTimestamp) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long ttl(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Boolean setbit(byte[] key, long offset, boolean value) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Boolean setbit(byte[] key, long offset, byte[] value) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Boolean getbit(byte[] key, long offset) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long setrange(byte[] key, long offset, byte[] value) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public byte[] getrange(byte[] key, long startOffset, long endOffset) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public byte[] getSet(byte[] key, byte[] value) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long setnx(byte[] key, byte[] value) {
        throw new UnsupportedOperationException("not yet implemented");
    }


    @Override
    public Long decrBy(byte[] key, long integer) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long decr(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long incrBy(byte[] key, long integer) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Double incrByFloat(byte[] key, double value) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long incr(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long append(byte[] key, byte[] value) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public byte[] substr(byte[] key, int start, int end) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long hset(byte[] key, byte[] field, byte[] value) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public byte[] hget(byte[] key, byte[] field) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long hsetnx(byte[] key, byte[] field, byte[] value) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public String hmset(byte[] key, Map<byte[], byte[]> hash) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<byte[]> hmget(byte[] key, byte[]... fields) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long hincrBy(byte[] key, byte[] field, long value) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Double hincrByFloat(byte[] key, byte[] field, double value) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Boolean hexists(byte[] key, byte[] field) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long hdel(byte[] key, byte[]... field) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long hlen(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> hkeys(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Collection<byte[]> hvals(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Map<byte[], byte[]> hgetAll(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long rpush(byte[] key, byte[]... args) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long lpush(byte[] key, byte[]... args) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long llen(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<byte[]> lrange(byte[] key, long start, long end) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public String ltrim(byte[] key, long start, long end) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public byte[] lindex(byte[] key, long index) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public String lset(byte[] key, long index, byte[] value) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long lrem(byte[] key, long count, byte[] value) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public byte[] lpop(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public byte[] rpop(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long sadd(byte[] key, byte[]... member) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> smembers(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long srem(byte[] key, byte[]... member) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public byte[] spop(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> spop(byte[] key, long count) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long scard(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Boolean sismember(byte[] key, byte[] member) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public byte[] srandmember(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<byte[]> srandmember(final byte[] key, final int count){
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long strlen(byte[] key) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zadd(byte[] key, double score, byte[] member) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zadd(byte[] key, Map<byte[], Double> scoreMembers) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrange(byte[] key, long start, long end) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zrem(byte[] key, byte[]... member) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Double zincrby(byte[] key, double score, byte[] member) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zrank(byte[] key, byte[] member) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zrevrank(byte[] key, byte[] member) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrevrange(byte[] key, long start, long end)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<Tuple> zrangeWithScores(byte[] key, long start, long end)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<Tuple> zrevrangeWithScores(byte[] key, long start, long end)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zcard(byte[] key)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Double zscore(byte[] key, byte[] member)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<byte[]> sort(byte[] key)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<byte[]> sort(byte[] key, SortingParams sortingParameters)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zcount(byte[] key, double min, double max)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zcount(byte[] key, byte[] min, byte[] max)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrangeByScore(byte[] key, double min, double max)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrangeByScore(byte[] key, byte[] min, byte[] max)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrevrangeByScore(byte[] key, double max, double min)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrangeByScore(byte[] key, double min, double max, int offset, int count)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrevrangeByScore(byte[] key, byte[] max, byte[] min)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrangeByScore(byte[] key, byte[] min, byte[] max, int offset, int count)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrevrangeByScore(byte[] key, double max, double min, int offset, int count)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(byte[] key, double min, double max)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, double max, double min)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(byte[] key, double min, double max, int offset, int count)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrevrangeByScore(byte[] key, byte[] max, byte[] min, int offset, int count)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<Tuple> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max, int offset, int count)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, double max, double min, int offset, int count)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min, int offset, int count)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zremrangeByRank(byte[] key, long start, long end)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zremrangeByScore(byte[] key, double start, double end)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zremrangeByScore(byte[] key, byte[] start, byte[] end)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zlexcount(final byte[] key, final byte[] min, final byte[] max)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrangeByLex(final byte[] key, final byte[] min, final byte[] max)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrangeByLex(final byte[] key, final byte[] min, final byte[] max, int offset,
        int count)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrevrangeByLex(final byte[] key, final byte[] max, final byte[] min)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Set<byte[]> zrevrangeByLex(final byte[] key, final byte[] max, final byte[] min, int offset,
        int count)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long zremrangeByLex(final byte[] key, final byte[] min, final byte[] max)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long linsert(byte[] key, BinaryClient.LIST_POSITION where, byte[] pivot, byte[] value)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long lpushx(byte[] key, byte[]... arg)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long rpushx(byte[] key, byte[]... arg)  {
        throw new UnsupportedOperationException("not yet implemented");
    }
    
    @Override
    public List<byte[]> blpop(byte[] arg) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public List<byte[]> brpop(byte[] arg) {
        throw new UnsupportedOperationException("not yet implemented");
    }
    
    @Override
    public Long del(byte[] key)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public byte[] echo(byte[] arg)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long move(byte[] key, int dbIndex)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long bitcount(final byte[] key)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long bitcount(final byte[] key, long start, long end)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Long pfadd(final byte[] key, final byte[]... elements)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public long pfcount(final byte[] key)  {
        throw new UnsupportedOperationException("not yet implemented");
    }

    /**
     * NOT SUPPORTED ! Use {@link #dyno_scan(CursorBasedResult, String...)}
     * instead.
     *
     * @param cursor cursor
     * @return nothing -- throws UnsupportedOperationException when invoked
     * @see #dyno_scan(CursorBasedResult, String...)
     */
    @Override
    public ScanResult<String> scan(int cursor) {
        throw new UnsupportedOperationException("Not supported - use dyno_scan(String, CursorBasedResult");
    }

    /**
     * NOT SUPPORTED ! Use {@link #dyno_scan(CursorBasedResult, String...)}
     * instead.
     *
     * @param cursor cursor
     * @return nothing -- throws UnsupportedOperationException when invoked
     * @see #dyno_scan(CursorBasedResult, String...)
     */
    @Override
    public ScanResult<String> scan(String cursor) {
        throw new UnsupportedOperationException("Not supported - use dyno_scan(String, CursorBasedResult");
    }

    public CursorBasedResult<String> dyno_scan(String... pattern) {
        return this.dyno_scan(null, pattern);
    }

    public CursorBasedResult<String> dyno_scan(CursorBasedResult<String> cursor, String... pattern) {
        final Map<String, ScanResult<String>> results = new LinkedHashMap<>();

        List<OperationResult<ScanResult<String>>> opResults = scatterGatherScan(cursor, pattern);
        for (OperationResult<ScanResult<String>> opResult: opResults) {
            results.put(opResult.getNode().getHostAddress(), opResult.getResult());
        }

        return new CursorBasedResultImpl<>(results);

    }

    @Override
    public String pfmerge(String destkey, String... sourcekeys) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public long pfcount(String... keys) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void stopClient() {
        if (pipelineMonitor.get() != null) {
            pipelineMonitor.get().stop();
        }

        this.connPool.shutdown();
    }

    public DynoJedisPipeline pipelined() {
        throw new UnsupportedOperationException("pipelining not supported Yet!");
    }

    private DynoJedisPipelineMonitor checkAndInitPipelineMonitor() {

        if (pipelineMonitor.get() != null) {
            return pipelineMonitor.get();
        }

        int flushTimerFrequency = this.connPool.getConfiguration().getTimingCountersResetFrequencySeconds();
        DynoJedisPipelineMonitor plMonitor = new DynoJedisPipelineMonitor(appName, flushTimerFrequency);
        boolean success = pipelineMonitor.compareAndSet(null, plMonitor);
        if (success) {
            pipelineMonitor.get().init();
        }
        return pipelineMonitor.get();
    }

    public static class Builder {

        private String appName;
        private String clusterName;
        private int port = -1;
        private ConnectionPoolConfigurationImpl cpConfig;
        private HostSupplier hostSupplier;
        private DiscoveryClient discoveryClient;
        private String dualWriteClusterName;
        private HostSupplier dualWriteHostSupplier;
        private DynoDualWriterClient.Dial dualWriteDial;

        public Builder() {
        }

        public Builder withApplicationName(String applicationName) {
            appName = applicationName;
            return this;
        }

        public Builder withDynomiteClusterName(String cluster) {
            clusterName = cluster;
            return this;
        }

        public Builder withCPConfig(ConnectionPoolConfigurationImpl config) {
            cpConfig = config;
            return this;
        }

        public Builder withHostSupplier(HostSupplier hSupplier) {
            hostSupplier = hSupplier;
            return this;
        }

        public Builder withPort(int suppliedPort) {
            port = suppliedPort;
            return this;
        }

        public Builder withDiscoveryClient(DiscoveryClient client) {
            discoveryClient = client;
            return this;
        }

        public Builder withDualWriteClusterName(String dualWriteCluster) {
            dualWriteClusterName = dualWriteCluster;
            return this;
        }

        public Builder withDualWriteHostSupplier(HostSupplier dualWriteHostSupplier) {
            this.dualWriteHostSupplier = dualWriteHostSupplier;
            return this;
        }

        public Builder withDualWriteDial(DynoDualWriterClient.Dial dial) {
            this.dualWriteDial = dial;
            return this;
        }

        public DynoJedisClient build() {
            assert (appName != null);
            assert (clusterName != null);

            if (cpConfig == null) {
                cpConfig = new ArchaiusConnectionPoolConfiguration(appName);
                Logger.info("Dyno Client runtime properties: " + cpConfig.toString());
            }

            if (cpConfig.isDualWriteEnabled()) {
                throw new UnsupportedOperationException("Dual write not supported in the shaded version.");
            } else {
                return buildDynoJedisClient();
            }
        }


        private DynoJedisClient buildDynoJedisClient() {
            if (port != -1) {
                cpConfig.setPort(port);
            }

            if (hostSupplier == null) {
                if (discoveryClient == null) {
                    throw new DynoConnectException("HostSupplier not provided. Cannot initialize EurekaHostsSupplier " +
                            "which requires a DiscoveryClient");
                } else {
                    hostSupplier = new EurekaHostsSupplier(clusterName, discoveryClient);
                }
            }

            cpConfig.withHostSupplier(hostSupplier);

            setLoadBalancingStrategy(cpConfig);

            DynoCPMonitor cpMonitor = new DynoCPMonitor(appName);
            DynoOPMonitor opMonitor = new DynoOPMonitor(appName);

            JedisConnectionFactory connFactory = new JedisConnectionFactory(opMonitor);

            final ConnectionPoolImpl<Jedis> pool = startConnectionPool(appName, connFactory, cpConfig, cpMonitor);

            return new DynoJedisClient(appName, clusterName, pool, opMonitor);
        }

        private ConnectionPoolImpl<Jedis> startConnectionPool(String appName, JedisConnectionFactory connFactory,
                                                              ConnectionPoolConfigurationImpl cpConfig,
                                                              DynoCPMonitor cpMonitor) {

            final ConnectionPoolImpl<Jedis> pool = new ConnectionPoolImpl<Jedis>(connFactory, cpConfig, cpMonitor);

            try {
                Logger.info("Starting connection pool for app " + appName);

                pool.start().get();

                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    @Override
                    public void run() {
                        pool.shutdown();
                    }
                }));
            } catch (NoAvailableHostsException e) {
                if (cpConfig.getFailOnStartupIfNoHosts()) {
                    throw new RuntimeException(e);
                }

                Logger.warn("UNABLE TO START CONNECTION POOL -- IDLING");

                pool.idle();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return pool;
        }

        private void setLoadBalancingStrategy(ConnectionPoolConfigurationImpl config) {
            if (ConnectionPoolConfiguration.LoadBalancingStrategy.TokenAware == config.getLoadBalancingStrategy()) {
                if (config.getTokenSupplier() == null) {
                    Logger.warn("TOKEN AWARE selected and no token supplier found, using default HttpEndpointBasedTokenMapSupplier()");
                    config.withTokenSupplier(new HttpEndpointBasedTokenMapSupplier(port));
                }

                if (config.getLocalRack() == null && config.localZoneAffinity()) {
                    String warningMessage =
                            "DynoJedisClient for app=[" + config.getName() + "] is configured for local rack affinity "+
                                    "but cannot determine the local rack! DISABLING rack affinity for this instance. " +
                                    "To make the client aware of the local rack either use " +
                                    "ConnectionPoolConfigurationImpl.setLocalRack() when constructing the client " +
                                    "instance or ensure EC2_AVAILABILTY_ZONE is set as an environment variable, e.g. " +
                                    "run with -DEC2_AVAILABILITY_ZONE=us-east-1c";
                    config.setLocalZoneAffinity(false);
                    Logger.warn(warningMessage);
                }
            }
        }

    }


    /**
     * Used for unit testing ONLY
     */
    /*package*/ static class TestBuilder {
        private ConnectionPool cp;
        private String appName;

        public TestBuilder withAppname(String appName) {
            this.appName = appName;
            return this;
        }

        public TestBuilder withConnectionPool(ConnectionPool cp) {
            this.cp = cp;
            return this;
        }

        public DynoJedisClient build() {
            return new DynoJedisClient(appName, "TestCluster", cp, null);
        }

    }

	@Override
	public Long exists(String... arg0) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public ScanResult<String> scan(String arg0, ScanParams arg1) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Long geoadd(byte[] arg0, Map<byte[], GeoCoordinate> arg1) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Long geoadd(byte[] arg0, double arg1, double arg2, byte[] arg3) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Double geodist(byte[] arg0, byte[] arg1, byte[] arg2) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Double geodist(byte[] arg0, byte[] arg1, byte[] arg2, GeoUnit arg3) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public List<byte[]> geohash(byte[] arg0, byte[]... arg1) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public List<GeoCoordinate> geopos(byte[] arg0, byte[]... arg1) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public List<GeoRadiusResponse> georadius(byte[] arg0, double arg1,
			double arg2, double arg3, GeoUnit arg4) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public List<GeoRadiusResponse> georadius(byte[] arg0, double arg1,
			double arg2, double arg3, GeoUnit arg4, GeoRadiusParam arg5) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public List<GeoRadiusResponse> georadiusByMember(byte[] arg0, byte[] arg1,
			double arg2, GeoUnit arg3) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public List<GeoRadiusResponse> georadiusByMember(byte[] arg0, byte[] arg1,
			double arg2, GeoUnit arg3, GeoRadiusParam arg4) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public ScanResult<Entry<byte[], byte[]>> hscan(byte[] arg0, byte[] arg1) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public ScanResult<Entry<byte[], byte[]>> hscan(byte[] arg0, byte[] arg1,
			ScanParams arg2) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public String set(byte[] arg0, byte[] arg1, byte[] arg2) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public ScanResult<byte[]> sscan(byte[] arg0, byte[] arg1) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public ScanResult<byte[]> sscan(byte[] arg0, byte[] arg1, ScanParams arg2) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Long zadd(byte[] arg0, Map<byte[], Double> arg1, ZAddParams arg2) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Long zadd(byte[] arg0, double arg1, byte[] arg2, ZAddParams arg3) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Double zincrby(byte[] arg0, double arg1, byte[] arg2,
			ZIncrByParams arg3) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public ScanResult<Tuple> zscan(byte[] arg0, byte[] arg1) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public ScanResult<Tuple> zscan(byte[] arg0, byte[] arg1, ScanParams arg2) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Long bitpos(String arg0, boolean arg1) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Long bitpos(String arg0, boolean arg1, BitPosParams arg2) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Long geoadd(String arg0, Map<String, GeoCoordinate> arg1) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Long geoadd(String arg0, double arg1, double arg2, String arg3) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Double geodist(String arg0, String arg1, String arg2) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Double geodist(String arg0, String arg1, String arg2, GeoUnit arg3) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public List<String> geohash(String arg0, String... arg1) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public List<GeoCoordinate> geopos(String arg0, String... arg1) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public List<GeoRadiusResponse> georadius(String arg0, double arg1,
			double arg2, double arg3, GeoUnit arg4) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public List<GeoRadiusResponse> georadius(String arg0, double arg1,
			double arg2, double arg3, GeoUnit arg4, GeoRadiusParam arg5) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public List<GeoRadiusResponse> georadiusByMember(String arg0, String arg1,
			double arg2, GeoUnit arg3) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public List<GeoRadiusResponse> georadiusByMember(String arg0, String arg1,
			double arg2, GeoUnit arg3, GeoRadiusParam arg4) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public ScanResult<Entry<String, String>> hscan(String arg0, String arg1,
			ScanParams arg2) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public String psetex(String arg0, long arg1, String arg2) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public String set(String arg0, String arg1, String arg2) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Long zadd(String arg0, Map<String, Double> arg1, ZAddParams arg2) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public Double zincrby(String arg0, double arg1, String arg2,
			ZIncrByParams arg3) {
        throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public ScanResult<Tuple> zscan(String arg0, String arg1, ScanParams arg2) {
        throw new UnsupportedOperationException("not yet implemented");
	}

}
