package com.genersoft.iot.vmp.conf.webLog;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.conf.websocket.AuthenticatedWebSocketConfigurator;
import lombok.extern.slf4j.Slf4j;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ServerEndpoint(
        value = "/channel/log",
        subprotocols = AuthenticatedWebSocketConfigurator.APPLICATION_PROTOCOL,
        configurator = AuthenticatedWebSocketConfigurator.class)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class LogChannel {

    public static final ConcurrentMap<String, LogChannel> CHANNELS = new ConcurrentHashMap<>();

    private final TokenVersionService tokenVersionService;
    private Session session;
    private AuthPrincipal principal;

    public LogChannel(TokenVersionService tokenVersionService) {
        this.tokenVersionService = tokenVersionService;
    }

    @OnMessage(maxMessageSize = 1) // MaxMessage 1 byte
    public void onMessage(String message) {

        try {
            this.session.close(new CloseReason(CloseReason.CloseCodes.TOO_BIG, "此节点不接收任何客户端信息"));
        } catch (IOException e) {
            log.error("[Web-Log] 连接关闭失败: id={}, err={}", this.session.getId(), e.getMessage());
        }
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        this.session = session;
        Object storedPrincipal = endpointConfig.getUserProperties().get(
                AuthenticatedWebSocketConfigurator.PRINCIPAL_PROPERTY);
        if (!(storedPrincipal instanceof AuthPrincipal authPrincipal)) {
            closePolicyViolation("握手身份缺失");
            return;
        }
        this.principal = authPrincipal;
        if (!authorizationCurrent()) {
            closePolicyViolation("授权已失效");
            return;
        }
        this.session.setMaxIdleTimeout(0);
        CHANNELS.put(this.session.getId(), this);

        log.info("[Web-Log] 连接已建立: id={}, userId={}",
                this.session.getId(), principal.userId());
    }

    @OnClose
    public void onClose(CloseReason closeReason) {
        String sessionId = session == null ? "unknown" : session.getId();
        log.info("[Web-Log] 连接已断开: id={}, err={}", sessionId, closeReason);
        CHANNELS.remove(sessionId);
    }

    @OnError
    public void onError(Throwable throwable) throws IOException {
        String sessionId = session == null ? "unknown" : session.getId();
        log.info("[Web-Log] 连接错误: id={}, err={}", sessionId, throwable.getMessage());
        if (this.session != null && this.session.isOpen()) {
            this.session.close(new CloseReason(
                    CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                    "日志通道异常"));
        }
    }

    /**
     * Push messages to all clients
     *
     * @param message
     */
    public static void push(String message) {
        CHANNELS.values().forEach(endpoint -> endpoint.pushIfAuthorized(message));
    }

    public static void closeUnauthorized() {
        CHANNELS.values().forEach(endpoint -> {
            if (!endpoint.authorizationCurrent()) {
                endpoint.closePolicyViolation("授权已变更");
            }
        });
    }

    private void pushIfAuthorized(String message) {
        if (session == null || !session.isOpen()) {
            removeSession();
            return;
        }
        if (!authorizationCurrent()) {
            closePolicyViolation("授权已变更");
            return;
        }
        session.getAsyncRemote().sendText(message);
    }

    private boolean authorizationCurrent() {
        return principal != null
                && principal.type() == com.genersoft.iot.vmp.auth.model.PrincipalType.USER
                && principal.userId() != null
                && (principal.systemUser()
                || principal.permissions().contains(
                com.genersoft.iot.vmp.conf.websocket.LogWebSocketTicketService.LOG_VIEW_PERMISSION))
                && tokenVersionService.matches(
                principal.userId(), principal.tokenVersion());
    }

    private void closePolicyViolation(String reason) {
        removeSession();
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(new CloseReason(
                    CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (IOException exception) {
            log.warn("[Web-Log] 拒绝连接时关闭失败: id={}, err={}",
                    session.getId(), exception.getMessage());
        }
    }

    private void removeSession() {
        if (session != null) {
            CHANNELS.remove(session.getId(), this);
        }
    }
}
