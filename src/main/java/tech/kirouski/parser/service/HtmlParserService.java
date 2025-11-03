package tech.kirouski.parser.service;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HtmlParserService {

    private static final Logger logger = LoggerFactory.getLogger(HtmlParserService.class);

    public String fetchHtml(String url) {
        // Настройка Selenide для работы без видимого браузера
        Configuration.headless = true;
        Configuration.browser = "chrome";
        Configuration.timeout = 10000;
        Configuration.browserSize = "1920x1080";
        
        // Настройка для работы в Docker/Railway
        String chromeBin = System.getenv("CHROME_BIN");
        if (chromeBin != null && !chromeBin.isEmpty()) {
            System.setProperty("webdriver.chrome.binary", chromeBin);
            // Дополнительные опции для headless Chrome в Docker (через систему свойств)
            System.setProperty("chromeoptions.args", "--no-sandbox;--disable-dev-shm-usage;--disable-gpu;--disable-software-rasterizer");
        }
        
        try {
            logger.info("Открываем URL: {}", url);
            // Открываем страницу
            Selenide.open(url);
            // Небольшая задержка для полной загрузки страницы
            Thread.sleep(2000);
            // Получаем HTML контент страницы
            String html = WebDriverRunner.getWebDriver().getPageSource();
            logger.info("HTML успешно получен, размер: {} символов", html.length());
            return html;
        } catch (Exception e) {
            logger.error("Ошибка при получении HTML с URL: {}", url, e);
            throw new RuntimeException("Не удалось получить HTML с URL: " + url, e);
        } finally {
            // Закрываем браузер после получения контента
            try {
                Selenide.closeWebDriver();
            } catch (Exception e) {
                logger.warn("Ошибка при закрытии браузера", e);
            }
        }
    }
}
