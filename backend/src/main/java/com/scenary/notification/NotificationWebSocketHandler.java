package com.scenary.notification;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenary.auth.AuthService;
import com.scenary.auth.JwtUtil;
import com.scenary.user.UserService;

import io.jsonwebtoken.Claims;

/** 原生 WebSocket 通知通道；令牌只允许出现在首帧认证消息中。 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final String USER_ID_ATTRIBUTE = NotificationWebSocketHandler.class.getName() + ".userId";
    private static final int MAX_FRAME_CHARS = 4096;

    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;
    private final AuthService authService;
    private final UserService userService;
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(ObjectMapper objectMapper, JwtUtil jwtUtil,
                                        AuthService authService, UserService userService) {
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
        this.authService = authService;
        this.userService = userService;
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session,
                                     @NonNull TextMessage message) throws IOException {
        String payload = message.getPayload();
        if (payload == null || payload.length() > MAX_FRAME_CHARS) {
            close(session);
            return;
        }
        try {
            JsonNode body = objectMapper.readTree(payload);
            if (body == null || !"AUTH".equals(body.path("type").asText())
                    || !body.path("accessToken").isTextual()) {
                close(session);
                return;
            }
            Claims claims = jwtUtil.parse(body.path("accessToken").asText());
            if (!JwtUtil.TYPE_ACCESS.equals(claims.get("type", String.class))
                    || authService.isAccessBlacklisted(claims)) {
                close(session);
                return;
            }
            long userId = Long.parseLong(claims.getSubject());
            userService.ensureActive(userId);
            bind(session, userId);
            send(session, Map.of("type", "READY"));
        } catch (RuntimeException e) {
            close(session);
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session,
                                       @NonNull CloseStatus status) {
        unbind(session);
    }

    public void push(long recipientId, long unreadCount) {
        Set<WebSocketSession> targets = sessions.get(recipientId);
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (WebSocketSession session : Set.copyOf(targets)) {
            try {
                send(session, Map.of("type", "NOTIFICATION", "unreadCount", unreadCount));
            } catch (IOException e) {
                unbind(session);
                close(session);
            }
        }
    }

    private void bind(WebSocketSession session, long userId) {
        unbind(session);
        session.getAttributes().put(USER_ID_ATTRIBUTE, userId);
        sessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    private void unbind(WebSocketSession session) {
        Object value = session.getAttributes().remove(USER_ID_ATTRIBUTE);
        if (!(value instanceof Long userId)) {
            return;
        }
        sessions.computeIfPresent(userId, (ignored, current) -> {
            current.remove(session);
            return current.isEmpty() ? null : current;
        });
    }

    private void send(WebSocketSession session, Map<String, ?> body) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        String payload = objectMapper.writeValueAsString(body);
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        }
    }

    private void close(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (IOException ignored) {
            // 认证失败或连接已断开，不再向客户端泄露内部异常。
        }
    }
}
