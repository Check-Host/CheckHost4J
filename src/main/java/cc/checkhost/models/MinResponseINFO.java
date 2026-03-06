package cc.checkhost.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MinResponseINFO(
        String ip,
        String reverse,
        String iprange,
        String country,
        String city,
        String zipcode
) {}
