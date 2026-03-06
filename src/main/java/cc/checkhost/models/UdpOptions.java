package cc.checkhost.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UdpOptions(List<String> region, Integer repeatchecks, Integer timeout, String payload) {
    public static class Builder {
        private List<String> region;
        private Integer repeatchecks;
        private Integer timeout;
        private String payload;

        public Builder region(List<String> region) {
            this.region = region;
            return this;
        }

        public Builder repeatchecks(Integer repeatchecks) {
            this.repeatchecks = repeatchecks;
            return this;
        }

        public Builder timeout(Integer timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder payload(String payload) {
            this.payload = payload;
            return this;
        }

        public UdpOptions build() {
            return new UdpOptions(region, repeatchecks, timeout, payload);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
