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
FROM eclipse-temurin:17-jre
WORKDIR /app

# Устанавливаем зависимости и Google Chrome
RUN apt-get update && \
    apt-get install -y \
    wget \
    gnupg2 \
    ca-certificates \
    fonts-liberation \
    libasound2t64 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libatspi2.0-0 \
    libcups2t64 \
    libdbus-1-3t64 \
    libdrm2t64 \
    libgbm1t64 \
    libgtk-3-0t64 \
    libnspr4t64 \
    libnss3t64 \
    libwayland-client0t64 \
    libxcomposite1t64 \
    libxdamage1t64 \
    libxfixes3t64 \
    libxkbcommon0t64 \
    libxrandr2t64 \
    xdg-utils \
    && wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | apt-key add - \
    && echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update \
    && apt-get install -y google-chrome-stable \
    && rm -rf /var/lib/apt/lists/*

# Устанавливаем переменные окружения для Chrome
ENV CHROME_BIN=/usr/bin/google-chrome-stable
ENV CHROMEDRIVER_PATH=/usr/bin/chromedriver

# Копируем только собранный JAR
COPY --from=build /app/build/libs/*.jar app.jar

# Открываем порт (Railway автоматически определит PORT из переменной окружения)
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]
