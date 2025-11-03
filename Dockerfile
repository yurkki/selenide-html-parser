# Многоэтапная сборка для уменьшения размера образа
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Копируем только файлы конфигурации Gradle
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Копируем исходный код
COPY src ./src

# Собираем приложение
RUN gradle build -x test --no-daemon

# Финальный образ с поддержкой Chrome для Selenide
FROM openjdk:17-jre-slim
WORKDIR /app

# Устанавливаем Chromium и зависимости
RUN apt-get update && \
    apt-get install -y \
    chromium \
    chromium-driver \
    fonts-liberation \
    libasound2 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libatspi2.0-0 \
    libcups2 \
    libdbus-1-3 \
    libdrm2 \
    libgbm1 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libwayland-client0 \
    libxcomposite1 \
    libxdamage1 \
    libxfixes3 \
    libxkbcommon0 \
    libxrandr2 \
    xdg-utils \
    && rm -rf /var/lib/apt/lists/*

# Устанавливаем переменную окружения для Chrome
ENV CHROME_BIN=/usr/bin/chromium
ENV CHROMEDRIVER_PATH=/usr/bin/chromedriver

# Копируем только собранный JAR
COPY --from=build /app/build/libs/*.jar app.jar

# Открываем порт (Railway автоматически определит PORT из переменной окружения)
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]
