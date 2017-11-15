package com.nepxion.aquarius.limit.redis.impl;

/**
 * <p>Title: Nepxion Aquarius</p>
 * <p>Description: Nepxion Aquarius</p>
 * <p>Copyright: Copyright (c) 2017</p>
 * <p>Company: Nepxion</p>
 * @author Haojun Ren
 * @email 1394997@qq.com
 * @version 1.0
 */

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.nepxion.aquarius.limit.redis.RedisLimit;

@Component("redisLimitImpl")
public class RedisLimitImpl implements RedisLimit {
    @Autowired
    @Qualifier("aquariusRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    // 针对资源Key，每seconds秒最多访问maxCount次，超过maxCount次返回false
    @Override
    public boolean tryAccess(String key, int seconds, int limitCount, int lockCount, int lockTime, boolean limitLockEnabled) {
        List<String> keys = new ArrayList<String>();
        keys.add(key);

        String luaScript = buildLuaScript(limitLockEnabled);

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<Long>(luaScript, Long.class);
        Long count = redisTemplate.execute(redisScript, keys, Math.max(limitCount, lockCount), seconds, lockCount, lockTime);

        return count <= limitCount;
    }

    private String buildLuaScript(boolean limitLockEnabled) {
        StringBuilder lua = new StringBuilder();
        lua.append("local c");
        lua.append("\nc = redis.call('get',KEYS[1])");
        lua.append("\nif c and tonumber(c) > tonumber(ARGV[1]) then");
        lua.append("\nreturn c;");
        lua.append("\nend");
        lua.append("\nc = redis.call('incr',KEYS[1])");
        lua.append("\nif tonumber(c) == 1 then");
        lua.append("\nredis.call('expire',KEYS[1],ARGV[2])");
        lua.append("\nend");
        if (limitLockEnabled) {
            lua.append("\nif tonumber(c) > tonumber(ARGV[3]) then");
            lua.append("\nredis.call('expire',KEYS[1],ARGV[4])");
            lua.append("\nend");
        }
        lua.append("\nreturn c;");

        return lua.toString();
    }
}