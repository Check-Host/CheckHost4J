package cc.checkhost;

public class CheckHostException extends RuntimeException {
    private final Integer statusCode;

    public CheckHostException(String message) {
        super(message);
        this.statusCode = null;
    }

    public CheckHostException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public CheckHostException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
