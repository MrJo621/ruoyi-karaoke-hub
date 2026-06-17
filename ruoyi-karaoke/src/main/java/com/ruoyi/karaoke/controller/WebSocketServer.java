package com.ruoyi.karaoke.controller;

import cn.hutool.json.JSONUtil;
import com.ruoyi.karaoke.model.WebSocketConsole;
import com.ruoyi.karaoke.service.IKaraokeSongsDetailService;
import com.ruoyi.karaoke.service.strategy.SongStrategyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.ruoyi.karaoke.enums.ConsoleType.HEARTBEAT;

@ServerEndpoint("/ws/{sid}")
@Component
public class WebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketServer.class);

    private static int onlineCount = 0;

    public static CopyOnWriteArraySet<WebSocketServer> webSocketSet = new CopyOnWriteArraySet<>();

    private Session session;

    private String sid = "";

    private static IKaraokeSongsDetailService songsDetailService;
    private static SongStrategyContext songStrategyContext;

    @Autowired
    public void setSongsDetailService(IKaraokeSongsDetailService songsDetailService) {
        WebSocketServer.songsDetailService = songsDetailService;
    }

    @Autowired
    public void setSongStrategyContext(SongStrategyContext songStrategyContext) {
        WebSocketServer.songStrategyContext = songStrategyContext;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        this.session = session;
        webSocketSet.add(this);
        addOnlineCount();
        log.info("有新窗口开始监听:" + sid + ", 当前在线人数为" + getOnlineCount());
        this.sid = sid;
        try {
            sendMessage(JSONUtil.toJsonPrettyStr(new WebSocketConsole(HEARTBEAT.getCode(), String.valueOf(getOnlineCount()), "连接成功！")));
        } catch (IOException e) {
            log.error("websocket IO异常");
        }
    }

    @OnClose
    public void onClose() {
        webSocketSet.remove(this);
        subOnlineCount();
        log.info("有一连接关闭！当前在线人数为" + getOnlineCount());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("收到来自窗口" + sid + "的信息:" + message);
        WebSocketConsole bean = JSONUtil.toBean(message, WebSocketConsole.class);
        WebSocketConsole result = songStrategyContext.deal(bean);

        for (WebSocketServer item : webSocketSet) {
            try {
                if (item.sid.equals(this.sid)) {
                    item.sendMessage(JSONUtil.toJsonPrettyStr(result));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("发生错误");
        error.printStackTrace();
    }

    public void sendMessage(String message) throws IOException {
        log.info("服务器消息推送：" + message);
        this.session.getBasicRemote().sendText(message);
    }

    public static void sendInfo(String message, @PathParam("sid") String sid) throws IOException {
        log.info("推送消息到窗口" + sid + "，推送内容:" + message);
        for (WebSocketServer item : webSocketSet) {
            try {
                if (sid == null) {
                    item.sendMessage(message);
                } else if (item.sid.equals(sid)) {
                    item.sendMessage(message);
                }
            } catch (IOException e) {
                continue;
            }
        }
    }

    public static synchronized int getOnlineCount() {
        return onlineCount;
    }

    public static synchronized void addOnlineCount() {
        WebSocketServer.onlineCount++;
    }

    public static synchronized void subOnlineCount() {
        WebSocketServer.onlineCount--;
    }

    public static CopyOnWriteArraySet<WebSocketServer> getWebSocketSet() {
        return webSocketSet;
    }

}
