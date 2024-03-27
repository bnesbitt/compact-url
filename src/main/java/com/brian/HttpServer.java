package com.brian;

import com.brian.cache.InMemoryURLCache;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServer {

    private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);

    public static void main(String[] args) throws Exception {
        new HttpServer().run();
    }

    public void run() throws Exception {
        var serverProperties = new ServerProperties();

        // TTL is defined in seconds. Convert to millis.
        var ttl = serverProperties.getCacheTTL() * 1000;

        var urlCache = new InMemoryURLCache(new Base62Encoder(), serverProperties.getDomain(), ttl);

        var bootstrap = new ServerBootstrap();

        // Allocate all available cores.
        int numCores = Runtime.getRuntime().availableProcessors();
        EventLoopGroup bossGroup = new NioEventLoopGroup(numCores);
        EventLoopGroup workers = new NioEventLoopGroup();

        try {
            bootstrap.group(bossGroup, workers)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new HttpRequestDecoder());
                            p.addLast(new HttpResponseEncoder());

                            // We use this to handle read times from slow or idle clients.
                            p.addLast(new ReadTimeoutHandler(1));

                            // We use this to decode the body and generate the shortened URL.
                            p.addLast(new URLServiceHandler(urlCache));
                        }
                    });

            // Set the connect timeout, and we don't need keepalive.
            bootstrap.childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                    .childOption(ChannelOption.SO_KEEPALIVE, false);

            // Bind to the port and listen
            ChannelFuture f = bootstrap.bind(serverProperties.getPort()).sync();

            logger.info("Starting the URL shortening service on port {}  :: Using {} CPU cores",
                    serverProperties.getPort(), numCores);

            logger.info("Using domain {}", serverProperties.getDomain());
            logger.info("Using cache TTL {}s", ttl);

            f.channel().closeFuture().sync();

        } finally {
            logger.info("Shutting down the URL shortening service.");
            bossGroup.shutdownGracefully();
            workers.shutdownGracefully();
        }
    }
}
