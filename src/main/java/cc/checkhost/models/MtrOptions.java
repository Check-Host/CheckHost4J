package cc.checkhost.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MtrOptions(List<String> region, Integer repeatchecks, Integer forceIPversion, String forceProtocol) {
    public static class Builder {
        private List<String> region;
        private Integer repeatchecks;
        private Integer forceIPversion;
        private String forceProtocol;

        public Builder region(List<String> region) {
            this.region = region;
            return this;
        }

        public Builder repeatchecks(Integer repeatchecks) {
            this.repeatchecks = repeatchecks;
            return this;
        }

        public Builder forceIPversion(Integer forceIPversion) {
            this.forceIPversion = forceIPversion;
            return this;
        }

        public Builder forceProtocol(String forceProtocol) {
            this.forceProtocol = forceProtocol;
            return this;
        }

        public MtrOptions build() {
            return new MtrOptions(region, repeatchecks, forceIPversion, forceProtocol);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
