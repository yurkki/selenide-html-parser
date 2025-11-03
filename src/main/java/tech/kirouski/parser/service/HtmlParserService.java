package tech.kirouski.parser.service;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class HtmlParserService {

    private static final Logger logger = LoggerFactory.getLogger(HtmlParserService.class);

    public String fetchHtml(String url) {
        // Настройка Selenide для работы без видимого браузера
        Configuration.headless = true;
        Configuration.browser = "chrome";
        Configuration.timeout = 10000;
        Configuration.browserSize = "1920x1080";
        
        // Настройка ChromeOptions для работы в Docker/Railway
        ChromeOptions chromeOptions = new ChromeOptions();
        // Обязательные опции для работы в Docker
        chromeOptions.addArguments("--no-sandbox");
        chromeOptions.addArguments("--disable-dev-shm-usage");
        chromeOptions.addArguments("--disable-gpu");
        chromeOptions.addArguments("--disable-software-rasterizer");
        chromeOptions.addArguments("--disable-extensions");
        chromeOptions.addArguments("--remote-allow-origins=*");
        chromeOptions.addArguments("--window-size=1920,1080");
        chromeOptions.addArguments("--disable-setuid-sandbox");
        chromeOptions.addArguments("--disable-web-security");
        chromeOptions.addArguments("--allow-running-insecure-content");
        
        // Устанавливаем User-Agent реального браузера чтобы избежать блокировки
        chromeOptions.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        
        // Отключаем автоматизационные флаги, которые могут выдать бота
        chromeOptions.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        chromeOptions.setExperimentalOption("useAutomationExtension", false);
        
        // Добавляем предпочтения для имитации реального браузера
        chromeOptions.addArguments("--lang=en-US,en");
        chromeOptions.addArguments("--accept-lang=en-US,en");
        
        // Дополнительные предпочтения для обхода защиты
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("profile.managed_default_content_settings.images", 1);
        chromeOptions.setExperimentalOption("prefs", prefs);
        
        // Устанавливаем дополнительные заголовки через preferences
        chromeOptions.setExperimentalOption("excludeSwitches", new String[]{"enable-automation", "enable-logging"});
        chromeOptions.setExperimentalOption("useAutomationExtension", false);
        
        // Устанавливаем путь к Chrome если указан в переменной окружения
        String chromeBin = System.getenv("CHROME_BIN");
        if (chromeBin != null && !chromeBin.isEmpty()) {
            chromeOptions.setBinary(chromeBin);
            System.setProperty("webdriver.chrome.binary", chromeBin);
        }
        
        // Устанавливаем ChromeOptions через Configuration
        Configuration.browserCapabilities = chromeOptions;
        
        try {
            logger.info("Открываем URL: {}", url);
            
            // Получаем базовый домен для предварительной загрузки кук
            String baseUrl = extractBaseUrl(url);
            
            // Сначала открываем главную страницу для получения кук и установки сессии
            if (baseUrl != null && !baseUrl.equals(url)) {
                logger.info("Предварительно открываем главную страницу: {}", baseUrl);
                try {
                    Selenide.open(baseUrl);
                    Thread.sleep(2000);
                    // Удаляем все признаки автоматизации после открытия первой страницы
                    removeAutomationFlags();
                    removeWebDriverFlag();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    logger.warn("Не удалось открыть главную страницу, продолжаем", e);
                }
            }
            
            // Открываем целевую страницу
            Selenide.open(url);
            
            // Ожидаем загрузки страницы
            Thread.sleep(3000);
            
            // Удаляем все признаки автоматизации после открытия целевой страницы
            removeAutomationFlags();
            removeWebDriverFlag();
            
            // Имитируем поведение пользователя - прокрутка страницы
            try {
                var driver = WebDriverRunner.getWebDriver();
                if (driver instanceof JavascriptExecutor) {
                    ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 100);");
                    Thread.sleep(500);
                    ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
                }
            } catch (Exception e) {
                logger.warn("Не удалось выполнить прокрутку", e);
            }
            
            // Дополнительная задержка для полной загрузки контента
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
    
    /**
     * Извлекает базовый URL из полного URL
     */
    private String extractBaseUrl(String url) {
        try {
            int protocolEnd = url.indexOf("://");
            if (protocolEnd == -1) {
                return null;
            }
            int hostEnd = url.indexOf('/', protocolEnd + 3);
            if (hostEnd == -1) {
                return url;
            }
            return url.substring(0, hostEnd);
        } catch (Exception e) {
            logger.warn("Не удалось извлечь базовый URL из: {}", url, e);
            return null;
        }
    }
    
    /**
     * Удаляет webdriver флаг из navigator для избежания детекции бота
     */
    private void removeWebDriverFlag() {
        try {
            var driver = WebDriverRunner.getWebDriver();
            if (driver instanceof JavascriptExecutor) {
                String script = """
                    Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
                    Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});
                    Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});
                    window.chrome = {runtime: {}};
                    Object.defineProperty(navigator, 'permissions', {get: () => ({query: () => Promise.resolve({state: 'granted'})})});
                    """;
                ((JavascriptExecutor) driver).executeScript(script);
            }
        } catch (Exception e) {
            logger.warn("Не удалось удалить webdriver флаг", e);
        }
    }
    
    /**
     * Удаляет признаки автоматизации через JavaScript
     */
    private void removeAutomationFlags() {
        try {
            var driver = WebDriverRunner.getWebDriver();
            if (driver instanceof JavascriptExecutor) {
                // Удаляем все возможные признаки автоматизации
                String script = """
                    // Удаляем webdriver
                    Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
                    
                    // Устанавливаем реалистичные plugins
                    Object.defineProperty(navigator, 'plugins', {
                        get: () => {
                            return [
                                {name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer'},
                                {name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai'},
                                {name: 'Native Client', filename: 'internal-nacl-plugin'}
                            ];
                        }
                    });
                    
                    // Устанавливаем languages
                    Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});
                    
                    // Добавляем chrome объект
                    window.chrome = {
                        runtime: {},
                        loadTimes: function() {},
                        csi: function() {},
                        app: {}
                    };
                    
                    // Устанавливаем permissions
                    const originalQuery = window.navigator.permissions.query;
                    window.navigator.permissions.query = (parameters) => (
                        parameters.name === 'notifications' ?
                            Promise.resolve({ state: Notification.permission }) :
                            originalQuery(parameters)
                    );
                    
                    // Удаляем признаки автоматизации из document
                    Object.defineProperty(document, '$cdc_asdjflasutopfhvcZLmcfl_', {get: () => undefined});
                    Object.defineProperty(document, '$chrome_asyncScriptInfo', {get: () => undefined});
                    
                    // Устанавливаем реалистичные screen свойства
                    Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8});
                    Object.defineProperty(navigator, 'deviceMemory', {get: () => 8});
                    
                    // Удаляем признаки headless
                    Object.defineProperty(navigator, 'maxTouchPoints', {get: () => 0});
                    """;
                ((JavascriptExecutor) driver).executeScript(script);
            }
        } catch (Exception e) {
            logger.warn("Не удалось удалить признаки автоматизации", e);
        }
    }
}
