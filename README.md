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

Приложение будет доступно по адресу `http://localhost:8080`

### Переменные окружения

При необходимости можно передать переменные окружения при запуске:

```bash
docker run -p 8080:8080 -e PORT=8080 selenide-html-parser
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
```\

## Требования

- Java 17+
- Chrome/Chromium браузер (для Selenide)
- ChromeDriver (Selenide управляет автоматически)

## CI/CD

GitHub Actions автоматически запускает тесты при каждом push в main/master ветку.
