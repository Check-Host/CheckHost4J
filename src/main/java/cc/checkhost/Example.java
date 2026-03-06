package cc.checkhost;

import cc.checkhost.models.*;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A comprehensive example demonstrating all available methods in the
 * CheckHost4J library.
 */
public class Example {

        public static void main(String[] args) {
                // Initialize the client. The API Key is optional.
                // Without an API key, standard public rate limits apply.
                CheckHost checkHost = new CheckHost();

                // Note: For public endpoints without an API key, you should wait
                // a few seconds between requests to avoid rate limits.

                try {
                        System.out.println("=========================================");
                        System.out.println("   1. INFORMATION & UTILITY ENDPOINTS    ");
                        System.out.println("=========================================\n");

                        // --- Get My IP ---
                        System.out.println("[->] Fetching My IP...");
                        String myIp = checkHost.myip();
                        System.out.println("My IP: " + myIp);
                        Thread.sleep(2000);

                        // --- Get Locations ---
                        System.out.println("\n[->] Fetching Available Nodes...");
                        JsonNode locations = checkHost.locations();
                        System.out.println("Total Nodes Available: " + locations.size());
                        Thread.sleep(2000);

                        // --- Host Info (GeoIP/ASN) ---
                        System.out.println("\n[->] Fetching Info for 'check-host.cc'...");
                        MinResponseINFO info = checkHost.info("check-host.cc");
                        System.out.println("Host Country: " + info.country() + ", IP: " + info.ip());
                        Thread.sleep(2000);

                        // --- WHOIS Lookup ---
                        System.out.println("\n[->] Fetching WHOIS for 'check-host.cc'...");
                        JsonNode whois = checkHost.whois("check-host.cc");
                        System.out.println("WHOIS Data Contains: " + whois.fieldNames().next());
                        Thread.sleep(2000);

                        System.out.println("\n=========================================");
                        System.out.println("   2. ACTIVE MONITORING ENDPOINTS (POST) ");
                        System.out.println("=========================================\n");

                        // --- Ping (ICMP) ---
                        System.out.println("[->] Executing ICMP Ping...");
                        // Minimal: checkHost.ping("8.8.8.8");
                        PingOptions pingOptions = PingOptions.builder()
                                        .region(List.of("us1.node.check-host.cc"))
                                        .repeatchecks(0)
                                        .build();
                        CheckCreated pingTask = checkHost.ping("8.8.8.8", pingOptions);
                        System.out.println("Ping Task UUID: " + pingTask.uuid());
                        Thread.sleep(2000);

                        // --- DNS Propagation ---
                        System.out.println("\n[->] Executing DNS Query (TXT)...");
                        DnsOptions dnsOptions = DnsOptions.builder()
                                        .querymethod("TXT")
                                        .region(List.of("us1.node.check-host.cc"))
                                        .build();
                        CheckCreated dnsTask = checkHost.dns("check-host.cc", dnsOptions);
                        System.out.println("DNS Task UUID: " + dnsTask.uuid());
                        Thread.sleep(2000);

                        // --- TCP Port Check ---
                        System.out.println("\n[->] Executing TCP Handshake on Port 443...");
                        TcpOptions tcpOptions = TcpOptions.builder()
                                        .region(List.of("us1.node.check-host.cc"))
                                        .build();
                        CheckCreated tcpTask = checkHost.tcp("1.1.1.1", 443, tcpOptions);
                        System.out.println("TCP Task UUID: " + tcpTask.uuid());
                        Thread.sleep(2000);

                        // --- UDP Port Check ---
                        System.out.println("\n[->] Executing UDP Packet on Port 53...");
                        UdpOptions udpOptions = UdpOptions.builder()
                                        .region(List.of("us1.node.check-host.cc"))
                                        .build();
                        CheckCreated udpTask = checkHost.udp("1.1.1.1", 53, udpOptions);
                        System.out.println("UDP Task UUID: " + udpTask.uuid());
                        Thread.sleep(2000);

                        // --- HTTP Performance ---
                        System.out.println("\n[->] Executing HTTP/HTTPS Performance Check...");
                        HttpOptions httpOptions = HttpOptions.builder()
                                        .region(List.of("us1.node.check-host.cc"))
                                        .build();
                        CheckCreated httpTask = checkHost.http("https://check-host.cc", httpOptions);
                        System.out.println("HTTP Task UUID: " + httpTask.uuid());
                        Thread.sleep(2000);

                        // --- MTR (My Traceroute) ---
                        System.out.println("\n[->] Executing MTR Diagnostic...");
                        MtrOptions mtrOptions = MtrOptions.builder()
                                        .region(List.of("us1.node.check-host.cc"))
                                        .forceIPversion(4)
                                        .build();
                        CheckCreated mtrTask = checkHost.mtr("1.1.1.1", mtrOptions);
                        System.out.println("MTR Task UUID: " + mtrTask.uuid());

                        System.out.println("\n=========================================");
                        System.out.println("   3. FETCHING THE REPORT RESULTS        ");
                        System.out.println("=========================================\n");

                        // Wait a little extra time for the nodes to process the MTR task
                        System.out.println("[*] Waiting 5 seconds before polling report...");
                        Thread.sleep(5000);

                        // Fetch the compiled MTR report payload using the UUID we just got
                        System.out.println("[->] Fetching Compiled MTR Report for UUID: " + mtrTask.uuid() + "...");
                        JsonNode report = checkHost.report(mtrTask.uuid());
                        System.out.println("\n[Report Head/Preview Data]");
                        System.out.println(report.toPrettyString().substring(0,
                                        Math.min(report.toPrettyString().length(), 200))
                                        + "...\n(Report End truncated for preview)");

                        System.out.println("\n🎉 All library functions executed successfully!");

                } catch (Exception e) {
                        System.err.println("API Error: " + e.getMessage());
                }
        }
}
