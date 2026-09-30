# Image for one converted CardDemo service, built from a clean clone.
#
# The jar is built in the first stage, so nothing has to be produced on a developer's machine first and
# the image is reproducible from the checkout alone. Only the jar reaches the runtime stage, which
# carries a JRE, no build tooling and an unprivileged user. The build context is modernization/, since
# the reactor pom and the shared modules are what the module builds against.
#
# The local Maven repository is a BuildKit cache mount, so the six service images share one download
# instead of each fetching the same dependencies. MAVEN_MIRROR_URL points the build at a mirror of
# Maven Central for networks that cannot reach or are rate-limited by repo.maven.apache.org; left
# empty, the build goes to Central directly.
FROM maven:3.9-eclipse-temurin-21 AS build

ARG MAVEN_MIRROR_URL=""
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2/repository \
    if [ -n "$MAVEN_MIRROR_URL" ]; then \
      printf '<settings><mirrors><mirror><id>mirror</id><url>%s</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>' \
        "$MAVEN_MIRROR_URL" > /root/.m2/settings.xml; \
    fi && \
    mvn -B -pl transaction-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S carddemo && adduser -S -G carddemo carddemo
WORKDIR /app
COPY --from=build /src/transaction-service/target/carddemo-transaction-service.jar /app/app.jar
USER carddemo

EXPOSE 8083

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
