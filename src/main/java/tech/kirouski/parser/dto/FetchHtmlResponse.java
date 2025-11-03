package tech.kirouski.parser.dto;

public class FetchHtmlResponse {
    private ContactInfo results;

    public FetchHtmlResponse() {
    }

    public FetchHtmlResponse(ContactInfo results) {
        this.results = results;
    }

    public ContactInfo getResults() {
        return results;
    }

    public void setResults(ContactInfo results) {
        this.results = results;
    }
}
