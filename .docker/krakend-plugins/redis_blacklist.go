package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"

	"github.com/redis/go-redis/v9"
)

const pluginName = "redis-blacklist-checker"

var HandlerRegisterer = registerer(pluginName)

type registerer string

func (r registerer) RegisterHandlers(f func(name string, handler func(context.Context, map[string]interface{}, http.Handler) (http.Handler, error))) {
	f(string(r), r.registerHandlers)
}

func (r registerer) registerHandlers(ctx context.Context, extra map[string]interface{}, handler http.Handler) (http.Handler, error) {
	log.Printf("[%s] plugin loaded", pluginName)

	redisHost, ok := extra["redis_host"].(string)
	if !ok {
		return nil, errors.New("redis_host not found in config")
	}

	keyPrefix, ok := extra["key_prefix"].(string)
	if !ok {
		return nil, errors.New("key_prefix not found in config")
	}

	redisDB, ok := extra["redis_db"].(float64)
	if !ok {
		redisDB = 0
	}

	rdb := redis.NewClient(&redis.Options{
		Addr: redisHost,
		DB:   int(redisDB),
	})

	if err := rdb.Ping(ctx).Err(); err != nil {
		return nil, fmt.Errorf("failed to connect to Redis: %w", err)
	}
	log.Printf("[%s] successfully connected to Redis at %s", pluginName, redisHost)

	return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		claims, ok := req.Context().Value("JWTClaims").(map[string]interface{})
		if !ok || claims == nil {
			handler.ServeHTTP(w, req)
			return
		}

		jti, ok := claims["jti"].(string)
		if !ok || jti == "" {
			handler.ServeHTTP(w, req)
			return
		}

		redisKey := keyPrefix + jti
		exists, err := rdb.Exists(ctx, redisKey).Result()
		if err != nil {
			log.Printf("[%s] Redis error checking key %s: %v", pluginName, redisKey, err)
			// Fail-open: allow request to proceed on Redis error
			handler.ServeHTTP(w, req)
			return
		}

		if exists > 0 {
			log.Printf("[%s] Blacklisted token detected. JTI: %s", pluginName, jti)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusUnauthorized)
			err := json.NewEncoder(w).Encode(map[string]string{"error": "Token has been revoked"})
			if err != nil {
				return
			}
			return
		}

		handler.ServeHTTP(w, req)
	}), nil
}

func main() {}

func init() {
	log.Println("Loading redis-blacklist-checker plugin...")
}
