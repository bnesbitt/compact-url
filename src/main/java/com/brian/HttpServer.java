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
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class HttpServer {

    // Hard coded port number.
    // TODO: Could read in from args to make it configurable.
    private static final int PORT = 8888;

    public static void main(String[] args) throws Exception {
        new HttpServer().run();
    }

    public void run() throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();

        // Allocate all available cores.
        EventLoopGroup bossGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());
        EventLoopGroup workers = new NioEventLoopGroup();

        URLCache urlCache = new URLCache("short.co");

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

            ChannelFuture f = bootstrap.bind(PORT).sync();
            f.channel().closeFuture().sync();

        } finally {
            bossGroup.shutdownGracefully();
            workers.shutdownGracefully();
        }
    }
}
