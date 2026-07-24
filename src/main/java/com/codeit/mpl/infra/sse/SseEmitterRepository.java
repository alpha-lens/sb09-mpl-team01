package com.codeit.mpl.infra.sse;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class SseEmitterRepository {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, Object> eventCache = new ConcurrentHashMap<>();

    /**
     * Stores an SSE emitter under the given emitter ID.
     *
     * @param emitterId the identifier to associate with the emitter
     * @param sseEmitter the SSE emitter to store
     * @return the same SseEmitter instance
     */
    public SseEmitter save(String emitterId, SseEmitter sseEmitter) {
        emitters.put(emitterId, sseEmitter);
        return sseEmitter;
    }

    /**
     * Stores an event in the cache under the specified event cache ID.
     *
     * @param eventCacheId the ID under which to cache the event
     * @param event        the event to store
     */
    public void saveEventCache(String eventCacheId, Object event) {
        eventCache.put(eventCacheId, event);
    }

    /**
     * Retrieves all emitters whose identifiers start with the provided member ID.
     *
     * @param memberId the prefix string to match against emitter identifiers
     * @return a map of emitters whose identifiers start with {@code memberId}
     */
    public Map<String, SseEmitter> findAllEmitterStartWithByMemberId(String memberId) {
        return emitters.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(memberId))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Retrieves all cached events whose keys start with the specified member ID.
     *
     * @param memberId the prefix to match against cached event keys
     * @return a new map of cached events with keys starting with the specified member ID
     */
    public Map<String, Object> findAllEventCacheStartWithByMemberId(String memberId) {
        return eventCache.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(memberId))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Deletes the emitter associated with the given ID.
     *
     * @param id the emitter ID
     */
    public void deleteById(String id) {
        emitters.remove(id);
    }

    /**
     * Removes all emitters whose identifiers start with the specified member ID.
     *
     * @param memberId the prefix to match against emitter identifiers
     */
    public void deleteAllEmitterStartWithByMemberId(String memberId) {
    emitters.keySet().stream()
                .filter(key -> key.startsWith(memberId))
                .forEach(emitters::remove);
    }

    /**
     * Removes all cached events whose keys start with the specified member ID.
     *
     * @param memberId the prefix that keys must match to be deleted
     */
    public void deleteAllEventCacheStartWithByMemberId(String memberId) {
        eventCache.keySet().stream()
                .filter(key -> key.startsWith(memberId))
                .forEach(eventCache::remove);
    }

    /**
     * Retrieves all active emitters.
     *
     * @return a map of all currently active SseEmitters
     */
    public Map<String, SseEmitter> getEmitters() {
        return emitters;
    }
}
