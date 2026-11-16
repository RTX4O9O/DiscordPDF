# syntax=docker/dockerfile:1

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-noble

# LibreOffice (headless, no GUI) for Office <-> PDF, plus fonts:
#   DejaVu        Latin / Greek / Cyrillic
#   Droid Fallback CJK as TrueType (embeddable by PDFBox, used by Markdown -> PDF)
#   Noto CJK      CJK for LibreOffice conversions
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      libreoffice-writer-nogui libreoffice-impress-nogui \
      fonts-dejavu-core fonts-droid-fallback fonts-noto-cjk \
 && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin discordpdf
WORKDIR /app
COPY --from=build /src/target/discordpdf.jar /app/discordpdf.jar

# All scratch data lives under /tmp, which docker-compose mounts as a RAM-only tmpfs.
ENV WORK_DIR=/tmp/discordpdf \
    HOME=/tmp \
    XDG_CACHE_HOME=/tmp/.cache \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"

USER 10001
ENTRYPOINT ["java", "-jar", "/app/discordpdf.jar"]
