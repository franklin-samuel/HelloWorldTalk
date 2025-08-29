package com.example.talkVideoAPI.handler;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.example.talkVideoAPI.model.SupportedLanguage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class SocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(SocketHandler.class);
    private final SocketIOServer server;
    private final RedisTemplate<String, Object> redis;

    private static final String MATCH_QUEUE = "match_queue";

    public SocketHandler(SocketIOServer server, RedisTemplate<String, Object> redisTemplate) {
        this.server = server;
        this.redis = redisTemplate;
        server.addListeners(this);
        server.start();
        System.out.println("[Socket] Netty Socket.IO iniciado na porta " + server.getConfiguration().getPort());
    }

    @OnConnect
    public void onConnect(SocketIOClient client) {
        logger.info("Cliente conectado: " + client.getSessionId());
    }

    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        String clientId = client.getSessionId().toString();
        redis.opsForList().remove(MATCH_QUEUE, 0, clientId);
        redis.delete("session:" + clientId);

        String room = (String) redis.opsForValue().get("user_room:" + clientId);
        if (room != null) {
            redis.delete("user_room:" + clientId);
            String partnerId = (String) redis.opsForValue().get("room_partner:" + room);
            if (partnerId != null && !partnerId.equals(clientId)) {
                SocketIOClient partner = server.getClient(UUID.fromString(partnerId));
                if (partner != null) partner.sendEvent("userDisconnected", clientId);
            }
            redis.delete("room_partnet:" + room);
        }
    }

    @OnEvent("joinRoom")
    public void onJoinRoom(SocketIOClient client, Map<String, String> payload) {
        String clientId = client.getSessionId().toString();
        String nativeLang = payload.get("nativeLanguage");
        String targetLang = payload.get("targetLanguage");
        String sessionKey = "session:" + clientId;

        if (!SupportedLanguage.isValid(nativeLang) || !SupportedLanguage.isValid(targetLang)) {
            client.sendEvent("error", "Idioma inválido.");
            return;
        }

        redis.opsForHash().put(sessionKey, "native", nativeLang);
        redis.opsForHash().put(sessionKey, "target", targetLang);

        logger.info("[MATCH] Cliente {} entrou. Tentando encontrar parceiro...", clientId);
        String partnerClientId = findPartner(clientId, nativeLang, targetLang);
        logger.info("[MATCH] Parceiro encontrado: {}", partnerClientId);

        String room = null;
        if (partnerClientId == null) {
            redis.opsForList().rightPush(MATCH_QUEUE, clientId);
            client.sendEvent("waiting");
        } else {
            room = UUID.randomUUID().toString();
            client.joinRoom(room);
            SocketIOClient partner = server.getClient(UUID.fromString(partnerClientId));
            if (partner != null) partner.joinRoom(room);
            client.sendEvent("joined", room);

            redis.opsForValue().set("user_room:" + clientId, room);
            redis.opsForValue().set("user_room:" + partnerClientId, room);
            redis.opsForValue().set("room_partner:" + room, partnerClientId);

            redis.opsForList().remove(MATCH_QUEUE, 0, partnerClientId);
            client.sendEvent("joined", room);
            if (partner != null) partner.sendEvent("joined", room);

        }
        printLog("onJoinRoom", client, room);
    }

    @OnEvent("ready")
    public void onReady(SocketIOClient client, String room, AckRequest ackRequest) {
        client.getNamespace().getRoomOperations(room).sendEvent("ready", room);
        printLog("onReady", client, room);
    }

    @OnEvent("candidate")
    public void onCandidate(SocketIOClient client, Map<String, Object> payload) {
        String room = (String) payload.get("room");
        client.getNamespace().getRoomOperations(room).sendEvent("candidate", payload);
        printLog("onCandidate", client, room);
    }

    @OnEvent("offer")
    public void onOffer(SocketIOClient client, Map<String, Object> payload) {
        String room = (String) payload.get("room");
        Object sdp = payload.get("sdp");
        client.getNamespace().getRoomOperations(room).sendEvent("offer", sdp);
        printLog("onOffer", client, room);
    }

    @OnEvent("answer")
    public void onAnswer(SocketIOClient client, Map<String, Object> payload) {
        String room = (String) payload.get("room");
        Object sdp = payload.get("sdp");
        client.getNamespace().getRoomOperations(room).sendEvent("answer", sdp);
        printLog("onAnswer", client, room);
    }

    @OnEvent("leaveRoom")
    public void onLeaveRoom(SocketIOClient client, String room) {
        String clientId = client.getSessionId().toString();
        client.leaveRoom(room);
        redis.delete("user_room:" + clientId);
        redis.delete("session:" + clientId);

        String partnerId = (String) redis.opsForValue().get("room_partner:" + room);
        if (partnerId != null && !partnerId.equals(clientId)) {
            SocketIOClient partner = server.getClient(UUID.fromString(partnerId));
            if (partner != null) partner.sendEvent("userDisconnected", clientId);
        }
        redis.delete("room_partner:" + room);
        printLog("onLeaveRoom", client, room);
    }

    private String findPartner(String clientId, String myNative, String myTarget) {
        Long size = redis.opsForList().size(MATCH_QUEUE);
        if (size == null || size == 0) return null;

        for (int i = 0; i < size; i++) {
            String otherId = (String) redis.opsForList().index(MATCH_QUEUE, i);
            if (otherId == null || otherId.equals(clientId)) continue;

            Map<Object, Object> otherSession = redis.opsForHash().entries("session:" + otherId);
            if (otherSession == null) continue;

            String otherNative = (String) otherSession.get("native");
            String otherTarget = (String) otherSession.get("target");

            if (myTarget.equals(otherNative) && otherTarget.equals(myNative)) {
                return otherId;
            }
        }
        return null;
    }

    private static void printLog(String header, SocketIOClient client, String room) {
        if (room == null) return;
        int size = 0;
        try {
            size = client.getNamespace().getRoomOperations(room).getClients().size();
        } catch (Exception e) {
            logger.error("error ", e);
        }
        logger.info("#ConncetedClients - {} => room: {}, count: {}", header, room, size);
    }

}
