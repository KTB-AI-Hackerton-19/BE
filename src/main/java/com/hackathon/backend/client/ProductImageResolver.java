package com.hackathon.backend.client;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 추천 상품 링크에서 대표 이미지를 뽑아낸다.
 *
 * <p>AI의 {@code products[].url}은 실제 쇼핑몰 상품 페이지지만 이미지 주소는 주지 않는다. 그래서 그 페이지를
 * 한 번 받아 <b>{@code og:image} 메타 태그</b>를 읽는다. 쇼핑몰은 카톡·슬랙 미리보기 때문에 이 태그를
 * 거의 예외 없이 넣어두므로, 사이트별 HTML 구조를 파싱하는 것보다 훨씬 안정적이다.</p>
 *
 * <p>규칙 하나: <b>절대 실패를 위로 던지지 않는다.</b> 이미지는 카드의 장식이고, 쇼핑몰이 봇을 막거나
 * 느리다고 추천 자체가 실패하면 안 된다. 못 찾으면 null이고 화면은 기존 이모지로 그리면 된다.</p>
 *
 * <p>본문은 앞부분 {@value #MAX_BYTES}바이트만 읽는다. 메타 태그는 {@code <head>}에 있어서 그 뒤는 볼 필요가
 * 없고, 상품 페이지 전체(수 MB)를 받으면 추천 응답이 그만큼 느려진다. 디코딩을 ISO-8859-1로 하는 것도
 * 같은 이유다 — 페이지 인코딩을 알아낼 필요 없이 바이트를 그대로 문자로 매핑하고, 우리가 찾는 URL은
 * 어차피 ASCII라 값이 깨지지 않는다.</p>
 */
@Component
public class ProductImageResolver {

    private static final Logger log = LoggerFactory.getLogger(ProductImageResolver.class);

    /** {@code <head>}를 담기에 충분한 양. 상품 페이지 본문까지 받지 않으려는 상한. */
    private static final int MAX_BYTES = 300_000;

    /** 봇 차단을 피하려고 일반 브라우저처럼 요청한다. UA가 없으면 403을 주는 쇼핑몰이 많다. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    /** 우선순위 순서. og:image가 없으면 트위터 카드, 그것도 없으면 링크 아이콘 순으로 본다. */
    private static final List<Pattern> IMAGE_PATTERNS =
            Stream.of("og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src")
                    .flatMap(key -> metaPatterns(key).stream())
                    .toList();

    private final HttpClient httpClient;
    private final boolean enabled;
    private final Duration timeout;

    public ProductImageResolver(@Value("${ai.product-image.enabled:true}") boolean enabled,
                                @Value("${ai.product-image.timeout-ms:3000}") int timeoutMs) {
        this.enabled = enabled;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                // 쇼핑몰 링크는 단축 URL이나 모바일 도메인으로 한두 번 리다이렉트되는 경우가 흔하다.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 여러 상품 링크의 이미지를 한꺼번에 찾는다.
     *
     * <p>순차로 돌면 링크 수 × 타임아웃만큼 응답이 밀리므로 가상 스레드로 동시에 던진다.
     * 전체 대기 시간은 링크가 몇 개든 타임아웃 한 번 수준으로 끝난다.</p>
     *
     * @return url → 이미지 주소. 못 찾은 링크는 결과에 들어가지 않는다.
     */
    public Map<String, String> resolveAll(List<String> productUrls) {
        List<String> targets = productUrls == null ? List.of() : productUrls.stream()
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .toList();
        if (!enabled || targets.isEmpty()) {
            return Map.of();
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Map.Entry<String, String>>> futures = targets.stream()
                    .map(url -> CompletableFuture.supplyAsync(() -> {
                        String image = resolve(url);
                        return image == null ? null : Map.entry(url, image);
                    }, executor))
                    .toList();

            return futures.stream()
                    .map(future -> {
                        try {
                            // 개별 호출에도 타임아웃이 걸려 있지만, 응답이 천천히 흘러오는 서버 때문에
                            // 전체가 늘어지지 않도록 여기서 한 번 더 상한을 둔다.
                            return future.get(timeout.toMillis() + 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
                        } catch (Exception e) {
                            future.cancel(true);
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first));
        }
    }

    /** 상품 페이지 하나에서 대표 이미지 주소를 뽑는다. 못 찾으면 null. */
    public String resolve(String productUrl) {
        if (!enabled || productUrl == null || productUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(productUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
                return null;
            }

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .GET()
                    .build();

            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                log.debug("상품 이미지 조회 실패 {} — HTTP {}", productUrl, response.statusCode());
                return null;
            }

            String head;
            try (InputStream body = response.body()) {
                byte[] bytes = readAtMost(body, MAX_BYTES);
                head = new String(bytes, StandardCharsets.ISO_8859_1);
            }
            return absolutize(findImage(head), response.uri());
        } catch (Exception e) {
            // InterruptedException 포함. 이미지는 없어도 되는 값이라 로그만 남기고 넘어간다.
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.debug("상품 이미지 조회 실패 {} — {}", productUrl, e.toString());
            return null;
        }
    }

    private byte[] readAtMost(InputStream input, int max) throws java.io.IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int read;
        while (out.size() < max && (read = input.read(buffer)) != -1) {
            out.write(buffer, 0, Math.min(read, max - out.size()));
        }
        return out.toByteArray();
    }

    private String findImage(String html) {
        for (Pattern pattern : IMAGE_PATTERNS) {
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                String value = unescape(matcher.group(1));
                if (!value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    /**
     * {@code <meta property="og:image" content="...">} 형태를 잡는다. 속성 순서가 뒤바뀐
     * ({@code content=...} 가 앞에 오는) 문서도 실제로 흔해서 두 순서를 각각 만들어 둔다 —
     * 한쪽만 보면 절반을 놓친다.
     */
    private static List<Pattern> metaPatterns(String key) {
        String quoted = Pattern.quote(key);
        return List.of(
                Pattern.compile("<meta[^>]+?(?:property|name)\\s*=\\s*[\"']" + quoted
                        + "[\"'][^>]*?content\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<meta[^>]+?content\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?(?:property|name)\\s*=\\s*[\"']"
                        + quoted + "[\"']", Pattern.CASE_INSENSITIVE));
    }

    private String unescape(String value) {
        return value.replace("&amp;", "&").replace("&#38;", "&").replace("&quot;", "\"");
    }

    /** {@code //img.11st.co.kr/...}이나 {@code /images/a.jpg}처럼 스킴·호스트가 빠진 주소를 채워준다. */
    private String absolutize(String image, URI base) {
        if (image == null || image.isBlank()) {
            return null;
        }
        String value = image.trim();
        try {
            if (value.startsWith("//")) {
                return base.getScheme() + ":" + value;
            }
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return value;
            }
            return base.resolve(value).toString();
        } catch (Exception e) {
            return null;
        }
    }
}
