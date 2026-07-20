package com.genersoft.iot.vmp.conf.websocket;

import com.genersoft.iot.vmp.conf.webLog.LogChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter(){
        ServerEndpointExporter endpointExporter = new ServerEndpointExporter();

        endpointExporter.setAnnotatedEndpointClasses(LogChannel.class);

        return endpointExporter;
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void closeRevokedLogChannels() {
        LogChannel.closeUnauthorized();
    }
}
