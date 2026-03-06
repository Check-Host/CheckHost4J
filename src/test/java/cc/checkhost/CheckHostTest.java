package cc.checkhost;

import cc.checkhost.models.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CheckHostTest {

    private CheckHost checkHost;

    @BeforeEach
    public void setUp() {
        // Initialize client without API key for tests
        checkHost = new CheckHost();
    }

    private void sleepToAvoidRateLimit() {
        try {
            // API without key requires slow execution
            Thread.sleep(5000);
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
}
