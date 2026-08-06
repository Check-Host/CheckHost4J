package cc.checkhost.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response returned by every monitoring endpoint (ping, dns, tcp, udp,
 * http, mtr).
 *
 * <p>{@link #uuid()} is the handle for subsequent {@code report()},
 * {@code ogImage()} and {@code countryMap()} calls.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckCreated(
        Integer status,
        String target,
        String method,
        Integer repeatchecks,
        List<String> region,
        String uuid,
        String reportURL,
        String apiURL,
        @JsonProperty("og-imageURL") String ogImageURL,
        String autodelete,
        String message,
        Integer port,
        String query,
        String payload,
        String success
) {

    /**
     * Whether the API accepted the submission.
     *
     * <p>Handles both the original spec ({@code "success": "success"}) and
     * the current production shape ({@code "success": true}).
     */
    public boolean isSuccess() {
        if (success == null) {
            return false;
        }
        return switch (success.toLowerCase()) {
            case "success", "true", "ok" -> true;
            default -> false;
        };
    }
}
