package tech.kirouski.parser.dto;

public class FetchHtmlResponse {
    private String results;

    public FetchHtmlResponse(String results) {
        this.results = results;
    }

    public String getResults() {
        return results;
    }

    public void setResults(String results) {
        this.results = results;
    }
}
