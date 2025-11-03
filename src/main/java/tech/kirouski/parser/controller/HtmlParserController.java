package tech.kirouski.parser.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.kirouski.parser.dto.ContactInfo;
import tech.kirouski.parser.dto.FetchHtmlRequest;
import tech.kirouski.parser.dto.FetchHtmlResponse;
import tech.kirouski.parser.service.HtmlParserService;

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
            return ResponseEntity.badRequest().build();
        }

        try {
            // Берем первую URL из списка
            String url = request.getUrls().get(0);
            
            // Проверяем, что URL не пустой
            if (url == null || url.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Получаем HTML
            String html = htmlParserService.fetchHtml(url);
            
            // Извлекаем контактную информацию
            ContactInfo contactInfo = htmlParserService.extractContactInfo(html, url);
            
            // Возвращаем структурированные данные
            return ResponseEntity.ok(new FetchHtmlResponse(contactInfo));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/health")
    public String health(){
        return "Everything is ok";
    }
}
