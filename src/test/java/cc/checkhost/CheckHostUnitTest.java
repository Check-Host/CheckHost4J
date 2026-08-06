package cc.checkhost;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Offline unit tests. A loopback {@link HttpServer} stands in for the API,
 * so nothing here touches the network and CI gets a deterministic gate.
 */
public class CheckHostUnitTest {

    private HttpServer server;
    private String baseUrl;

    // Last request the client issued.
    private volatile String lastPath;
    private volatile String lastMethod;
    private volatile String lastAuth;
    private volatile String lastAccept;
    private volatile String lastBody;
    private volatile int callCount;

    // Canned response.
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"success\":true}";

    @BeforeEach
    public void startServer() throws IOException {
        callCount = 0;
        lastPath = lastMethod = lastAuth = lastAccept = lastBody = null;
        responseStatus = 200;
        responseBody = "{\"success\":true}";

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    public void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        callCount++;
        lastPath = exchange.getRequestURI().toString();
        lastMethod = exchange.getRequestMethod();
        lastAuth = exchange.getRequestHeaders().getFirst("Authorization");
        lastAccept = exchange.getRequestHeaders().getFirst("Accept");
        try (InputStream in = exchange.getRequestBody()) {
            lastBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private CheckHost client(String token) {
        return new CheckHost(token, baseUrl);
    }

    // ───────────────────────────── Authentication ─────────────────────────────

    @Test
    public void tokenIsSentAsBearerHeader() {
        client("tok-123").ping("1.1.1.1");
        assertEquals("Bearer tok-123", lastAuth);
    }

    @Test
    public void tokenNeverReachesTheRequestBody() {
        client("tok-123").ping("1.1.1.1");
        assertFalse(lastBody.contains("tok-123"), "token leaked into the body: " + lastBody);
        assertFalse(lastBody.contains("apikey"), "body still carries apikey: " + lastBody);
        assertTrue(lastBody.contains("\"target\":\"1.1.1.1\""), lastBody);
    }

    @Test
    public void tokenNeverReachesTheUrl() {
        client("tok-123").locations();
        assertFalse(lastPath.contains("tok-123"), "token leaked into the URL: " + lastPath);
    }

    @Test
    public void getRequestsAreAuthenticated() {
        client("tok-123").ipIntel("1.1.1.1");
        assertEquals("Bearer tok-123", lastAuth);
    }

    @Test
    public void binaryRequestsAreAuthenticated() {
        client("tok-123").ogImage("uuid-1");
        assertEquals("Bearer tok-123", lastAuth);
        assertEquals("image/png", lastAccept);
    }

    @Test
    public void anonymousClientSendsNoAuthorizationHeader() {
        client(null).ping("1.1.1.1");
        assertNull(lastAuth);
    }

    @Test
    public void emptyTokenSendsNoAuthorizationHeader() {
        client("").ping("1.1.1.1");
        assertNull(lastAuth);
    }

    @Test
    public void baseUrlTrailingSlashesAreTrimmed() {
        CheckHost api = new CheckHost(null, baseUrl + "///");
        api.myip();
        assertEquals("/myip", lastPath);
    }

    // ────────────────────────── Intelligence endpoints ──────────────────────────

    @Test
    public void intelligenceEndpointsBuildDocumentedPaths() {
        CheckHost api = client(null);

        api.ipIntel("1.1.1.1");
        assertEquals("/ip/1.1.1.1", lastPath);
        assertEquals("GET", lastMethod);

        api.asnIntel("AS13335");
        assertEquals("/as/13335", lastPath);

        api.asnIntel("13335");
        assertEquals("/as/13335", lastPath);

        api.asnIntel("as13335");
        assertEquals("/as/13335", lastPath);

        api.asnIntel(13335);
        assertEquals("/as/13335", lastPath);

        api.prefixIntel("1.1.1.0", 24);
        assertEquals("/prefix/1.1.1.0/24", lastPath);

        api.domainIntel("check-host.cc");
        assertEquals("/domain/check-host.cc", lastPath);

        api.certIntel("A".repeat(64));
        assertEquals("/cert/" + "a".repeat(64), lastPath);

        api.portIntel(443);
        assertEquals("/port/443", lastPath);

        api.softwareIntel("nginx");
        assertEquals("/software/nginx", lastPath);

        api.softwareIntel("nginx", "1.24.0");
        assertEquals("/software/nginx/1.24.0", lastPath);

        api.recentScans("check-host.cc");
        assertEquals("/scan/check-host.cc", lastPath);
    }

    @Test
    public void domainIntelPercentEncodesPathSegment() {
        client(null).domainIntel("a b.example");
        assertEquals("/domain/a%20b.example", lastPath);
    }

    @Test
    public void intelligenceRejectsMalformedInput() {
        CheckHost api = client(null);

        assertThrows(CheckHostException.class, () -> api.ipIntel("  "));
        assertThrows(CheckHostException.class, () -> api.ipIntel(null));
        assertThrows(CheckHostException.class, () -> api.asnIntel("not-an-asn"));
        assertThrows(CheckHostException.class, () -> api.asnIntel(""));
        assertThrows(CheckHostException.class, () -> api.asnIntel(-1));
        assertThrows(CheckHostException.class, () -> api.prefixIntel("1.1.1.0", 129));
        assertThrows(CheckHostException.class, () -> api.prefixIntel("1.1.1.0", -1));
        assertThrows(CheckHostException.class, () -> api.prefixIntel(" ", 24));
        assertThrows(CheckHostException.class, () -> api.domainIntel(""));
        assertThrows(CheckHostException.class, () -> api.certIntel("deadbeef"));
        assertThrows(CheckHostException.class, () -> api.portIntel(70000));
        assertThrows(CheckHostException.class, () -> api.portIntel(0));
        assertThrows(CheckHostException.class, () -> api.softwareIntel(""));
        assertThrows(CheckHostException.class, () -> api.recentScans(""));

        assertEquals(0, callCount, "validation must short-circuit before any HTTP call");
    }

    @Test
    public void intelligenceDecodesResponse() {
        responseBody = "{\"success\":true,\"ip\":\"1.1.1.1\",\"family\":\"IPv4\","
                + "\"data\":{\"threat_count\":0,"
                + "\"bgp\":{\"asn\":13335,\"as_name\":\"Cloudflare, Inc.\"}}}";

        JsonNode intel = client(null).ipIntel("1.1.1.1");
        assertTrue(intel.get("success").asBoolean());
        assertEquals("IPv4", intel.get("family").asText());
        assertEquals("Cloudflare, Inc.", intel.at("/data/bgp/as_name").asText());
        assertEquals(0, intel.at("/data/threat_count").asInt());
    }

    // ──────────────────────────────── Fullscan ────────────────────────────────

    @Test
    public void fullscanSubmitPostsTargetAndScope() {
        responseBody = "{\"success\":true,\"uuid\":\"scan-1\",\"target_type\":\"domain\"}";

        JsonNode job = client(null).fullscan("check-host.cc", CheckHost.SCOPE_FULL);
        assertEquals("/fullscan", lastPath);
        assertEquals("POST", lastMethod);
        assertTrue(lastBody.contains("\"target\":\"check-host.cc\""), lastBody);
        assertTrue(lastBody.contains("\"scope\":\"full\""), lastBody);
        assertEquals("scan-1", job.get("uuid").asText());
    }

    @Test
    public void fullscanDefaultsToDeepScope() {
        client(null).fullscan("check-host.cc");
        assertTrue(lastBody.contains("\"scope\":\"deep\""), lastBody);

        client(null).fullscan("check-host.cc", null);
        assertTrue(lastBody.contains("\"scope\":\"deep\""), lastBody);

        client(null).fullscan("check-host.cc", "  ");
        assertTrue(lastBody.contains("\"scope\":\"deep\""), lastBody);
    }

    @Test
    public void fullscanRejectsBadInput() {
        CheckHost api = client(null);
        assertThrows(CheckHostException.class, () -> api.fullscan("x", "turbo"));
        assertThrows(CheckHostException.class, () -> api.fullscan("  "));
        assertThrows(CheckHostException.class, () -> api.fullscanStatus(""));
        assertThrows(CheckHostException.class, () -> api.fullscanResults(""));
        assertEquals(0, callCount, "validation must short-circuit before any HTTP call");
    }

    @Test
    public void fullscanStatusUnwrapsJobEnvelope() {
        responseBody = "{\"success\":true,\"job\":{\"uuid\":\"scan-1\",\"status\":\"running\","
                + "\"subjobs_total\":5,\"subjobs_done\":2}}";

        JsonNode job = client(null).fullscanStatus("scan-1");
        assertEquals("/fullscan/scan-1", lastPath);
        assertEquals("running", job.get("status").asText());
        assertEquals(2, job.get("subjobs_done").asInt());
        assertFalse(CheckHost.isFullscanFinished(job));
    }

    @Test
    public void fullscanResultsBuildsDocumentedPath() {
        responseBody = "{\"success\":true,\"data\":{\"open_ports\":[{\"port\":443}]}}";

        JsonNode results = client(null).fullscanResults("scan-1");
        assertEquals("/fullscan/scan-1/results", lastPath);
        assertEquals(443, results.at("/data/open_ports/0/port").asInt());
    }

    @Test
    public void isFullscanFinishedRecognisesTerminalStatuses() {
        assertTrue(CheckHost.isFullscanFinished(statusNode("complete")));
        assertTrue(CheckHost.isFullscanFinished(statusNode("partial")));
        assertTrue(CheckHost.isFullscanFinished(statusNode("failed")));
        assertTrue(CheckHost.isFullscanFinished(statusNode("COMPLETE")));
        assertFalse(CheckHost.isFullscanFinished(statusNode("pending")));
        assertFalse(CheckHost.isFullscanFinished(statusNode("running")));
        assertFalse(CheckHost.isFullscanFinished(null));
    }

    private JsonNode statusNode(String status) {
        responseBody = "{\"success\":true,\"job\":{\"status\":\"" + status + "\"}}";
        return client(null).fullscanStatus("scan-1");
    }

    @Test
    public void waitForFullscanReturnsImmediatelyWhenFinished() {
        responseBody = "{\"success\":true,\"job\":{\"uuid\":\"scan-1\",\"status\":\"complete\"}}";

        JsonNode job = client(null).waitForFullscan("scan-1", 1000L, 10_000L);
        assertTrue(CheckHost.isFullscanFinished(job));
        assertEquals(1, callCount);
    }

    @Test
    public void waitForFullscanThrowsOnceTheDeadlinePasses() {
        responseBody = "{\"success\":true,\"job\":{\"status\":\"running\","
                + "\"subjobs_done\":1,\"subjobs_total\":5}}";

        CheckHost api = client(null);
        CheckHostException ex = assertThrows(
                CheckHostException.class,
                () -> api.waitForFullscan("scan-1", 1000L, 0L)
        );
        assertTrue(ex.getMessage().contains("not finished"), ex.getMessage());
        assertTrue(ex.getMessage().contains("1/5"), ex.getMessage());
    }

    @Test
    public void waitForFullscanRejectsBlankUuid() {
        CheckHost api = client(null);
        assertThrows(CheckHostException.class, () -> api.waitForFullscan("  ", 1000L, 1000L));
    }

    // ─────────────────────────── Models and errors ───────────────────────────

    @Test
    public void myinfoHitsDocumentedPath() {
        responseBody = "{\"ip\":\"1.2.3.4\",\"country\":\"Germany\",\"success\":true}";

        var info = client(null).myinfo();
        assertEquals("/myinfo", lastPath);
        assertEquals("1.2.3.4", info.ip());
        assertEquals("Germany", info.country());
    }

    @Test
    public void infoDecodesRichSchema() {
        responseBody = "{\"ip\":\"34.36.183.77\",\"countryCode\":\"US\",\"isEu\":false,"
                + "\"postalCode\":\"64101\","
                + "\"privacy\":{\"isHosting\":true,\"isVpn\":false},"
                + "\"asn\":{\"asn\":\"AS396982\",\"name\":\"Google LLC\",\"rir\":\"ARIN\"},"
                + "\"abuse\":{\"email\":\"abuse@example.com\"},\"success\":true}";

        var info = client(null).info("check-host.cc");
        assertEquals("US", info.countryCode());
        assertFalse(info.isEu());
        assertTrue(info.privacy().isHosting());
        assertEquals("Google LLC", info.asn().name());
        assertEquals("ARIN", info.asn().rir());
        assertEquals("abuse@example.com", info.abuse().email());
        // Deprecated alias still resolves to the renamed field.
        assertEquals("64101", info.zipcode());
    }

    @Test
    public void checkCreatedDecodesSwagger21Fields() {
        responseBody = "{\"status\":200,\"success\":true,\"target\":\"check-host.cc\","
                + "\"method\":\"ping\",\"repeatchecks\":0,\"region\":[\"DE\"],"
                + "\"uuid\":\"uuid-1\",\"og-imageURL\":\"https://example/og.png\","
                + "\"port\":null,\"query\":null,\"payload\":null}";

        var created = client(null).ping("check-host.cc");
        assertEquals("uuid-1", created.uuid());
        assertEquals(java.util.List.of("DE"), created.region());
        assertEquals("https://example/og.png", created.ogImageURL());
        assertTrue(created.isSuccess());
    }

    @Test
    public void errorResponsesRaiseCheckHostException() {
        responseStatus = 429;
        responseBody = "{\"success\":false,\"error\":\"Rate limit exceeded. Please slow down.\"}";

        CheckHost api = client(null);
        CheckHostException ex = assertThrows(
                CheckHostException.class,
                () -> api.ping("1.1.1.1")
        );
        assertEquals(429, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Rate limit exceeded"), ex.getMessage());
    }

    @Test
    public void validationErrorsSurfaceTheApiMessage() {
        responseStatus = 422;
        responseBody = "{\"status\":422,\"error\":\"The target field is required.\",\"success\":false}";

        CheckHost api = client(null);
        CheckHostException ex = assertThrows(
                CheckHostException.class,
                () -> api.ping("1.1.1.1")
        );
        assertEquals(422, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("The target field is required."), ex.getMessage());
    }
}
