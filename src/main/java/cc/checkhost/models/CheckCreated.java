package cc.checkhost.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckCreated(
        Integer status,
        String target,
        String method,
        Integer repeatchecks,
        String uuid,
        String reportURL,
        String apiURL,
        String autodelete,
        String message,
        String success
) {
}
