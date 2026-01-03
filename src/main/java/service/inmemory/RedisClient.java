package service.inmemory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

public class RedisClient {
    private static final Logger logger = LoggerFactory.getLogger(RedisClient.class);
    
    // Jedis 인스턴스가 아니라 Pool을 static으로 유지해야 합니다.
    private static JedisPool pool;

    // 생성자나 초기화 블록에서 Pool을 딱 한 번만 만듭니다.
    public RedisClient() {
        if (pool == null) {
            connect();
        }
    }

    public synchronized static void connect() {
        if (pool != null && !pool.isClosed()) {
            return;
        }
        // Pool 설정 (선택사항이지만 안정성을 위해 추천)
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(false);
        poolConfig.setTestWhileIdle(false);

        poolConfig.setNumTestsPerEvictionRun(3);
        
        poolConfig.setBlockWhenExhausted(true);

        pool = new JedisPool(poolConfig, "localhost", 6379, 2000); // 6379는 기본 포트
        logger.info("Redis Connection Pool Initialized.");
    }

    // 데이터를 쓸 때마다 Pool에서 '새로운' 연결을 빌려오고, try-with-resources로 자동 반납합니다.
    public static void set(String key, String value) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(key, value);
        } catch (Exception e) {
            logger.error("Redis Set Error", e);
        }
    }

    public static String get(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            logger.error("Redis Get Error", e);
            return null;
        }
    }

    public static boolean has(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key) != null;
        } catch (Exception e) {
            logger.error("Redis Has Error", e);
            return false;
        }
    }

    public static void del(String key) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
        } catch (Exception e) {
            logger.error("Redis Del Error", e);
        }
    }

    public static void setExpire(String key, int seconds) {
        try (Jedis jedis = pool.getResource()) {
            jedis.expire(key, seconds);
        } catch (Exception e) {
            logger.error("Redis Expire Error", e);
        }
    }
}