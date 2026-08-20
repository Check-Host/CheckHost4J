# CheckHost4J

A lightweight, lightning-fast, and feature-complete Java 17+ wrapper for the [Check-Host.cc](https://check-host.cc) API. Full API reference: [check-host.cc/docs](https://check-host.cc/docs). A bundled OpenAPI 3.0.3 / Swagger spec ships at [`swagger.yaml`](./swagger.yaml) for codegen / offline browsing.

Seamlessly integrate global network diagnostics into your backend. Perform remote Ping, MTR, DNS, HTTP, TCP and UDP checks from multiple worldwide locations—straight from your Java application. Checks from 60+ locations worldwide.

## Features

- **Minimal Dependencies:** Built purely on the native `java.net.http.HttpClient` (Java 11+). Only requires Jackson for robust JSON parsing.
- **Bulletproof Payloads:** Strictly utilizes POST requests for all active monitoring endpoints. This completely eliminates nasty URL-encoding issues with complex hostnames or custom UDP payloads.
- **Modern & Clean:** Written for Java 17+ with full `record` classes to eliminate boilerplate, ensuring a beautifully typed structure.
- **Header-Based Authentication:** Configure your token once during initialization; the SDK attaches it as an `Authorization: Bearer` header to every request. The token never lands in a URL or a request body.
- **Network Intelligence & Fullscan:** Passive IP / ASN / prefix / domain / certificate / port / software lookups, plus deep on-demand scans with a built-in polling helper.

## Requirements

- **Java**: 17 or higher
- **Maven**: 3.6+ (for building)

## Installation

Releases are published as tags on this repository and served by
[JitPack](https://jitpack.io), so no extra credentials or repositories are
needed beyond the JitPack one.

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.Check-Host</groupId>
    <artifactId>CheckHost4J</artifactId>
    <version>v1.1.0</version>
</dependency>
```

Gradle:

```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { implementation 'com.github.Check-Host:CheckHost4J:v1.1.0' }
```

Building from source works too — `mvn package` produces
`target/checkhost4j-1.1.0.jar`. Requires **JDK 17+**.

## Quickstart

A comprehensive, ready-to-run demonstration containing **every available method and option** is included in the project under `src/main/java/cc/checkhost/Example.java`.

### Basic Usage

```java
package cc.checkhost;

import cc.checkhost.models.CheckCreated;
    public static void main(String[] args) {
        // Initialize the client. The API token is optional.
        // Without a token, standard public rate limits apply.
        // CheckHost checkHost = new CheckHost("YOUR_API_TOKEN_UUID");
        CheckHost checkHost = new CheckHost();

        // Example: Retrieve the public IP of your server
        System.out.println("My IP: " + checkHost.myip());
        
        // Example: Ping exactly one location
        CheckCreated pingNode = checkHost.ping("1.1.1.1");
        System.out.println("Task UUID: " + pingNode.uuid());
    }
}
```

## Authentication

The token is sent as an `Authorization: Bearer <token>` header on every
request — GET, POST and binary alike. It is never placed in the query string
or the request body, so it does not leak into access logs, referrer headers
or browser history.

```java
CheckHost checkHost = new CheckHost("YOUR_API_TOKEN_UUID");

// Optional second argument overrides the base URL - useful for the
// https://check-host.cc/api mirror when api.check-host.cc is blocked.
CheckHost mirror = new CheckHost("YOUR_API_TOKEN_UUID", "https://check-host.cc/api");
```

> **Migrating from v1.0:** the token used to travel in the JSON body as an
> `apikey` field. That field is deprecated server-side. The constructor is
> positional and unchanged, so `new CheckHost(yourToken)` keeps working as-is.

---

## Complete API Reference & Examples

This library supports both minimal invocations and detailed, options-rich requests for every endpoint using the Builder pattern. All failures (network issues, API errors, rate limits) throw a `CheckHostException`.

### Common Options Used in Examples
- `region`: List of Node names (e.g., `List.of("us1.node.check-host.cc")`) or ISO Country Codes (e.g., `List.of("DE", "NL")`) or Continents (e.g., `List.of("EU")`).
- `repeatchecks`: Number of repeated probes to perform per node for higher accuracy (Live Check).
- `timeout`: Per-check timeout in **milliseconds** (100–30000). Optional; each check type has its own default (ping/tcp 1000, udp 2000, dns 5000, http 15000, mtr 1000). A value below 100 is read as seconds and converted, so older code that passed `timeout: 15` still works — but new code should pass milliseconds.

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

---

### Network Intelligence

Passive lookups against the dataset behind the entity pages — no check is dispatched to the monitoring nodes, so results come back immediately. Every method returns a `JsonNode` because each endpoint's `data` section has a different, open-ended key set; sections we hold no data for come back as empty arrays or `null`.

#### IP Profile
Reverse DNS, open ports and banners, TLS certificates, BGP/ASN attribution, GeoIP, tech-stack, co-hosted domains, origin-leak candidates, threat-intel matches and honeypot activity.
```java
JsonNode intel = checkHost.ipIntel("1.1.1.1");
System.out.println(intel.at("/data/bgp/as_name").asText());   // Cloudflare, Inc.
System.out.println(intel.at("/data/open_ports/0/port").asInt());
```

Honeypot passwords are never returned in cleartext — entries expose only `password_captured` (bool) and `password_len`.

#### ASN Profile
Prefix counts, announced IP totals, peers / providers / customers, IXP memberships, RPKI coverage, GeoIP footprint and hosted-domain summaries. Accepts `"13335"`, `"AS13335"` or the `int` overload.
```java
JsonNode intel = checkHost.asnIntel("AS13335");
System.out.println(intel.at("/data/prefix_count").asInt());
System.out.println(intel.at("/data/rpki_coverage_pct").asDouble());
```

#### Prefix, Domain and Certificate
```java
JsonNode prefix = checkHost.prefixIntel("1.1.1.0", 24);
JsonNode domain = checkHost.domainIntel("check-host.cc");
JsonNode cert   = checkHost.certIntel("3a1b8f0c…9f90");  // 64-char hex fingerprint

System.out.println(prefix.path("cidr").asText());
System.out.println(domain.at("/data/subdomains"));
System.out.println(cert.at("/data/served_by"));
```

#### Port and Software Exposure
```java
JsonNode port = checkHost.portIntel(443);
System.out.println(port.path("well_known").asText() + " " + port.at("/data/open_ips").asLong());

JsonNode nginx  = checkHost.softwareIntel("nginx");            // all versions
JsonNode pinned = checkHost.softwareIntel("nginx", "1.24.0");  // one version
```

---

### Fullscan

A deep, on-demand multi-stage scan (ports + banners + TLS + DNS + threat-intel) of an IP, CIDR, domain or ASN. Asynchronous: submit, poll, then read the results. Budget minutes, not seconds.

```java
JsonNode job = checkHost.fullscan("check-host.cc", CheckHost.SCOPE_DEEP);
String uuid = job.path("uuid").asText();
System.out.println(uuid + " " + job.path("status").asText());   // ... pending

// Block until the job reaches a terminal status (complete/partial/failed)
JsonNode finished = checkHost.waitForFullscan(uuid, 5_000L, 300_000L);
System.out.printf("%s %d/%d%n",
        finished.path("status").asText(),
        finished.path("subjobs_done").asInt(),
        finished.path("subjobs_total").asInt());

JsonNode results = checkHost.fullscanResults(uuid);
for (JsonNode entry : results.at("/data/open_ports")) {
    System.out.println(entry.path("port").asInt() + " " + entry.path("service").asText());
}
```

Scopes: `CheckHost.SCOPE_BASIC` (top-100 ports + banner), `CheckHost.SCOPE_DEEP` (default — full port range, TLS, body and threat-intel), `CheckHost.SCOPE_FULL` (deep plus subdomain enumeration; domains only). Passing `null` selects `deep`.

Anonymous CIDR submissions are capped at `/24` (v4) and `/120` (v6); an API token raises that to `/20` and `/112`.

Before dispatching a scan, check whether a recent one already exists:
```java
JsonNode scans = checkHost.recentScans("check-host.cc");
for (JsonNode prior : scans.path("recent_scans")) {
    if (CheckHost.isFullscanFinished(prior)) {
        JsonNode results = checkHost.fullscanResults(prior.path("uuid").asText());
        break;
    }
}
```

`fullscanStatus(uuid)` returns the job row directly (the `job` envelope is unwrapped for you).

---

## API surface

| Method | Endpoint |
|---|---|
| `myip()` | `GET /myip` |
| `myinfo()` | `GET /myinfo` |
| `locations()` | `GET /locations` |
| `info(target)` | `POST /info` |
| `whois(target)` | `POST /whois` |
| `ping(target[, options])` | `POST /ping` |
| `dns(target[, options])` | `POST /dns` |
| `tcp(target, port[, options])` | `POST /tcp` |
| `udp(target, port[, options])` | `POST /udp` |
| `http(target[, options])` | `POST /http` |
| `mtr(target[, options])` | `POST /mtr` |
| `report(uuid)` | `GET /report/{uuid}` |
| `ogImage(uuid)` | `GET /report/{uuid}/og-image` |
| `countryMap(uuid[, format, resolution])` | `GET /report/{uuid}/country-map` |
| `ipIntel(ip)` | `GET /ip/{ip}` |
| `asnIntel(asn)` | `GET /as/{asn}` |
| `prefixIntel(net, mask)` | `GET /prefix/{net}/{mask}` |
| `domainIntel(domain)` | `GET /domain/{domain}` |
| `certIntel(sha256)` | `GET /cert/{sha256}` |
| `portIntel(port)` | `GET /port/{port}` |
| `softwareIntel(name[, version])` | `GET /software/{name}[/{version}]` |
| `recentScans(target)` | `GET /scan/{target}` |
| `fullscan(target[, scope])` | `POST /fullscan` |
| `fullscanStatus(uuid)` | `GET /fullscan/{uuid}` |
| `fullscanResults(uuid)` | `GET /fullscan/{uuid}/results` |
| `waitForFullscan(uuid[, interval, maxWait])` | polls `GET /fullscan/{uuid}` |

## Development

```bash
mvn test                                              # offline unit tests, no network
mvn test -Dtest.excludedGroups= -Dtest.groups=live    # live smoke tests
```

## License
MIT License
