package cc.checkhost.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Geolocation / ASN / privacy / abuse record returned by
 * {@code POST /info}, {@code GET /info/{target}} and {@code GET /myinfo}.
 *
 * <p>Aligned with Swagger 2.1.0. Fields the API omits decode as
 * {@code null}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MinResponseINFO(
        String ip,
        String reverse,
        String country,
        @JsonProperty("countryCode") String countryCode,
        @JsonProperty("isEu") Boolean isEu,
        String city,
        String continent,
        Double latitude,
        Double longitude,
        @JsonProperty("timeZone") String timeZone,
        @JsonProperty("postalCode") String postalCode,
        String subdivision,
        @JsonProperty("currencyCode") String currencyCode,
        @JsonProperty("callingCode") String callingCode,
        Privacy privacy,
        AsnInfo asn,
        Company company,
        Abuse abuse,
        Boolean success
) {

    /** Privacy classification of the address. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Privacy(
            @JsonProperty("isAbuser") Boolean isAbuser,
            @JsonProperty("isAnonymous") Boolean isAnonymous,
            @JsonProperty("isBogon") Boolean isBogon,
            @JsonProperty("isHosting") Boolean isHosting,
            @JsonProperty("isIcloudRelay") Boolean isIcloudRelay,
            @JsonProperty("isProxy") Boolean isProxy,
            @JsonProperty("isTor") Boolean isTor,
            @JsonProperty("isVpn") Boolean isVpn
    ) {}

    /** BGP / RIR attribution for the address. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AsnInfo(
            String asn,
            String route,
            String netname,
            String name,
            @JsonProperty("countryCode") String countryCode,
            String domain,
            String type,
            String rir
    ) {}

    /** Organisation the address is registered to. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Company(
            String name,
            String domain,
            @JsonProperty("countryCode") String countryCode,
            String type
    ) {}

    /** Abuse contact for the network. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Abuse(
            String address,
            @JsonProperty("countryCode") String countryCode,
            String email,
            String name,
            String network,
            String phone
    ) {}

    /**
     * Alias for {@link #postalCode()}.
     *
     * @deprecated the API renamed this field to {@code postalCode} in
     *             Swagger 2.0; use {@link #postalCode()}.
     */
    @Deprecated
    public String zipcode() {
        return postalCode;
    }

    /**
     * Always {@code null}: the API stopped returning an IP range with
     * {@code /info} in Swagger 2.0.
     *
     * @deprecated use {@link AsnInfo#route()} from {@link #asn()} instead.
     */
    @Deprecated
    public String iprange() {
        return null;
    }
}
