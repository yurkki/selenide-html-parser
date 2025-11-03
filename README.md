# Selenide HTML Parser

REST API сервис для парсинга HTML контента веб-сайтов с использованием Selenide.

## Локальный запуск

```bash
./gradlew bootRun
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

## Деплой на Railway

1. Создайте аккаунт на [Railway.app](https://railway.app)
2. Создайте новый проект
3. Подключите ваш Git репозиторий
4. Railway автоматически обнаружит Dockerfile и задеплоит приложение
5. Приложение будет доступно по адресу, который предоставит Railway

### Переменные окружения

Railway автоматически устанавливает переменную `PORT`, приложение использует её для прослушивания порта.

## Требования

- Java 17+
- Chrome/Chromium браузер (для Selenide)
- ChromeDriver (Selenide управляет автоматически)

## CI/CD

GitHub Actions автоматически запускает тесты при каждом push в main/master ветку.
