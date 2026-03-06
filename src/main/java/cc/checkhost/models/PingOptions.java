package cc.checkhost.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PingOptions(List<String> region, Integer repeatchecks, Integer timeout) {
    public static class Builder {
        private List<String> region;
        private Integer repeatchecks;
        private Integer timeout;

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

        public PingOptions build() {
            return new PingOptions(region, repeatchecks, timeout);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
