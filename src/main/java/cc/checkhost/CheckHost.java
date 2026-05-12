package cc.checkhost;

import cc.checkhost.models.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Check-Host API Wrapper Client
 */
public class CheckHost {
    private static final String BASE_URL = "https://api.check-host.cc";
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public CheckHost() {
        this(null);
    }

    public CheckHost(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    // --- Utilities ---

    public String myip() {
        return get("/myip", String.class);
    }

    public JsonNode locations() {
        return get("/locations", JsonNode.class);
    }

    public MinResponseINFO info(String target) {
        return post("/info", createPayload(target, null), MinResponseINFO.class);
    }

    public JsonNode whois(String target) {
        return post("/whois", createPayload(target, null), JsonNode.class);
    }

    // --- Active Monitoring ---

    public CheckCreated ping(String target) {
        return ping(target, null);
    }

    public CheckCreated ping(String target, PingOptions options) {
        return post("/ping", createPayload(target, options), CheckCreated.class);
    }

    public CheckCreated dns(String target) {
        return dns(target, null);
    }

    public CheckCreated dns(String target, DnsOptions options) {
        return post("/dns", createPayload(target, options), CheckCreated.class);
    }

    public CheckCreated tcp(String target, int port) {
        return tcp(target, port, null);
    }

    public CheckCreated tcp(String target, int port, TcpOptions options) {
        Map<String, Object> payload = createPayload(target, options);
        payload.put("port", port);
        return post("/tcp", payload, CheckCreated.class);
    }

    public CheckCreated udp(String target, int port) {
        return udp(target, port, null);
    }

    public CheckCreated udp(String target, int port, UdpOptions options) {
        Map<String, Object> payload = createPayload(target, options);
        payload.put("port", port);
        return post("/udp", payload, CheckCreated.class);
    }

    public CheckCreated http(String target) {
        return http(target, null);
    }

    public CheckCreated http(String target, HttpOptions options) {
        return post("/http", createPayload(target, options), CheckCreated.class);
    }

    public CheckCreated mtr(String target) {
        return mtr(target, null);
    }

    public CheckCreated mtr(String target, MtrOptions options) {
        return post("/mtr", createPayload(target, options), CheckCreated.class);
    }

    // --- Reporting ---

    public JsonNode report(String uuid) {
        return get("/report/" + uuid, JsonNode.class);
    }

    /**
     * Fetches the dynamic 1200x630 PNG status map for a check UUID.
     * Returns the raw PNG bytes.
     */
    public byte[] ogImage(String uuid) {
        return getBinary("/report/" + uuid + "/og-image", "image/png");
    }

    /**
     * Fetches the per-country world map for a check UUID. Default
     * format is SVG; pass {@code "png"} with a resolution for the
     * rasterised variant.
     *
     * @param uuid       The check UUID.
     * @param format     {@code "svg"} (default) or {@code "png"}.
     * @param resolution PNG resolution: {@code "low"} (800px),
     *                   {@code "med"} (1200px), or {@code "high"}
     *                   (2000px). Ignored for SVG.
     * @return Raw image bytes (UTF-8 text for SVG, binary for PNG).
     */
    public byte[] countryMap(String uuid, String format, String resolution) {
        if (format == null || format.isEmpty()) format = "svg";
        if (resolution == null || resolution.isEmpty()) resolution = "med";
        if (!"svg".equals(format) && !"png".equals(format)) {
            throw new CheckHostException("format must be 'svg' or 'png', got '" + format + "'.");
        }
        switch (resolution) {
            case "low": case "med": case "high": break;
            default:
                throw new CheckHostException(
                        "resolution must be 'low', 'med', or 'high', got '" + resolution + "'."
                );
        }
        String accept = "png".equals(format) ? "image/png" : "image/svg+xml";
        String path = "/report/" + uuid + "/country-map?format=" + format + "&res=" + resolution;
        return getBinary(path, accept);
    }

    /** Overload that defaults to SVG / medium resolution. */
    public byte[] countryMap(String uuid) {
        return countryMap(uuid, "svg", "med");
    }

    // --- Internal Helpers ---

    private Map<String, Object> createPayload(String target, Object options) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("target", target);
        if (apiKey != null && !apiKey.isEmpty()) {
            payload.put("apikey", apiKey);
        }
        if (options != null) {
            Map<String, Object> optionsMap = mapper.convertValue(options, new TypeReference<>() {
            });
            payload.putAll(optionsMap);
        }
        return payload;
    }

    private <T> T get(String endpoint, Class<T> responseType) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Accept", "application/json")
                // java.net.http.HttpClient does not transparently decompress
                // gzip/br responses; ask the server for an identity body so
                // we can hand it straight to Jackson.
                .header("Accept-Encoding", "identity")
                .GET()
                .build();
        return execute(request, responseType);
    }

    private <T> T post(String endpoint, Map<String, Object> payload, Class<T> responseType) {
        try {
            String jsonBody = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + endpoint))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "identity")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            return execute(request, responseType);
        } catch (JsonProcessingException e) {
            throw new CheckHostException("Failed to serialize request payload", e);
        }
    }

    /**
     * Issues a GET against {@code endpoint} and returns the raw response
     * body without trying to JSON-decode it. Used for binary endpoints
     * (og-image, country-map).
     */
    private byte[] getBinary(String endpoint, String accept) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Accept", accept)
                // Disable transparent decompression so the byte stream
                // matches the wire content exactly.
                .header("Accept-Encoding", "identity")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new CheckHostException(
                        "API Error: " + response.statusCode(),
                        response.statusCode()
                );
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new CheckHostException("HTTP Request failed: " + e.getMessage(), e);
        }
    }

    private <T> T execute(HttpRequest request, Class<T> responseType) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (response.statusCode() >= 400) {
                // Try to extract an error message if possible
                String errorMsg = "API Error: " + response.statusCode();
                try {
                    JsonNode errorNode = mapper.readTree(body);
                    if (errorNode.has("error")) {
                        errorMsg += " - " + errorNode.get("error").asText();
                    } else if (errorNode.has("message")) {
                        errorMsg += " - " + errorNode.get("message").asText();
                    }
                } catch (Exception ignored) {
                    errorMsg += " - " + body;
                }
                throw new CheckHostException(errorMsg, response.statusCode());
            }

            if (responseType == String.class) {
                return responseType.cast(body);
            }
            return mapper.readValue(body, responseType);

        } catch (IOException | InterruptedException e) {
            throw new CheckHostException("HTTP Request failed: " + e.getMessage(), e);
        }
    }
}
