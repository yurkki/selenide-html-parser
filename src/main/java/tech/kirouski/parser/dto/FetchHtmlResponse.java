package tech.kirouski.parser.dto;

public class FetchHtmlResponse {
    private String result;

    public FetchHtmlResponse(String result) {
        this.result = result;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
