package com.monitoring.logforwarder.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class GrpcConfiguration {

    @Value("${grpc.server.port:50051}")
    private int grpcPort;

    private final ForwarderGrpcService forwarderGrpcService;

    @Bean
    public Server grpcServer() throws IOException {
        log.info("Starting gRPC server on port: {}", grpcPort);

        Server server = NettyServerBuilder.forPort(grpcPort)
            .addService(forwarderGrpcService)
            .build();

        server.start();
        log.info("gRPC server started successfully on port {}", grpcPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down gRPC server");
            try {
                server.shutdown();
                if (!server.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("Error shutting down gRPC server", e);
                server.shutdownNow();
            }
        }));

        return server;
    }
}
