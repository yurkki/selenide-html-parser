# Selenide HTML Parser

REST API сервис для парсинга HTML контента веб-сайтов с использованием Selenide.

## Локальный запуск

```bash
./gradlew bootRun
```

## Запуск в Docker

### Сборка образа

```bash
docker build -t selenide-html-parser .
```

### Запуск контейнера

```bash
docker run -p 8080:8080 selenide-html-parser
```

## API

### POST /api/fetch-html

Получает HTML контент указанного сайта.

**Запрос:**
```json
{
  "urls": ["https://example.com"]
}
```

**Ответ:**
```json
{
  "result": "<html>...</html>"
}
```

## Требования

- Java 17+
- Chrome/Chromium браузер (для Selenide)
- ChromeDriver (Selenide управляет автоматически)
