package service.inmemory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;

public class RedisClient {
    private static Jedis client;

    public RedisClient(){}

    public void connect() {
        try (JedisPool pool = new JedisPool("localhost", Protocol.DEFAULT_PORT)) {
            client = pool.getResource();
        }
    }

    public static void set(String key, String value) {
        client.set(key, value);
    }

    public static String get(String key) {
        return client.get(key);
    }

    public static boolean has(String key) {
        return client.get(key) != null;
    }

    public static void del(String key) {
        client.del(key);
    }

    public static void setExpire(String key, int seconds) {
        client.expire(key, seconds);
    }
}
