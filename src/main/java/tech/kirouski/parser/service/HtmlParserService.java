package tech.kirouski.parser.service;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.kirouski.parser.dto.ContactInfo;
import tech.kirouski.parser.exception.HtmlFetchException;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HtmlParserService {

    private static final Logger logger = LoggerFactory.getLogger(HtmlParserService.class);

    public String fetchHtml(String url) throws HtmlFetchException {
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
                    // Умное ожидание готовности страницы (макс 3 сек вместо фиксированных 2 сек)
                    waitForPageLoad(3000);
                    // Удаляем все признаки автоматизации после открытия первой страницы
                    removeAutomationFlags();
                    removeWebDriverFlag();
                    // Минимальная задержка для применения скриптов (0.3 сек вместо 1 сек)
                    waitForScriptsExecution(300);
                } catch (Exception e) {
                    logger.warn("Не удалось открыть главную страницу, продолжаем", e);
                }
            }
            
            // Открываем целевую страницу
            Selenide.open(url);
            
            // Умное ожидание готовности страницы (макс 5 сек вместо фиксированных 3 сек)
            waitForPageLoad(5000);
            
            // Удаляем все признаки автоматизации после открытия целевой страницы
            removeAutomationFlags();
            removeWebDriverFlag();
            
            // Имитируем поведение пользователя - прокрутка страницы
            try {
                var driver = WebDriverRunner.getWebDriver();
                if (driver instanceof JavascriptExecutor) {
                    ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 100);");
                    // Минимальная задержка для анимации прокрутки (0.2 сек вместо 0.5 сек)
                    waitForScrollAnimation(200);
                    ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
                }
            } catch (Exception e) {
                logger.warn("Не удалось выполнить прокрутку", e);
            }
            
            // Умное ожидание завершения динамической загрузки контента (макс 2 сек вместо фиксированных 2 сек)
            waitForDynamicContent(2000);
            
            // Дополнительное ожидание для загрузки контактов и адресов (если они загружаются динамически)
            waitForContactInfo(1500);
            
            // Получаем HTML контент страницы (с контактами и адресами внутри)
            String html = WebDriverRunner.getWebDriver().getPageSource();
            logger.info("HTML успешно получен, размер: {} символов", html.length());
            
            // Проверяем на наличие ошибки 403
            if (is403Error(html)) {
                throw new HtmlFetchException("Доступ к ресурсу запрещен (403 Forbidden)");
            }
            
            // Проверяем размер HTML (должен быть не менее 500 символов)
            if (html == null || html.length() < 500) {
                throw new HtmlFetchException("Размер полученного HTML меньше 500 символов. Возможно, страница не загрузилась полностью или доступ к ресурсу ограничен");
            }
            
            return html;
        } catch (HtmlFetchException e) {
            logger.error("Ошибка при получении HTML с URL: {} - {}", url, e.getMessage());
            throw e;
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
    
    /**
     * Извлекает контактную информацию из HTML
     */
    public ContactInfo extractContactInfo(String html, String url) {
        ContactInfo contactInfo = new ContactInfo();
        contactInfo.setFullHtml(html);
        
        try {
            Document doc = Jsoup.parse(html);
            
            // Извлекаем телефоны
            contactInfo.setPhones(extractPhones(html, doc));
            
            // Извлекаем email
            contactInfo.setEmails(extractEmails(html, doc));
            
            // Извлекаем адреса
            contactInfo.setAddresses(extractAddresses(doc));
            
            // Извлекаем время работы
            contactInfo.setWorkingHours(extractWorkingHours(doc));
            
            logger.info("Извлечена контактная информация: телефоны={}, emails={}, адреса={}, время работы={}",
                    contactInfo.getPhones().size(),
                    contactInfo.getEmails().size(),
                    contactInfo.getAddresses().size(),
                    contactInfo.getWorkingHours() != null ? "найдено" : "не найдено");
            
        } catch (Exception e) {
            logger.error("Ошибка при извлечении контактной информации", e);
        }
        
        return contactInfo;
    }
    
    /**
     * Извлекает телефоны из HTML
     */
    private List<String> extractPhones(String html, Document doc) {
        Set<String> phones = new LinkedHashSet<>();
        
        // Регулярное выражение для телефонов (разные форматы)
        Pattern phonePattern = Pattern.compile(
            "(?:\\+?375|8)?\\s?[-()]?\\s?(?:29|25|33|44|17)\\s?[-()]?\\s?\\d{3}[-()]?\\s?\\d{2}[-()]?\\s?\\d{2}" + // Беларусь
            "|(?:\\+?7|8)?\\s?[-()]?\\s?(?:\\d{3})\\s?[-()]?\\s?\\d{3}[-()]?\\s?\\d{2}[-()]?\\s?\\d{2}" + // Россия
            "|(?:\\+?\\d{1,3})?[-.\\s]?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}" + // Международный формат
            "|\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{4}" // Простой формат
        );
        
        // Поиск в тексте страницы
        Matcher matcher = phonePattern.matcher(html);
        while (matcher.find()) {
            String phone = matcher.group().trim();
            if (phone.length() >= 7) { // Минимальная длина телефона
                phones.add(normalizePhone(phone));
            }
        }
        
        // Поиск в атрибутах href tel:
        Elements telLinks = doc.select("a[href^=tel:]");
        for (Element link : telLinks) {
            String tel = link.attr("href").replace("tel:", "").trim();
            if (!tel.isEmpty()) {
                phones.add(normalizePhone(tel));
            }
        }
        
        // Поиск в тексте элементов с классами, содержащими "phone", "tel", "contact"
        Elements phoneElements = doc.select("*[class*='phone'], *[class*='tel'], *[class*='contact'], *[id*='phone'], *[id*='tel']");
        for (Element element : phoneElements) {
            String text = element.text();
            Matcher textMatcher = phonePattern.matcher(text);
            while (textMatcher.find()) {
                phones.add(normalizePhone(textMatcher.group().trim()));
            }
        }
        
        return new ArrayList<>(phones);
    }
    
    /**
     * Нормализует номер телефона
     */
    private String normalizePhone(String phone) {
        return phone.replaceAll("[^\\d+]", "").replaceAll("^8", "+375");
    }
    
    /**
     * Извлекает email из HTML
     */
    private List<String> extractEmails(String html, Document doc) {
        Set<String> emails = new LinkedHashSet<>();
        
        // Регулярное выражение для email
        Pattern emailPattern = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE
        );
        
        // Поиск в тексте страницы
        Matcher matcher = emailPattern.matcher(html);
        while (matcher.find()) {
            String email = matcher.group().toLowerCase();
            if (!email.contains("@example") && !email.contains("@test")) {
                emails.add(email);
            }
        }
        
        // Поиск в атрибутах href mailto:
        Elements mailLinks = doc.select("a[href^=mailto:]");
        for (Element link : mailLinks) {
            String email = link.attr("href").replace("mailto:", "").split("[?]")[0].trim().toLowerCase();
            if (!email.isEmpty()) {
                emails.add(email);
            }
        }
        
        // Поиск в элементах с классами, содержащими "email", "mail", "contact"
        Elements emailElements = doc.select("*[class*='email'], *[class*='mail'], *[id*='email'], *[id*='mail']");
        for (Element element : emailElements) {
            String text = element.text();
            Matcher textMatcher = emailPattern.matcher(text);
            while (textMatcher.find()) {
                emails.add(textMatcher.group().toLowerCase());
            }
        }
        
        return new ArrayList<>(emails);
    }
    
    /**
     * Извлекает адреса из HTML
     */
    private List<String> extractAddresses(Document doc) {
        Set<String> addresses = new LinkedHashSet<>();
        
        // Поиск в структурированных данных (schema.org)
        Elements addressElements = doc.select("*[itemtype*='PostalAddress'], *[itemprop='address'], address");
        for (Element element : addressElements) {
            String address = element.text().trim();
            if (address.length() > 10) {
                addresses.add(address);
            }
        }
        
        // Поиск в элементах с классами, содержащими "address", "адрес", "location"
        Elements addressClassElements = doc.select(
            "*[class*='address'], *[class*='адрес'], *[class*='location'], " +
            "*[id*='address'], *[id*='адрес'], *[id*='location']"
        );
        for (Element element : addressClassElements) {
            String address = element.text().trim();
            if (address.length() > 10 && !address.contains("@")) { // Исключаем email
                addresses.add(address);
            }
        }
        
        // Поиск в footer и contact sections
        Elements footerElements = doc.select("footer, .footer, .contacts, .contact-info, .address-block");
        for (Element element : footerElements) {
            String text = element.text();
            // Ищем паттерны адресов (улица, дом, город)
            if (text.matches(".*(улица|ул\\.|street|st\\.|проспект|пр\\.|avenue|av\\.).*")) {
                String[] lines = text.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.length() > 15 && line.matches(".*\\d+.*")) { // Содержит цифры
                        addresses.add(line);
                    }
                }
            }
        }
        
        return new ArrayList<>(addresses);
    }
    
    /**
     * Извлекает время работы из HTML
     */
    private String extractWorkingHours(Document doc) {
        // Поиск в структурированных данных (schema.org)
        Elements openingHoursElements = doc.select("*[itemprop='openingHours'], *[itemprop='openingHoursSpecification']");
        if (!openingHoursElements.isEmpty()) {
            return openingHoursElements.first().text().trim();
        }
        
        // Поиск в элементах с классами, содержащими "hours", "time", "расписание", "work"
        Elements hoursElements = doc.select(
            "*[class*='hours'], *[class*='time'], *[class*='расписание'], *[class*='work'], " +
            "*[id*='hours'], *[id*='time'], *[id*='расписание']"
        );
        for (Element element : hoursElements) {
            String text = element.text().trim();
            if (text.toLowerCase().contains("пн") || text.toLowerCase().contains("mon") ||
                text.toLowerCase().contains("вт") || text.toLowerCase().contains("tue") ||
                text.matches(".*\\d{1,2}:\\d{2}.*")) {
                return text;
            }
        }
        
        // Поиск в тексте, содержащем паттерны времени работы
        String bodyText = doc.body().text();
        Pattern hoursPattern = Pattern.compile(
            "(?:пн|пон|mon|monday)[\\s:-]*\\d{1,2}:\\d{2}[\\s-]*\\d{1,2}:\\d{2}.*",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );
        Matcher matcher = hoursPattern.matcher(bodyText);
        if (matcher.find()) {
            String match = matcher.group();
            // Берем первые 200 символов
            return match.length() > 200 ? match.substring(0, 200) + "..." : match;
        }
        
        return null;
    }
    
    /**
     * Умное ожидание готовности страницы (DOM готов + все запросы завершены)
     */
    private void waitForPageLoad(long maxWaitMs) {
        try {
            WebDriver driver = WebDriverRunner.getWebDriver();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(maxWaitMs));
            
            // Ждем готовности DOM
            wait.until((ExpectedCondition<Boolean>) d -> {
                if (!(d instanceof JavascriptExecutor)) {
                    return true;
                }
                JavascriptExecutor js = (JavascriptExecutor) d;
                String readyState = (String) js.executeScript("return document.readyState");
                return "complete".equals(readyState);
            });
            
            // Ждем завершения всех AJAX запросов (если jQuery используется)
            try {
                wait.until((ExpectedCondition<Boolean>) d -> {
                    if (!(d instanceof JavascriptExecutor)) {
                        return true;
                    }
                    JavascriptExecutor js = (JavascriptExecutor) d;
                    try {
                        Long ajaxComplete = (Long) js.executeScript(
                            "return (typeof jQuery !== 'undefined' && jQuery.active === 0) || " +
                            "typeof jQuery === 'undefined'"
                        );
                        return ajaxComplete != null && ajaxComplete == 1;
                    } catch (Exception e) {
                        return true; // Если jQuery не используется, считаем что готово
                    }
                });
            } catch (Exception e) {
                // jQuery может отсутствовать, это нормально
                logger.debug("jQuery не найден, пропускаем проверку AJAX");
            }
            
            logger.debug("Страница загружена (макс. ожидание: {} мс)", maxWaitMs);
        } catch (Exception e) {
            logger.warn("Таймаут ожидания загрузки страницы, продолжаем", e);
        }
    }
    
    /**
     * Ожидание выполнения JavaScript скриптов
     */
    private void waitForScriptsExecution(long maxWaitMs) {
        try {
            Thread.sleep(Math.min(maxWaitMs, 500)); // Максимум 500мс
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Ожидание завершения анимации прокрутки
     */
    private void waitForScrollAnimation(long maxWaitMs) {
        try {
            Thread.sleep(Math.min(maxWaitMs, 300)); // Максимум 300мс
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Умное ожидание загрузки динамического контента
     */
    private void waitForDynamicContent(long maxWaitMs) {
        try {
            WebDriver driver = WebDriverRunner.getWebDriver();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(maxWaitMs));
            
            // Ждем стабилизации количества элементов на странице
            final int[] previousElementCount = {0};
            wait.until((ExpectedCondition<Boolean>) d -> {
                if (!(d instanceof JavascriptExecutor)) {
                    return true;
                }
                JavascriptExecutor js = (JavascriptExecutor) d;
                try {
                    Long currentCount = (Long) js.executeScript(
                        "return document.getElementsByTagName('*').length"
                    );
                    
                    if (currentCount == null) {
                        return true;
                    }
                    
                    // Если количество элементов стабильно 2 проверки подряд - считаем загруженным
                    if (currentCount == previousElementCount[0]) {
                        return true;
                    }
                    
                    previousElementCount[0] = currentCount.intValue();
                    Thread.sleep(100); // Небольшая пауза перед следующей проверкой
                    return false;
                } catch (Exception e) {
                    return true; // При ошибке считаем готовым
                }
            });
            
            logger.debug("Динамический контент загружен");
        } catch (Exception e) {
            logger.debug("Таймаут ожидания динамического контента, продолжаем", e);
        }
    }
    
    /**
     * Ожидание загрузки контактной информации на странице
     */
    private void waitForContactInfo(long maxWaitMs) {
        try {
            WebDriver driver = WebDriverRunner.getWebDriver();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(maxWaitMs));
            
            // Ждем появления контактной информации (телефоны, адреса, email)
            wait.until((ExpectedCondition<Boolean>) d -> {
                if (!(d instanceof JavascriptExecutor)) {
                    return true;
                }
                JavascriptExecutor js = (JavascriptExecutor) d;
                try {
                    // Проверяем наличие элементов с контактной информацией
                    Long contactElements = (Long) js.executeScript(
                        "return document.querySelectorAll(" +
                        "'[class*=\"phone\"], [class*=\"tel\"], [class*=\"contact\"], " +
                        "[class*=\"address\"], [class*=\"адрес\"], [class*=\"location\"], " +
                        "[href^=\"tel:\"], [href^=\"mailto:\"], " +
                        "[itemprop=\"telephone\"], [itemprop=\"address\"], [itemprop=\"email\"]" +
                        ").length"
                    );
                    
                    // Если есть хотя бы один элемент с контактами - считаем готово
                    if (contactElements != null && contactElements > 0) {
                        return true;
                    }
                    
                    // Также проверяем наличие текста, похожего на телефон или адрес
                    String pageText = (String) js.executeScript("return document.body.innerText || ''");
                    if (pageText != null) {
                        // Проверяем наличие паттернов телефонов (беларусь, россия, международные)
                        boolean hasPhone = pageText.matches(".*(\\+?375|\\+?7|8)?\\s?[-()]?\\s?\\d{2,3}\\s?[-()]?\\s?\\d{3}[-()]?\\s?\\d{2}[-()]?\\s?\\d{2}.*") ||
                                         pageText.matches(".*\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{4}.*");
                        
                        // Проверяем наличие адресов (улица, дом, город)
                        boolean hasAddress = pageText.toLowerCase().matches(".*(улица|ул\\.|street|st\\.|проспект|пр\\.|avenue|av\\.|адрес|address).*");
                        
                        // Проверяем наличие email
                        boolean hasEmail = pageText.matches(".*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*");
                        
                        if (hasPhone || hasAddress || hasEmail) {
                            return true;
                        }
                    }
                    
                    return false;
                } catch (Exception e) {
                    return true; // При ошибке считаем готовым
                }
            });
            
            logger.debug("Контактная информация обнаружена на странице");
        } catch (Exception e) {
            logger.debug("Таймаут ожидания контактной информации, продолжаем (возможно, контакты не найдены на странице)", e);
        }
    }
    
    /**
     * Проверяет, является ли HTML ответом с ошибкой 403
     */
    private boolean is403Error(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        
        String lowerHtml = html.toLowerCase();
        // Проверяем наличие признаков ошибки 403
        return lowerHtml.contains("403") && 
               (lowerHtml.contains("forbidden") || 
                lowerHtml.contains("доступ запрещен") || 
                lowerHtml.contains("access denied") ||
                lowerHtml.contains("доступ запрещён"));
    }
}
