package tech.kirouski.parser.dto;

import java.util.List;

public class FetchHtmlRequest {
    private List<String> urls;

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }
}
