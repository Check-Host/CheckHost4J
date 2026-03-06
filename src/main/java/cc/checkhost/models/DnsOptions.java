package cc.checkhost.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DnsOptions(String querymethod, List<String> region) {
    public static class Builder {
        private String querymethod;
        private List<String> region;

        public Builder querymethod(String querymethod) {
            this.querymethod = querymethod;
            return this;
        }

        public Builder region(List<String> region) {
            this.region = region;
            return this;
        }

        public DnsOptions build() {
            return new DnsOptions(querymethod, region);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
