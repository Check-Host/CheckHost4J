package cc.checkhost;

import cc.checkhost.models.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Check-Host API Wrapper Client
 */
public class CheckHost {
    private static final String DEFAULT_BASE_URL = "https://api.check-host.cc";

    private final String baseUrl;

    /** Scopes accepted by {@link #fullscan(String, String)}. */
    public static final String SCOPE_BASIC = "basic";
    public static final String SCOPE_DEEP = "deep";
    public static final String SCOPE_FULL = "full";

    private static final Set<String> FULLSCAN_SCOPES =
            Set.of(SCOPE_BASIC, SCOPE_DEEP, SCOPE_FULL);

    private static final Set<String> FULLSCAN_TERMINAL =
            Set.of("complete", "partial", "failed");

    private static final Pattern ASN_PATTERN = Pattern.compile("^(?i:AS)?(\\d+)$");
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");

    /**
     * API token (UUID), sent as an {@code Authorization: Bearer <token>}
     * header on every request. Never appears in a URL or a request body.
     */
    private final String token;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    /** Anonymous client. Public rate limits apply. */
    public CheckHost() {
        this(null);
    }

    /**
     * @param token API token (UUID) for higher rate limits, or {@code null}
     *              for anonymous access. It is sent as an
     *              {@code Authorization: Bearer} header, so it never lands
     *              in a URL, an access log or a request body.
     */
    public CheckHost(String token) {
        this(token, DEFAULT_BASE_URL);
    }

