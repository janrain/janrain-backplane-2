package com.janrain.backplane2.server.dao.redis;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane2.server.Token;
import com.janrain.backplane2.server.dao.TokenDAO;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.redis.Redis;
import org.apache.commons.lang.SerializationUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Tom Raney
 */
public class RedisTokenDAO implements TokenDAO {

    public static byte[] getKey(String id) {
        return ("v2_token_" + id).getBytes();
    }

    @Override
    public Token get(String id) throws BackplaneServerException {
        byte[] bytes = Redis.getInstance().get(getKey(id));
        if (bytes != null) {
            return (Token) SerializationUtils.deserialize(bytes);
        } else {
            return null;
        }
    }

    @Override
    public List<Token> getAll() throws BackplaneServerException {
        List<Token> tokens = new ArrayList<Token>();
        List<byte[]> byteList = Redis.getInstance().lrange(getKey("list"), 0, -1);
        for (byte[] bytes : byteList) {
            if (bytes != null) {
                tokens.add((Token) SerializationUtils.deserialize(bytes));
            }
        }
        return tokens;
    }

    @Override
    public void persist(Token token) throws BackplaneServerException {
        Jedis jedis = null;
        try {
            jedis = Redis.getInstance().getWriteJedis();
            byte[] bytes = SerializationUtils.serialize(token);
            jedis.rpush(getKey("list"), bytes);
            jedis.set(getKey(token.getIdValue()), bytes);
            // set a TTL
            if (token.getExpirationDate() != null) {
                jedis.expireAt(getKey(token.getIdValue()), token.getExpirationDate().getTime() / 1000 +1);
            }
        } finally {
            Redis.getInstance().releaseToPool(jedis);
        }
    }

    @Override
    public void delete(String tokenId) throws BackplaneServerException {
        Jedis jedis = null;
        try {
            jedis = Redis.getInstance().getWriteJedis();
            byte[] bytes = jedis.get(getKey(tokenId));
            if (bytes != null) {
                logger.info("removing token " + tokenId);
                jedis.lrem(getKey("list"), 0, bytes);
                jedis.del(getKey(tokenId));
            }
        } finally {
            Redis.getInstance().releaseToPool(jedis);
        }
    }

    @Override
    public List<Token> retrieveTokensByGrant(String grantId) throws BackplaneServerException {
        List<Token> tokens = getAll();
        List<Token> filtered = new ArrayList<Token>();
        for (Token token: tokens) {
            if (token.getBackingGrants().contains(grantId)) {
                logger.info("found");
                filtered.add(token);
            }
        }
        return filtered;
    }

    @Override
    public void revokeTokenByGrant(String grantId) throws BackplaneServerException {
        List<Token> tokens = retrieveTokensByGrant(grantId);
        for (Token token : tokens) {
            delete(token.getIdValue());
            logger.info("revoked token " + token.getIdValue());
        }
        if (! tokens.isEmpty()) {
            logger.info("all tokens for grant " + grantId + " have been revoked");
        }
    }

    @Override
    public void deleteExpiredTokens() throws BackplaneServerException {
        // todo: add token cache?
        Jedis jedis = null;

        try {
            jedis = Redis.getInstance().getWriteJedis();
            logger.info("Backplane token cleanup task started.");

            List<Token> tokens = getAll();
            if (tokens != null) {
                for (Token token : tokens) {
                    if (Redis.getInstance().get(getKey(token.getIdValue())) == null) {
                        // remove from list
                        jedis.lrem(getKey("list"), 0, SerializationUtils.serialize(token));
                        logger.info("removed expired token " + token.getIdValue());
                    }
                }
            }
        } catch (Exception e) {
            // catch-all, else cleanup thread stops
            logger.error("Backplane token cleanup task error: " + e.getMessage(), e);
        } finally {
            logger.info("Backplane token cleanup task finished.");
            Redis.getInstance().releaseToPool(jedis);
        }
    }

    @Override
    public void cacheRevokedCleanup() throws SimpleDBException {
        // no-op
    }

    // PRIVATE

    private static final Logger logger = Logger.getLogger(RedisTokenDAO.class);

    private String getChannelBindingKey(@NotNull String channel) {
        // todo: key prefixes should be centralized to avoid conflicts
        return "v2_channel_bus_" + channel;
    }
}
