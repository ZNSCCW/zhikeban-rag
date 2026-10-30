FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
# ★ alpine 的 JDK 镜像【不带 Maven】——必须自己装（原来的 maven: 镜像自带，所以没这一行）
RUN apk add --no-cache maven
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2/repository mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jdk-alpine AS runtime
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=builder /build/target/*.jar /app/app.jar
EXPOSE 8080
ENV TZ=Asia/Shanghai JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]