    /**
     * @param token   API token (UUID), or {@code null} for anonymous access.
     * @param baseUrl Override the API base URL - useful for tests, or for
     *                the {@code https://check-host.cc/api} mirror when
     *                {@code api.check-host.cc} is blocked by a network.
     */
    public CheckHost(String token, String baseUrl) {
        this.baseUrl = (baseUrl == null || baseUrl.isEmpty())
                ? DEFAULT_BASE_URL
                : baseUrl.replaceAll("/+$", "");
        this.token = token;
        // Force HTTP/1.1: the Java HttpClient's HTTP/2 transport tends
        // to negotiate brotli with the CDN edge, which we cannot
        // transparently decompress. Sticking to HTTP/1.1 keeps the
        // server in plain / gzip territory.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
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

    /**
     * Geolocation + ASN for the caller's own IP.
     *
     * <p>Same response shape as {@link #info(String)}, resolved against the
     * requesting client's IP. Subject to bot detection - repeated cache
     * misses can return a 429 carrying a captcha verification URL.
     */
    public MinResponseINFO myinfo() {
        return get("/myinfo", MinResponseINFO.class);
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

    // --- Network Intelligence ---
    //
    // Passive lookups against the dataset behind the entity pages - no check
    // is dispatched to the monitoring nodes, so results come back
    // immediately. Responses are returned as JsonNode because each endpoint's
    // "data" section has a different, open-ended key set; sections we hold no
    // data for come back as empty arrays or null.

    /**
     * Full intelligence profile for a single IPv4/IPv6 address.
     *
     * <p>Data sections: {@code ptr}, {@code open_ports}, {@code banners},
     * {@code tls_certs}, {@code co_hosted_domains}, {@code external_refs},
     * {@code leak_candidates}, {@code titles}, {@code techs}, {@code bgp},
     * {@code geo}, {@code probe_findings}, {@code threat_matches},
     * {@code threat_count}, {@code honeypot}, {@code honeypot_recent},
     * {@code honeypot_actor}, {@code honeypot_ja}, {@code honeypot_classes}.
     *
     * <p>Honeypot passwords are never returned in cleartext - each entry
     * exposes only {@code password_captured} (bool) and {@code password_len}.
     */
    public JsonNode ipIntel(String ip) {
        requireText(ip, "ip");
        return get("/ip/" + encode(ip.trim()), JsonNode.class);
    }

    /**
     * Autonomous-system intelligence: prefix counts, announced IP totals,
     * peers / providers / customers, IXP memberships, RPKI coverage, GeoIP
     * footprint, top ports and hosted-domain summaries.
     *
     * @param asn {@code "13335"} or {@code "AS13335"}.
     */
    public JsonNode asnIntel(String asn) {
        return get("/as/" + normaliseAsn(asn), JsonNode.class);
    }

    /** Convenience overload taking a numeric AS number. */
    public JsonNode asnIntel(int asn) {
        if (asn < 0) {
            throw new CheckHostException("asn must be >= 0, got " + asn);
        }
        return get("/as/" + asn, JsonNode.class);
    }

    /**
     * CIDR prefix intelligence: BGP origin, RPKI validity, GeoIP
     * distribution, open-IP count, top ports and sample scanned hosts.
     *
     * @param net  Network address, e.g. {@code "1.1.1.0"}.
     * @param mask Prefix length, 0-128.
     */
    public JsonNode prefixIntel(String net, int mask) {
        requireText(net, "net");
        if (mask < 0 || mask > 128) {
            throw new CheckHostException("mask must be between 0 and 128, got " + mask);
        }
        return get("/prefix/" + encode(net.trim()) + "/" + mask, JsonNode.class);
    }

    /**
     * Domain intelligence: current DNS records plus passive-DNS history, TLS
     * certificates, CT-log evidence, discovered subdomains, tech-stack and
     * origin-leak (Cloudflare-bypass) candidates.
     */
    public JsonNode domainIntel(String domain) {
        requireText(domain, "domain");
        return get("/domain/" + encode(domain.trim()), JsonNode.class);
    }

    /**
     * TLS certificate intelligence: subject, issuer, SANs and validity
     * window, every {@code (ip, port)} observed serving it, and the matching
     * CT-log entries.
     *
     * @param sha256 64-character hex fingerprint.
     */
    public JsonNode certIntel(String sha256) {
        requireText(sha256, "sha256");
        String normalised = sha256.trim().toLowerCase(Locale.ROOT);
        if (!SHA256_PATTERN.matcher(normalised).matches()) {
            throw new CheckHostException(
                    "sha256 must be 64 hexadecimal characters, got '" + sha256 + "'."
            );
        }
        return get("/cert/" + normalised, JsonNode.class);
    }

    /**
     * Port exposure across the scanned Internet: open-IP count, most common
     * banners, top countries and ASNs, tech-stack and a sample of recent
     * hosts.
     *
     * @param port 1-65535.
     */
    public JsonNode portIntel(int port) {
        if (port < 1 || port > 65535) {
            throw new CheckHostException("port must be between 1 and 65535, got " + port);
        }
        return get("/port/" + port, JsonNode.class);
    }

    /**
     * Software / tech-stack intelligence: host counts for a detected
     * technology, version breakdown, categories and a sample of hosts.
     *
     * @param name    Technology name, e.g. {@code "nginx"}.
     * @param version Pin the stats to a single version, or {@code null} for
     *                all versions.
     */
    public JsonNode softwareIntel(String name, String version) {
        requireText(name, "name");
        String path = "/software/" + encode(name.trim());
        if (version != null && !version.trim().isEmpty()) {
            path += "/" + encode(version.trim());
        }
        return get(path, JsonNode.class);
    }

    /** Overload covering every version of {@code name}. */
    public JsonNode softwareIntel(String name) {
        return softwareIntel(name, null);
    }

    /**
     * Most-recent fullscan jobs submitted for a target (IP, CIDR, domain or
     * ASN), so you can deep-link to a fresh report instead of triggering a
     * redundant scan.
     */
    public JsonNode recentScans(String target) {
        requireText(target, "target");
        return get("/scan/" + encode(target.trim()), JsonNode.class);
    }

    // --- Fullscan ---

    /**
     * Dispatches a deep, multi-stage scan (ports + banners + TLS + DNS +
     * threat-intel) of an IP, CIDR, domain or ASN.
     *
     * <p>Returns immediately with {@code status = "pending"}. Poll
     * {@link #fullscanStatus(String)} for progress, or use
     * {@link #waitForFullscan(String, long, long)}.
     *
     * <p>Anonymous CIDR submissions are capped at /24 (v4) and /120 (v6); an
     * API token raises that to /20 and /112.
     *
     * @param target IPv4/IPv6 address, CIDR block, domain or AS number.
     * @param scope  {@link #SCOPE_BASIC}, {@link #SCOPE_DEEP} (default) or
     *               {@link #SCOPE_FULL}. {@code null} or empty selects
     *               {@code deep}.
     */
    public JsonNode fullscan(String target, String scope) {
        requireText(target, "target");
        String normalised = (scope == null || scope.trim().isEmpty())
                ? SCOPE_DEEP
                : scope.trim().toLowerCase(Locale.ROOT);
        if (!FULLSCAN_SCOPES.contains(normalised)) {
            throw new CheckHostException(
                    "scope must be one of " + FULLSCAN_SCOPES + ", got '" + scope + "'."
            );
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("target", target.trim());
        payload.put("scope", normalised);
        return post("/fullscan", payload, JsonNode.class);
    }

    /** Overload using the default {@code deep} scope. */
    public JsonNode fullscan(String target) {
        return fullscan(target, SCOPE_DEEP);
    }

    /**
     * Polls a fullscan's progress counters and returns the job row (the
     * {@code job} object, unwrapped from the response envelope).
     */
    public JsonNode fullscanStatus(String uuid) {
        requireText(uuid, "uuid");
        JsonNode response = get("/fullscan/" + encode(uuid.trim()), JsonNode.class);
        JsonNode job = response.get("job");
        return (job != null && job.isObject()) ? job : response;
    }

    /**
     * Fetches the aggregated findings a fullscan produced - open ports,
     * banners, DNS records, BGP context and TLS certificates. Partial
     * results are available while the job is still running.
     */
    public JsonNode fullscanResults(String uuid) {
        requireText(uuid, "uuid");
        return get("/fullscan/" + encode(uuid.trim()) + "/results", JsonNode.class);
    }

    /**
     * Reports whether a job row has reached a terminal status and will not
     * progress further.
     */
    public static boolean isFullscanFinished(JsonNode job) {
        if (job == null) {
            return false;
        }
        JsonNode status = job.get("status");
        if (status == null || status.isNull()) {
            return false;
        }
        return FULLSCAN_TERMINAL.contains(status.asText("").toLowerCase(Locale.ROOT));
    }

    /**
     * Polls {@link #fullscanStatus(String)} until the job reaches a terminal
     * status or {@code maxWaitMillis} elapses.
     *
     * <p>Fullscans are far slower than node checks - budget minutes, not
     * seconds. {@code intervalMillis} is clamped to at least 1000 ms.
     *
     * @return the last observed job row.
     * @throws CheckHostException if the deadline passes while the job is
     *                            still pending or running.
     */
    public JsonNode waitForFullscan(String uuid, long intervalMillis, long maxWaitMillis) {
        requireText(uuid, "uuid");
        long interval = Math.max(intervalMillis, 1000L);
        long deadline = System.nanoTime() + maxWaitMillis * 1_000_000L;

        JsonNode job = fullscanStatus(uuid);
        if (isFullscanFinished(job)) {
            return job;
        }

        while (true) {
            long remainingMillis = (deadline - System.nanoTime()) / 1_000_000L;
            if (remainingMillis <= 0) {
                break;
            }
            try {
                Thread.sleep(Math.min(interval, remainingMillis));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CheckHostException("Interrupted while polling fullscan " + uuid, e);
            }
            job = fullscanStatus(uuid);
            if (isFullscanFinished(job)) {
                return job;
            }
        }

        throw new CheckHostException(String.format(
                "Fullscan %s not finished after %dms (status=%s, %s/%s sub-jobs).",
                uuid,
                maxWaitMillis,
                job.path("status").asText("unknown"),
                job.path("subjobs_done").asText("?"),
                job.path("subjobs_total").asText("?")
        ));
    }

    /** Overload polling every 3s for up to 5 minutes. */
    public JsonNode waitForFullscan(String uuid) {
        return waitForFullscan(uuid, 3_000L, 300_000L);
    }

    // --- Internal Helpers ---

    private static void requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new CheckHostException(name + " is required and must not be blank.");
        }
    }

    /** Percent-encodes a single path segment. */
    private static String encode(String segment) {
        // URLEncoder is form-encoding, so '+' has to be corrected back to
        // the path-safe %20 for spaces.
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Copies a request's headers into the flat {@code name, value, ...} array
     * {@link HttpRequest.Builder#headers} expects, overriding
     * {@code Accept-Encoding} with {@code override}.
     */
    private static String[] flattenHeaders(HttpRequest request, String encodingOverride) {
        java.util.List<String> flat = new java.util.ArrayList<>();
        request.headers().map().forEach((name, values) -> {
            if (name.equalsIgnoreCase("Accept-Encoding")) {
                return;
            }
            for (String value : values) {
                flat.add(name);
                flat.add(value);
            }
        });
        flat.add("Accept-Encoding");
        flat.add(encodingOverride);
        return flat.toArray(new String[0]);
    }

    /** Normalises {@code 13335} / {@code AS13335} to its bare decimal form. */
    private static String normaliseAsn(String asn) {
        requireText(asn, "asn");
        Matcher matcher = ASN_PATTERN.matcher(asn.trim());
        if (!matcher.matches()) {
            throw new CheckHostException(
                    "asn must look like '13335' or 'AS13335', got '" + asn + "'."
            );
        }
        return matcher.group(1);
    }

    private Map<String, Object> createPayload(String target, Object options) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("target", target);
        // No credential here: the token travels in the Authorization header.
        if (options != null) {
            Map<String, Object> optionsMap = mapper.convertValue(options, new TypeReference<>() {
            });
            payload.putAll(optionsMap);
        }
        return payload;
    }

    /**
     * Starts a request builder with the headers every call shares.
     *
     * <p>Ask for gzip explicitly (so the CDN doesn't fall back to brotli,
     * which java.net.http won't decode); we inflate ourselves in
     * {@link #execute}. When a token is configured it is attached as
     * {@code Authorization: Bearer <token>} - never as a query parameter or
     * a body field.
     */
    private HttpRequest.Builder requestBuilder(String endpoint, String accept) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Accept", accept)
                .header("Accept-Encoding", "gzip");
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private <T> T get(String endpoint, Class<T> responseType) {
        HttpRequest request = requestBuilder(endpoint, "application/json")
                .GET()
                .build();
        return execute(request, responseType);
    }

    private <T> T post(String endpoint, Map<String, Object> payload, Class<T> responseType) {
        try {
            String jsonBody = mapper.writeValueAsString(payload);
            HttpRequest request = requestBuilder(endpoint, "application/json")
                    .header("Content-Type", "application/json")
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
        // Demand an identity body: PNG/SVG payloads must reach the caller
        // byte-for-byte, and there is no point compressing an already
        // compressed PNG.
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Accept", accept)
                .header("Accept-Encoding", "identity")
                .GET()
                .build();
        if (token != null && !token.isEmpty()) {
            request = HttpRequest.newBuilder(request.uri())
                    .header("Accept", accept)
                    .header("Accept-Encoding", "identity")
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
        }
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new CheckHostException(
                        "API Error: " + response.statusCode(),
                        response.statusCode()
                );
            }
            // The SVG variant of /country-map is text and may still arrive
            // gzipped; decode it so callers always get the real bytes.
            byte[] decoded = decodeBody(
                    response.body(),
                    response.headers().firstValue("content-encoding").orElse(null)
            );
            return decoded != null ? decoded : response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new CheckHostException("HTTP Request failed: " + e.getMessage(), e);
        }
    }

    /** True when {@code body} starts with the gzip magic bytes. */
    private static boolean isGzip(byte[] body) {
        return body != null && body.length >= 2
                && (body[0] & 0xFF) == 0x1F
                && (body[1] & 0xFF) == 0x8B;
    }

    /**
     * Inflates a response body when needed.
     *
     * <p>java.net.http performs no transparent decompression, so we handle
     * it. We only advertise {@code gzip}, but the CDN edge has been observed
     * ignoring that and answering with an encoding we cannot decode (brotli);
     * in that case this returns {@code null} so the caller can retry with
     * {@code identity} rather than feeding binary to Jackson.
     *
     * @return the decoded body, or {@code null} if the encoding is not
     *         something we can decode.
     */
    private static byte[] decodeBody(byte[] rawBody, String contentEncoding) throws IOException {
        if (rawBody == null || rawBody.length == 0) {
            return rawBody;
        }
        String encoding = contentEncoding == null
                ? ""
                : contentEncoding.trim().toLowerCase(Locale.ROOT);

        // Trust the magic bytes over the header: the edge sometimes gzips
        // without advertising it.
        if (isGzip(rawBody)) {
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(rawBody))) {
                return gz.readAllBytes();
            }
        }
        if (encoding.isEmpty() || encoding.equals("identity")) {
            return rawBody;
        }
        if (encoding.equals("gzip")) {
            // Header says gzip but the magic bytes disagree - the body is
            // already plain (some proxies strip the encoding but keep the
            // header).
            return rawBody;
        }
        // br, zstd, compress, ... - not decodable here.
        return null;
    }

    private <T> T execute(HttpRequest request, Class<T> responseType) {
        try {
            // Read as raw bytes so we can transparently inflate compressed
            // responses. The Check-Host edge compresses large JSON bodies
            // (notably /locations), so relying on BodyHandlers.ofString
            // here would surface garbled bytes to Jackson.
            HttpResponse<byte[]> raw = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofByteArray()
            );
            byte[] rawBody = decodeBody(
                    raw.body(), raw.headers().firstValue("content-encoding").orElse(null)
            );
            int status = raw.statusCode();

            if (rawBody == null) {
                // Undecodable encoding: retry once demanding identity so a
                // brotli-serving edge can't break the call.
                HttpRequest retry = HttpRequest.newBuilder(request.uri())
                        .method(
                                request.method(),
                                request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody())
                        )
                        .headers(flattenHeaders(request, "identity"))
                        .build();
                HttpResponse<byte[]> retried = httpClient.send(
                        retry, HttpResponse.BodyHandlers.ofByteArray()
                );
                rawBody = decodeBody(
                        retried.body(),
                        retried.headers().firstValue("content-encoding").orElse(null)
                );
                status = retried.statusCode();
                if (rawBody == null) {
                    throw new CheckHostException(
                            "Server returned a response encoding this client cannot decode: "
                                    + retried.headers()
                                        .firstValue("content-encoding").orElse("unknown"),
                            status
                    );
                }
            }

            String body = new String(rawBody, StandardCharsets.UTF_8);

            if (status >= 400) {
                String errorMsg = "API Error: " + status;
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
                throw new CheckHostException(errorMsg, status);
            }

            if (responseType == String.class) {
                return responseType.cast(body);
            }
            return mapper.readValue(body, responseType);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new CheckHostException("HTTP Request failed: " + e.getMessage(), e);
        }
    }
}
