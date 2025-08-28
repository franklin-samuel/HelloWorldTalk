package com.example.talkVideoAPI.handler;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
@Slf4j
public class SocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(SocketHandler.class);

    private final SocketIOServer server;

    private static final Map<String, String> users = new HashMap<>();
    private static final Map<String, String> rooms = new HashMap<>();

    private static final Map<String, String> nativeLanguage = new HashMap<>();
    private static final Map<String, String> targetLanguage = new HashMap<>();

    public SocketHandler(SocketIOServer server) {
        this.server = server;
        server.addListeners(this);
        server.start();
    }

    @OnConnect
    public void onConnect(SocketIOClient client) {
        logger.info("Cliente conectado: " + client.getSessionId());
        String clientId = client.getSessionId().toString();
        users.put(clientId, null);
    }

    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        String clientId = client.getSessionId().toString();
        String room = users.get(clientId);
        if (!Objects.isNull(room)) {
            logger.info("Cliente desconectado: %s from : %s", clientId, room);
            users.remove(clientId);
            nativeLanguage.remove(clientId);
            targetLanguage.remove(clientId);
            client.getNamespace().getRoomOperations(room).sendEvent("userDisconnected", clientId);
        }
        printLog("onDisconnect", client, room);
    }

    @OnEvent("joinRoom")
    public void onJoinRoom(SocketIOClient client, Map<String, String> payload) {
        String room = payload.get("room");
        String nativeLang = payload.get("nativeLanguage");
        String targetLang = payload.get("targetLanguage");
        String clientId = client.getSessionId().toString();

        nativeLanguage.put(clientId, nativeLang);
        targetLanguage.put(clientId, targetLang);

        String partnerClientId = findPartner(clientId);

        if (partnerClientId == null) {
            client.sendEvent("waiting", room);
            users.put(clientId, null);
        } else {
            client.joinRoom(room);
            SocketIOClient partner = server.getClient(UUID.fromString(partnerClientId));
            if (partner != null) partner.joinRoom(room);
            client.sendEvent("joined", room);

            if(partner != null) partner.sendEvent("joined", room);

            users.put(clientId, room);
            users.put(partnerClientId, room);
            rooms.put(room, partnerClientId);
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
        client.leaveRoom(room);
        users.put(client.getSessionId().toString(), null);
        nativeLanguage.remove(client.getSessionId().toString());
        targetLanguage.remove(client.getSessionId().toString());
        printLog("onLeaveRoom", client, room);
    }

    private String findPartner(String clientId) {
        String myNative = nativeLanguage.get(clientId);
        String myTarget = targetLanguage.get(clientId);

        for (String otherId : users.keySet()) {
            if (otherId.equals(clientId)) continue;

            String otherRoom = users.get(otherId);
            String otherNative = nativeLanguage.get(otherId);
            String otherTarget = targetLanguage.get(otherId);

            if (otherRoom == null &&
                    myTarget.equals(otherNative) &&
                    otherTarget.equals(myNative)
            ) {
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
