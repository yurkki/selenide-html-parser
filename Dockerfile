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
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Устанавливаем Google Chrome и его зависимости (минимальный набор)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    wget \
    gnupg \
    ca-certificates \
    && wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-chrome-keyring.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome-keyring.gpg] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends google-chrome-stable \
    && apt-get purge -y wget gnupg \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/* \
    && rm -rf /tmp/* \
    && rm -rf /var/tmp/*

# Устанавливаем переменные окружения для Chrome
ENV CHROME_BIN=/usr/bin/google-chrome-stable
ENV CHROMEDRIVER_PATH=/usr/bin/chromedriver

# Копируем только собранный JAR
COPY --from=build /app/build/libs/*.jar app.jar

# Открываем порт 8080 (Railway автоматически определит PORT из переменной окружения)
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]
