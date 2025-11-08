package tech.kirouski.parser.dto;

public class FetchHtmlResponse {
    private String results;
    private String message;

    public FetchHtmlResponse() {
    }

    public FetchHtmlResponse(String results) {
        this.results = results;
    }

    public FetchHtmlResponse(String results, String message) {
        this.results = results;
        this.message = message;
    }

    public String getResults() {
        return results;
    }

    public void setResults(String results) {
        this.results = results;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
