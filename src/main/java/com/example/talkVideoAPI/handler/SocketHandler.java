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
        logger.info("[Socket] Netty Socket.IO iniciado na porta " + server.getConfiguration().getPort());
    }

    @OnConnect
    public void onConnect(SocketIOClient client) {
        logger.info("[Connect] Cliente conectado: {}", client.getSessionId());
    }

    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        String clientId = client.getSessionId().toString();
        logger.info("[Disconnect] Cliente desconectado: {}", clientId);

        redis.opsForList().remove(MATCH_QUEUE, 0, clientId);
        redis.delete("session:" + clientId);

        String room = (String) redis.opsForValue().get("user_room:" + clientId);
        if (room != null) {
            String partnerId = (String) redis.opsForValue().get("room_partner:" + clientId);

            // Limpa mapeamentos do cliente
            redis.delete("user_room:" + clientId);
            redis.delete("room_partner:" + clientId);

            if (partnerId != null && !partnerId.equals(clientId)) {
                SocketIOClient partner = server.getClient(UUID.fromString(partnerId));

                redis.delete("user_room:" + partnerId);
                redis.delete("room_partner:" + partnerId);

                if (partner != null) {
                    partner.leaveRoom(room);
                    partner.sendEvent("userDisconnected", clientId);
                    logger.info("[Disconnect] Notificando parceiro {} sobre desconexão de {}", partnerId, clientId);
                }

                requeueClient(partnerId);
            }

            logger.info("[Disconnect] Sala {} limpa após desconexão de {}", room, clientId);
        }
    }

    @OnEvent("joinQueue")
    public void onJoinQueue(SocketIOClient client, Map<String, String> payload) {
        String clientId = client.getSessionId().toString();
        String nativeLang = payload.get("nativeLanguage");
        String targetLang = payload.get("targetLanguage");
        String sessionKey = "session:" + clientId;

        if (!SupportedLanguage.isValid(nativeLang) || !SupportedLanguage.isValid(targetLang)) {
            client.sendEvent("error", "Idioma inválido.");
            logger.warn("[joinQueue] Cliente {} enviou idiomas inválidos.", clientId);
            return;
        }

        redis.opsForHash().put(sessionKey, "native", nativeLang);
        redis.opsForHash().put(sessionKey, "target", targetLang);

        logger.info("[MATCH] Cliente {} entrou. Tentando encontrar parceiro...", clientId);
        String partnerClientId = findPartner(clientId, nativeLang, targetLang);
        logger.info("[MATCH] Parceiro encontrado: {}", partnerClientId);

        if (partnerClientId == null) {
            redis.opsForList().rightPush(MATCH_QUEUE, clientId);
            client.sendEvent("waiting");
            logger.info("[MATCH] Cliente {} colocado na fila de espera.", clientId);
        } else {
            createRoomAndNotify(clientId, partnerClientId);
        }
    }

    @OnEvent("ready")
    public void onReady(SocketIOClient client, String room, AckRequest ackRequest) {
        client.getNamespace().getRoomOperations(room).sendEvent("ready", room);
        logger.info("[ready] Cliente {} pronto na sala {}", client.getSessionId(), room);
        printLog("onReady", client, room);
    }

    @OnEvent("candidate")
    public void onCandidate(SocketIOClient client, Map<String, Object> payload) {
        String room = (String) payload.get("room");
        client.getNamespace().getRoomOperations(room).sendEvent("candidate", payload);
        logger.info("[candidate] Cliente {} enviou candidate na sala {}", client.getSessionId(), room);
        printLog("onCandidate", client, room);
    }

    @OnEvent("offer")
    public void onOffer(SocketIOClient client, Map<String, Object> payload) {
        String room = (String) payload.get("room");
        Object sdp = payload.get("sdp");
        client.getNamespace().getRoomOperations(room).sendEvent("offer", sdp);
        logger.info("[offer] Cliente {} enviou offer na sala {}", client.getSessionId(), room);
        printLog("onOffer", client, room);
    }

    @OnEvent("answer")
    public void onAnswer(SocketIOClient client, Map<String, Object> payload) {
        String room = (String) payload.get("room");
        Object sdp = payload.get("sdp");
        client.getNamespace().getRoomOperations(room).sendEvent("answer", sdp);
        logger.info("[answer] Cliente {} enviou answer na sala {}", client.getSessionId(), room);
        printLog("onAnswer", client, room);
    }

    @OnEvent("stop")
    public void onStop(SocketIOClient client) {
        String clientId = client.getSessionId().toString();
        String room = (String) redis.opsForValue().get("user_room:" + clientId);
        cleanupRoom(clientId, room, false);
        client.sendEvent("stopped");
    }

    @OnEvent("nextPartner")
    public void onNext(SocketIOClient client) {
        String clientId = client.getSessionId().toString();
        String room = (String) redis.opsForValue().get("user_room:" + clientId);
        cleanupRoom(clientId, room, true);
    }

    private void cleanupRoom(String clientId, String room, boolean requeue) {
        if (room == null) return;

        String partnerId = (String) redis.opsForValue().get("room_partner:" + clientId);

        redis.delete("user_room:" + clientId);
        redis.delete("room_partner:" + clientId);

        SocketIOClient client = server.getClient(UUID.fromString(clientId));
        if (client != null) {
            client.leaveRoom(room);
        }

        if (partnerId != null && !partnerId.equals(clientId)) {
            redis.delete("user_room:" + partnerId);
            redis.delete("room_partner:" + partnerId);

            SocketIOClient partner = server.getClient(UUID.fromString(partnerId));
            if (partner != null) {
                partner.leaveRoom(room);
                if (requeue) {
                    partner.sendEvent("partnerNext");
                } else {
                    partner.sendEvent("userDisconnected");
                }
            }

            requeueClient(partnerId);
        }

        if (requeue) requeueClient(clientId);

        logger.info("[cleanupRoom] Sala {} limpa. clientId={}, partnerId={}, requeueCaller={}", room, clientId, partnerId, requeue);
    }

    private void requeueClient(String clientId) {
        Map<Object, Object> session = redis.opsForHash().entries("session:" + clientId);
        if (!session.isEmpty()) {
            String nativeLang = (String) session.get("native");
            String targetLang = (String) session.get("target");

            String partnerId = findPartner(clientId, nativeLang, targetLang);
            if (partnerId == null) {
                redis.opsForList().rightPush(MATCH_QUEUE, clientId);
                SocketIOClient c = server.getClient(UUID.fromString(clientId));
                if (c != null) c.sendEvent("waiting");
                logger.info("[requeueClient] {} re-enfileirado e aguardando.", clientId);
            } else {
                createRoomAndNotify(clientId, partnerId);
            }
        } else {
            logger.info("[requeueClient] Sessão ausente para {}. Não re-enfileirando.", clientId);
        }
    }

    private String findPartner(String clientId, String myNative, String myTarget) {
        Long size = redis.opsForList().size(MATCH_QUEUE);
        if (size == null || size == 0) return null;

        for (int i = 0; i < size; i++) {
            String otherId = (String) redis.opsForList().index(MATCH_QUEUE, i);
            if (otherId == null) continue;

            Map<Object, Object> otherSession = redis.opsForHash().entries("session:" + otherId);
            if (otherSession.isEmpty()) {
                logger.info("[findPartner] Cliente {} não tem sessão ativa.", otherId);
                continue;
            } else {
                logger.info("[findPartner] Cliente {} disponível para match.", otherId);
            }

            String otherNative = (String) otherSession.get("native");
            String otherTarget = (String) otherSession.get("target");

            if (myTarget.equalsIgnoreCase(otherNative) && otherTarget.equalsIgnoreCase(myNative)) {
                logger.info("[findPartner] Match encontrado entre {} e {}", clientId, otherId);
                return otherId;
            }
        }
        logger.info("[findPartner] Nenhum parceiro encontrado para {}", clientId);
        return null;
    }

    private void createRoomAndNotify(String clientId, String partnerId) {
        String roomId = UUID.randomUUID().toString();

        SocketIOClient client = server.getClient(UUID.fromString(clientId));
        SocketIOClient partner = server.getClient(UUID.fromString(partnerId));
        if (client == null || partner == null) return;

        client.joinRoom(roomId);
        partner.joinRoom(roomId);

        redis.opsForValue().set("user_room:" + clientId, roomId);
        redis.opsForValue().set("user_room:" + partnerId, roomId);
        redis.opsForValue().set("room_partner:" + clientId, partnerId);
        redis.opsForValue().set("room_partner:" + partnerId, clientId);

        redis.opsForList().remove(MATCH_QUEUE, 0, clientId);
        redis.opsForList().remove(MATCH_QUEUE, 0, partnerId);

        client.sendEvent("match_found", Map.of("room", roomId, "role", "caller"));
        partner.sendEvent("match_found", Map.of("room", roomId, "role", "callee"));

        logger.info("[MATCH] Sala {} criada entre {} (caller) e {} (callee)", roomId, clientId, partnerId);
    }

    private static void printLog(String header, SocketIOClient client, String room) {
        if (room == null) return;
        int size = 0;
        try {
            size = client.getNamespace().getRoomOperations(room).getClients().size();
        } catch (Exception e) {
            logger.error("Erro ao obter clientes da sala", e);
        }
        logger.info("#ConnectedClients - {} => room: {}, count: {}", header, room, size);
    }
}
