package tech.kirouski.parser.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.kirouski.parser.dto.FetchHtmlRequest;
import tech.kirouski.parser.dto.FetchHtmlResponse;
import tech.kirouski.parser.exception.HtmlFetchException;
import tech.kirouski.parser.exception.InvalidUrlException;
import tech.kirouski.parser.service.HtmlParserService;

import java.net.MalformedURLException;
import java.net.URL;

@RestController
@RequestMapping("/api")
public class HtmlParserController {

    private final HtmlParserService htmlParserService;

    @Autowired
    public HtmlParserController(HtmlParserService htmlParserService) {
        this.htmlParserService = htmlParserService;
    }

    @PostMapping("/fetch-html")
    public ResponseEntity<FetchHtmlResponse> fetchHtml(@RequestBody FetchHtmlRequest request) {
        if (request == null || request.getUrls() == null || request.getUrls().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new FetchHtmlResponse(null, "Список URL не может быть пустым"));
        }

        try {
            // Берем первую URL из списка
            String url = request.getUrls().get(0);
            
            // Проверяем, что URL не пустой
            if (url == null || url.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new FetchHtmlResponse(null, "URL не может быть пустым"));
            }

            // Валидация URL
            validateUrl(url);

            // Получаем HTML (с контактами и адресами внутри)
            String html = htmlParserService.fetchHtml(url);
            
            // Возвращаем HTML в results
            return ResponseEntity.ok(new FetchHtmlResponse(html));
        } catch (InvalidUrlException e) {
            return ResponseEntity.badRequest()
                    .body(new FetchHtmlResponse(null, "Невалидный URL: " + e.getMessage()));
        } catch (HtmlFetchException e) {
            return ResponseEntity.badRequest()
                    .body(new FetchHtmlResponse(null, e.getMessage()));
        } catch (RuntimeException e) {
            // Обрабатываем RuntimeException, которые могут быть связаны с невалидным URL
            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("Invalid URL") || 
                                         errorMessage.contains("Malformed URL") ||
                                         errorMessage.contains("невалидный URL") ||
                                         errorMessage.contains("Невалидный URL"))) {
                return ResponseEntity.badRequest()
                        .body(new FetchHtmlResponse(null, "Невалидный URL: " + errorMessage));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FetchHtmlResponse(null, "Внутренняя ошибка сервера: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FetchHtmlResponse(null, "Внутренняя ошибка сервера: " + e.getMessage()));
        }
    }
    
    /**
     * Валидирует URL
     */
    private void validateUrl(String url) throws InvalidUrlException {
        if (url == null || url.trim().isEmpty()) {
            throw new InvalidUrlException("URL не может быть пустым");
        }
        
        String trimmedUrl = url.trim();
        
        // Проверяем, что URL содержит протокол и двоеточие
        if (!trimmedUrl.contains("://")) {
            throw new InvalidUrlException("URL должен содержать протокол (http:// или https://)");
        }
        
        // Проверяем, что после протокола есть что-то еще
        String[] parts = trimmedUrl.split("://", 2);
        if (parts.length != 2 || parts[1] == null || parts[1].trim().isEmpty()) {
            throw new InvalidUrlException("URL должен содержать хост после протокола");
        }
        
        try {
            URL urlObj = new URL(trimmedUrl);
            // Проверяем протокол
            String protocol = urlObj.getProtocol();
            if (!protocol.equals("http") && !protocol.equals("https")) {
                throw new InvalidUrlException("URL должен использовать протокол http или https");
            }
            // Проверяем наличие хоста
            if (urlObj.getHost() == null || urlObj.getHost().isEmpty()) {
                throw new InvalidUrlException("URL должен содержать валидный хост");
            }
        } catch (MalformedURLException e) {
            throw new InvalidUrlException("Невалидный формат URL: " + e.getMessage());
        }
    }

    @GetMapping("/health")
    public String health(){
        return "Everything is ok";
    }
}
