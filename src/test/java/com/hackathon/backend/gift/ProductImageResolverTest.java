package com.hackathon.backend.gift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hackathon.backend.client.ProductImageResolver;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 상품 페이지에서 대표 이미지를 뽑는 부분만 검증한다. 실제 쇼핑몰을 때리면 테스트가 남의 사이트 사정에
 * 따라 깨지므로, 같은 HTML 모양을 로컬 서버로 흉내 낸다.
 *
 * <p>보는 것은 <b>실제로 틀리기 쉬운 것들</b>이다 — 속성 순서가 뒤집힌 meta, 스킴이 빠진 {@code //} 주소,
 * 태그가 아예 없을 때 예외 대신 null이 나오는지.</p>
 */
class ProductImageResolverTest {

    private HttpServer server;
    private String baseUrl;
    private final ProductImageResolver resolver = new ProductImageResolver(true, 3000);

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serve("/normal", """
                <html><head><meta property="og:image" content="https://cdn.example.com/a.jpg"></head></html>""");
        // content가 앞에 오는 문서도 실제로 흔하다.
        serve("/reversed", """
                <html><head><meta content="https://cdn.example.com/b.jpg" property="og:image"></head></html>""");
        // 스킴 없는 주소. 그대로 내보내면 프론트에서 깨진 이미지가 된다.
        serve("/schemeless", """
                <html><head><meta property="og:image" content="//cdn.example.com/c.jpg"></head></html>""");
        // og:image가 없으면 트위터 카드로 내려간다.
        serve("/twitter", """
                <html><head><meta name="twitter:image" content="https://cdn.example.com/d.jpg"></head></html>""");
        serve("/none", "<html><head><title>이미지 없는 상품</title></head></html>");
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void serve(String path, String html) {
        server.createContext(path, exchange -> {
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    @Test
    void metaTagsAreRead() {
        assertEquals("https://cdn.example.com/a.jpg", resolver.resolve(baseUrl + "/normal"));
        assertEquals("https://cdn.example.com/b.jpg", resolver.resolve(baseUrl + "/reversed"));
        assertEquals("https://cdn.example.com/d.jpg", resolver.resolve(baseUrl + "/twitter"));
    }

    @Test
    void schemelessUrlBorrowsPageScheme() {
        assertEquals("http://cdn.example.com/c.jpg", resolver.resolve(baseUrl + "/schemeless"));
    }

    /** 이미지는 없어도 되는 값이다 — 실패는 null이지 예외가 아니다. */
    @Test
    void missingTagOrDeadLinkIsNull() {
        assertNull(resolver.resolve(baseUrl + "/none"));
        assertNull(resolver.resolve(baseUrl + "/404-not-there"));
        assertNull(resolver.resolve("not-a-url"));
        assertNull(resolver.resolve(null));
    }

    /** 못 찾은 링크는 결과에서 빠질 뿐, 나머지는 그대로 나와야 한다. */
    @Test
    void resolveAllSkipsFailures() {
        Map<String, String> images = resolver.resolveAll(
                List.of(baseUrl + "/normal", baseUrl + "/none", baseUrl + "/twitter"));
        assertEquals(Map.of(
                baseUrl + "/normal", "https://cdn.example.com/a.jpg",
                baseUrl + "/twitter", "https://cdn.example.com/d.jpg"), images);
    }

    @Test
    void disabledResolverDoesNothing() {
        ProductImageResolver off = new ProductImageResolver(false, 3000);
        assertNull(off.resolve(baseUrl + "/normal"));
        assertEquals(Map.of(), off.resolveAll(List.of(baseUrl + "/normal")));
    }
}
