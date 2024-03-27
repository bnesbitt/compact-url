package com.brian;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServer {

    private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);

    public static void main(String[] args) throws Exception {
        new HttpServer().run();
    }

    public void run() throws Exception {
        var serverProperties = new ServerProperties();
        var urlCache = new InMemoryURLCache(new Base62Encoder(), serverProperties.getDomain());

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
                            p.addLast(new URLServiceHandler(urlCache));
                        }
                    });

            // Bind to the port and listen
            ChannelFuture f = bootstrap.bind(serverProperties.getPort()).sync();

            logger.info("Starting the URL shortening service on port {}  :: Using {} CPU cores",
                    serverProperties.getPort(), numCores);

            f.channel().closeFuture().sync();

        } finally {
            logger.info("Shutting down the URL shortening service.");
            bossGroup.shutdownGracefully();
            workers.shutdownGracefully();
        }
    }
}
