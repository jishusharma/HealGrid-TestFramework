FROM maven:3.9.6-eclipse-temurin-17

RUN apt-get update && apt-get install -y \
    wget \
    gnupg \
    && wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | apt-key add - \
    && echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" \
       >> /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update && apt-get install -y google-chrome-stable \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pom.xml .
COPY src ./src
COPY testNgXmls ./testNgXmls

RUN cp /app/src/main/resources/healenium-docker.properties \
       /app/src/main/resources/healenium.properties

CMD ["mvn", "test", "-Dheadless=true", "-Dhealenium.host=healenium", "-Dexecution=grid", "-Dgrid.url=http://selenium-hub:4444", "-Dsurefire.suiteXmlFiles=testNgXmls/docker.xml"]