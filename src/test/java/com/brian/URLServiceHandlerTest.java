package com.brian;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.brian.cache.InMemoryURLCache;
import com.brian.cache.URLCache;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class URLServiceHandlerTest {

    public static class URLEmbeddedChannel extends EmbeddedChannel{

        private final InetSocketAddress socketAddress;

        public URLEmbeddedChannel(String host, int port, final ChannelHandler... handlers){
            super(handlers);
            socketAddress = new InetSocketAddress(host, port);
        }

        @Override
        protected SocketAddress remoteAddress0(){
            return this.socketAddress;
        }
    }

    @Mock
    ChannelHandlerContext context;

    @Mock
    HttpRequest request;

    @Test
    void requestIsNotGetOrPost() throws Exception {
        URLCache cache = Mockito.mock(URLCache.class);

        Channel channel = Mockito.mock(Channel.class);
        when(context.channel()).thenReturn(channel);

        InetSocketAddress socketAddress = Mockito.mock(InetSocketAddress.class);
        when(channel.remoteAddress()).thenReturn(socketAddress);

        InetAddress addr = Mockito.mock(InetAddress.class);
        when(socketAddress.getAddress()).thenReturn(addr);
        when(addr.getHostAddress()).thenReturn("192.168.1.1");
        when(socketAddress.getPort()).thenReturn(1234);

        when(request.method()).thenReturn(HttpMethod.DELETE);

        ChannelFuture channelFuture = Mockito.mock(ChannelFuture.class);
        when(context.writeAndFlush(any())).thenReturn(channelFuture);

        URLServiceHandler handler = new URLServiceHandler(cache);
        handler.channelRead0(context, request);

        verify(request, times(2)).method();
        verify(context, times(1)).write(any());
        verify(context, times(1)).writeAndFlush(any());
    }

    @Test
    void testPost() {
        URLEncoder urlEncoder = Mockito.mock(URLEncoder.class);
        when(urlEncoder.encode(anyString())).thenReturn("abcxyz");

        var cache = new InMemoryURLCache(urlEncoder, "domain", 60 * 10000);

        var embeddedChannel = new URLEmbeddedChannel("192.168.1.1", 1234,
                new URLServiceHandler(cache));

        // Simulate a POST.
        FullHttpRequest httpRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "");

        // Send in the request.
        httpRequest.content().writeBytes("http://google.com/very/long/path".getBytes());
        embeddedChannel.writeInbound(httpRequest);

        // Check the response.
        FullHttpResponse httpResponse = embeddedChannel.readOutbound();
        HttpResponseStatus status = httpResponse.status();
        // Response should be 200 OK.
        assertEquals(HttpResponseStatus.OK, status);

        // Verify that we get a response body.
        var body = httpResponse.content().toString(Charset.defaultCharset());
        assertEquals("http://domain/abcxyz", body);
    }

    @Test
    void testPostWithMissingBody() {
        var cache = new InMemoryURLCache(new Base62Encoder(), "domain", 60 * 10000);

        var embeddedChannel = new URLEmbeddedChannel("192.168.1.1", 1234,
                new URLServiceHandler(cache));

        // Simulate a POST.
        FullHttpRequest httpRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "");

        // Send in the request.
        httpRequest.content().writeBytes("".getBytes());
        embeddedChannel.writeInbound(httpRequest);

        // Check the response.
        FullHttpResponse httpResponse = embeddedChannel.readOutbound();
        HttpResponseStatus status = httpResponse.status();
        // Response should be 400 as the body is missing.
        assertEquals(HttpResponseStatus.BAD_REQUEST, status);
    }

    @Test
    void testPostWithInvalidBody() {
        var cache = new InMemoryURLCache(new Base62Encoder(), "domain", 60 * 10000);

        var embeddedChannel = new URLEmbeddedChannel("192.168.1.1", 1234,
                new URLServiceHandler(cache));

        // Simulate a POST.
        FullHttpRequest httpRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "");

        // Send in the request.
        httpRequest.content().writeBytes("this is not a URL".getBytes());
        embeddedChannel.writeInbound(httpRequest);

        // Check the response.
        FullHttpResponse httpResponse = embeddedChannel.readOutbound();
        HttpResponseStatus status = httpResponse.status();
        // Response should be 400 as the body is missing.
        assertEquals(HttpResponseStatus.BAD_REQUEST, status);
    }

    @Test
    void testGetWithNoMatchingURL() {
        var cache = new InMemoryURLCache(new Base62Encoder(), "domain", 60 * 10000);

        var embeddedChannel = new URLEmbeddedChannel("192.168.1.1", 1234,
                new URLServiceHandler(cache));

        // Simulate a POST.
        FullHttpRequest httpRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "abczxy");

        // Send in the request.
        embeddedChannel.writeInbound(httpRequest);

        // Check the response.
        FullHttpResponse httpResponse = embeddedChannel.readOutbound();
        HttpResponseStatus status = httpResponse.status();
        // Response should be 404 as the lookup fails.
        assertEquals(HttpResponseStatus.NOT_FOUND, status);
    }

    @Test
    void testGetWithMatchingURL() {
        URLEncoder urlEncoder = Mockito.mock(URLEncoder.class);
        when(urlEncoder.encode(anyString())).thenReturn("abczxy");

        var cache = new InMemoryURLCache(urlEncoder, "domain", 60 * 10000);

        // Pre-populate the cache with the entry we want.
        cache.shorten(UUID.randomUUID(), "http://test-url.com/this/is_a_very_long/path");

        var embeddedChannel = new URLEmbeddedChannel("192.168.1.1", 1234,
                new URLServiceHandler(cache));

        // Simulate a POST.
        FullHttpRequest httpRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "abczxy");

        // Send in the request.
        embeddedChannel.writeInbound(httpRequest);

        // Check the response.
        FullHttpResponse httpResponse = embeddedChannel.readOutbound();
        HttpResponseStatus status = httpResponse.status();

        // Response should be a 301 redirecting to the cached URL.
        assertEquals(HttpResponseStatus.MOVED_PERMANENTLY, status);

        // Check the location header.
        var location = httpResponse.headers().get("location");
        assertNotNull(location);
        assertEquals("http://test-url.com/this/is_a_very_long/path", location);

        // Check the response.
        var body = httpResponse.content().toString(Charset.defaultCharset());

        var expectedResponse = """
                <!DOCTYPE HTML>
                <html lang="en-US">
                    <head>
                        <meta charset="UTF-8">
                        <meta http-equiv="refresh" content="0; url=http://test-url.com/this/is_a_very_long/path">
                        <title>Page Redirection</title>
                    </head>
                </html>
                """;
        assertEquals(expectedResponse, body);
    }

}
