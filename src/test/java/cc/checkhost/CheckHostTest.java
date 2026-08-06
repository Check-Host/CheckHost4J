package cc.checkhost;

import cc.checkhost.models.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live smoke tests against the production API.
 *
 * <p>Tagged {@code live} and excluded from {@code mvn test} by default (see
 * the surefire config in pom.xml). Run them with:
 * {@code mvn test -Dtest.excludedGroups= -Dgroups=live}
 *
 * <p>The deterministic, offline suite is {@link CheckHostUnitTest}.
 */
@Tag("live")
public class CheckHostTest {

    private CheckHost checkHost;

    /**
     * CI populates CHECK_HOST_API_TOKEN via a masked GitLab variable so
     * pipelines pick up the higher per-token rate limit. Locally, and when
     * the env var is empty, we keep the anonymous tier.
     */
    private static String token() {
        String t = System.getenv("CHECK_HOST_API_TOKEN");
        if (t != null && !t.isEmpty()) {
            return t;
        }
        return System.getenv("CHECK_HOST_API_KEY");
    }

    @BeforeEach
    public void setUp() {
        String tok = token();
        checkHost = (tok != null && !tok.isEmpty())
                ? new CheckHost(tok)
                : new CheckHost();
    }

    private void sleepToAvoidRateLimit() {
        try {
            // With a token the per-IP bucket is generous, so a
            // tiny pause keeps log ordering deterministic without
            // wasting CI minutes. Anonymous tier still needs ~5s.
            String tok = token();
            long ms = (tok == null || tok.isEmpty()) ? 5000L : 300L;
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void testMyIp() {
        String ip = checkHost.myip();
        assertNotNull(ip);
        assertFalse(ip.isEmpty());
        System.out.println("testMyIp Response: " + ip);
        sleepToAvoidRateLimit();
    }

    @Test
    public void testLocations() {
        JsonNode locations = checkHost.locations();
        assertNotNull(locations);
        assertTrue(locations.size() > 0);
        System.out.println("testLocations Response size: " + locations.size());
        sleepToAvoidRateLimit();
    }

    @Test
    public void testInfo() {
        MinResponseINFO info = checkHost.info("check-host.cc");
        assertNotNull(info);
        assertNotNull(info.country());
        System.out.println("testInfo Response Country: " + info.country());
        sleepToAvoidRateLimit();
    }

    @Test
    public void testWhois() {
        JsonNode whois = checkHost.whois("check-host.cc");
        assertNotNull(whois);
        System.out.println("testWhois Response keys: " + whois.fieldNames().next());
        sleepToAvoidRateLimit();
    }

    @Test
    public void testPingMinAndMax() {
        CheckCreated min = checkHost.ping("8.8.8.8");
        assertNotNull(min.uuid());
        System.out.println("testPingMin UUID: " + min.uuid());
        sleepToAvoidRateLimit();

        PingOptions options = PingOptions.builder()
                .region(List.of("us1.node.check-host.cc"))
                .repeatchecks(0)
                .build();
        CheckCreated max = checkHost.ping("8.8.8.8", options);
        assertNotNull(max.uuid());
        System.out.println("testPingMax UUID: " + max.uuid());
        sleepToAvoidRateLimit();

        JsonNode report = checkHost.report(max.uuid());
        assertNotNull(report);
        System.out.println("testReport (Ping) fields: "
                + report.toPrettyString().substring(0, Math.min(report.toPrettyString().length(), 100)) + "...");
        sleepToAvoidRateLimit();
    }

    @Test
    public void testDns() {
        DnsOptions options = DnsOptions.builder()
                .querymethod("TXT")
                .region(List.of("us1.node.check-host.cc"))
                .build();
        CheckCreated dns = checkHost.dns("check-host.cc", options);
        assertNotNull(dns.uuid());
        System.out.println("testDns UUID: " + dns.uuid());
        sleepToAvoidRateLimit();
    }

    @Test
    public void testTcp() {
        TcpOptions options = TcpOptions.builder()
                .region(List.of("us1.node.check-host.cc"))
                .build();
        CheckCreated tcp = checkHost.tcp("1.1.1.1", 443, options);
        assertNotNull(tcp.uuid());
        System.out.println("testTcp UUID: " + tcp.uuid());
        sleepToAvoidRateLimit();
    }

    @Test
    public void testUdp() {
        UdpOptions options = UdpOptions.builder()
                .region(List.of("us1.node.check-host.cc"))
                .build();
        CheckCreated udp = checkHost.udp("1.1.1.1", 53, options);
        assertNotNull(udp.uuid());
        System.out.println("testUdp UUID: " + udp.uuid());
        sleepToAvoidRateLimit();
    }

    @Test
    public void testHttp() {
        HttpOptions options = HttpOptions.builder()
                .region(List.of("us1.node.check-host.cc"))
                .build();
        CheckCreated http = checkHost.http("https://check-host.cc", options);
        assertNotNull(http.uuid());
        System.out.println("testHttp UUID: " + http.uuid());
        sleepToAvoidRateLimit();
    }

    @Test
    public void testMtr() {
        MtrOptions options = MtrOptions.builder()
                .region(List.of("us1.node.check-host.cc"))
                .forceIPversion(4)
                .build();
        CheckCreated mtr = checkHost.mtr("1.1.1.1", options);
        assertNotNull(mtr.uuid());
        System.out.println("testMtr UUID: " + mtr.uuid());
        sleepToAvoidRateLimit();
    }

    // --- Network Intelligence ---

    @Test
    public void testMyInfo() {
        MinResponseINFO info = checkHost.myinfo();
        assertNotNull(info.ip());
        System.out.println("testMyInfo: " + info.ip() + " (" + info.country() + ")");
        sleepToAvoidRateLimit();
    }

    @Test
    public void testIpIntel() {
        JsonNode intel = checkHost.ipIntel("1.1.1.1");
        assertTrue(intel.path("success").asBoolean());
        assertEquals("1.1.1.1", intel.path("ip").asText());
        assertTrue(intel.path("data").isObject());
        System.out.println("testIpIntel family: " + intel.path("family").asText());
        sleepToAvoidRateLimit();
    }

    @Test
    public void testAsnIntel() {
        JsonNode intel = checkHost.asnIntel("AS13335");
        assertTrue(intel.path("success").asBoolean());
        assertEquals(13335, intel.path("asn").asInt());
        System.out.println("testAsnIntel: " + intel.path("as_name").asText());
        sleepToAvoidRateLimit();
    }

    @Test
    public void testPrefixIntel() {
        JsonNode intel = checkHost.prefixIntel("1.1.1.0", 24);
        assertTrue(intel.path("success").asBoolean());
        assertEquals("1.1.1.0/24", intel.path("cidr").asText());
        System.out.println("testPrefixIntel: " + intel.path("cidr").asText());
        sleepToAvoidRateLimit();
    }

    @Test
    public void testDomainIntel() {
        JsonNode intel = checkHost.domainIntel("check-host.cc");
        assertTrue(intel.path("success").asBoolean());
        assertEquals("check-host.cc", intel.path("domain").asText());
        sleepToAvoidRateLimit();
    }

    @Test
    public void testPortIntel() {
        JsonNode intel = checkHost.portIntel(443);
        assertTrue(intel.path("success").asBoolean());
        assertEquals(443, intel.path("port").asInt());
        System.out.println("testPortIntel well-known: " + intel.path("well_known").asText());
        sleepToAvoidRateLimit();
    }

    @Test
    public void testSoftwareIntel() {
        JsonNode intel = checkHost.softwareIntel("nginx");
        assertTrue(intel.path("success").asBoolean());
        assertEquals("nginx", intel.path("name").asText());
        sleepToAvoidRateLimit();
    }

    @Test
    public void testRecentScans() {
        JsonNode scans = checkHost.recentScans("check-host.cc");
        assertTrue(scans.path("success").asBoolean());
        assertTrue(scans.path("recent_scans").isArray());
        System.out.println("testRecentScans: " + scans.path("recent_scans").size() + " job(s)");
        sleepToAvoidRateLimit();
    }
}
