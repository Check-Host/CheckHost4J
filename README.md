# CheckHost4J

A lightweight, lightning-fast, and feature-complete Java 17+ wrapper for the [Check-Host.cc](https://check-host.cc) API. Full documentation is available at [docs.check-host.cc](https://docs.check-host.cc).

Seamlessly integrate global network diagnostics into your backend. Perform remote Ping, TCP, UDP, DNS, and HTTP checks from multiple worldwide locations—straight from your Java application. Checks from 60+ locations worldwide.

## Features

- **Minimal Dependencies:** Built purely on the native `java.net.http.HttpClient` (Java 11+). Only requires Jackson for robust JSON parsing.
- **Bulletproof Payloads:** Strictly utilizes POST requests for all active monitoring endpoints. This completely eliminates nasty URL-encoding issues with complex hostnames or custom UDP payloads.
- **Modern & Clean:** Written for Java 17+ with full `record` classes to eliminate boilerplate, ensuring a beautifully typed structure.
- **Smart Authentication:** API Key auto-injection. Configure your key once during initialization, and the core SDK seamlessly handles all authentication payloads under the hood.

## Requirements

- **Java**: 17 or higher
- **Maven**: 3.6+ (for building)

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>cc.checkhost</groupId>
    <artifactId>checkhost4j</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Quickstart

A comprehensive, ready-to-run demonstration containing **every available method and option** is included in the project under `src/main/java/cc/checkhost/Example.java`.

### Basic Usage

```java
package cc.checkhost;

import cc.checkhost.models.CheckCreated;
    public static void main(String[] args) {
        // Initialize the client. The API Key is optional.
        // Without an API key, standard public rate limits apply.
        // CheckHost checkHost = new CheckHost("YOUR_API_KEY_HERE");
        CheckHost checkHost = new CheckHost();

        // Example: Retrieve the public IP of your server
        System.out.println("My IP: " + checkHost.myip());
        
        // Example: Ping exactly one location
        CheckCreated pingNode = checkHost.ping("1.1.1.1");
        System.out.println("Task UUID: " + pingNode.uuid());
    }
}
```

---

## Complete API Reference & Examples

This library supports both minimal invocations and detailed, options-rich requests for every endpoint using the Builder pattern. All failures (network issues, API errors, rate limits) throw a `CheckHostException`.

### Common Options Used in Examples
- `region`: List of Node names (e.g., `List.of("us1.node.check-host.cc")`) or ISO Country Codes (e.g., `List.of("DE", "NL")`) or Continents (e.g., `List.of("EU")`).
- `repeatchecks`: Number of repeated probes to perform per node for higher accuracy (Live Check).
- `timeout`: Connection timeout threshold in seconds.

---

### Information & Utilities

#### Get My IP
Returns the requesting client's public IPv4 or IPv6 address.
```java
String ip = checkHost.myip();
```

#### Get Locations
Fetches a dynamic list of all currently active monitoring nodes across the globe.
```java
JsonNode nodes = checkHost.locations();
```

#### Host Info (GeoIP/ASN)
Retrieves detailed geolocation data, ISP information, and ASN details.
```java
MinResponseINFO info = checkHost.info("check-host.cc");
System.out.println(info.country());
```

#### WHOIS Lookup
Performs a WHOIS registry lookup.
```java
JsonNode whois = checkHost.whois("check-host.cc");
```

---

### Active Monitoring (POST Tasks)

Monitoring endpoints initiate tasks asynchronously and return a `CheckCreated` record containing an `uuid`. Use the `report()` method to fetch the actual results.

#### Ping
Dispatches ICMP echo requests to the target from global nodes.
```java
// Minimal Example
CheckCreated pingMin = checkHost.ping("8.8.8.8");

// Max Example (With options)
PingOptions options = PingOptions.builder()
        .region(List.of("DE", "NL"))
        .repeatchecks(5)
        .timeout(5)
        .build();
CheckCreated pingMax = checkHost.ping("8.8.8.8", options);
```

#### DNS
Queries global nameservers for specific DNS records.
```java
// Minimal Example
CheckCreated dnsMin = checkHost.dns("check-host.cc");

// Max Example (TXT Record)
DnsOptions options = DnsOptions.builder()
        .querymethod("TXT")
        .region(List.of("US", "DE"))
        .build();
CheckCreated dnsMax = checkHost.dns("check-host.cc", options);
```

#### TCP
Attempts to establish a 3-way TCP handshake on a specific destination port.
```java
// Minimal Example (Target, Port)
CheckCreated tcpMin = checkHost.tcp("1.1.1.1", 443);

// Max Example
TcpOptions options = TcpOptions.builder()
        .region(List.of("DE", "NL"))
        .repeatchecks(3)
        .build();
CheckCreated tcpMax = checkHost.tcp("1.1.1.1", 443, options);
```

#### UDP
Sends UDP packets to a specified target and port.
```java
// Minimal Example (Target, Port)
CheckCreated udpMin = checkHost.udp("1.1.1.1", 53);

// Max Example (With hex payload)
UdpOptions options = UdpOptions.builder()
        .payload("0b")
        .region(List.of("EU"))
        .build();
CheckCreated udpMax = checkHost.udp("1.1.1.1", 123, options);
```

#### HTTP
Executes an HTTP/HTTPS request to the target to measure latency.
```java
// Minimal Example
CheckCreated httpMin = checkHost.http("https://check-host.cc");

// Max Example
HttpOptions options = HttpOptions.builder()
        .region(List.of("US", "DE"))
        .repeatchecks(3)
        .build();
CheckCreated httpMax = checkHost.http("https://check-host.cc", options);
```

#### MTR
Initiates an MTR (My Traceroute) diagnostic.
```java
// Minimal Example
CheckCreated mtrMin = checkHost.mtr("1.1.1.1");

// Max Example
MtrOptions options = MtrOptions.builder()
        .repeatchecks(15)
        .forceIPversion(4)
        .forceProtocol("TCP")
        .region(List.of("DE"))
        .build();
CheckCreated mtrMax = checkHost.mtr("1.1.1.1", options);
```

---

### Fetching Results

#### Report
Fetches the compiled report and real-time statuses from a previously initiated monitoring check.
```java
// The check UUID is returned by any monitoring method above
String taskUuid = "c0b4b0e3-aed7-4ae2-9f53-7bac879697cb";

// Fetch the result payload (JsonNode)
JsonNode report = checkHost.report(taskUuid);
System.out.println(report.toPrettyString());
```

## License
MIT License
