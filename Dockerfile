FROM maven:3.8.3-openjdk-17 AS build
WORKDIR /usr/src/app/

COPY ./pom.xml ./pom.xml
RUN --mount=type=cache,id=maven,target=/root/.m2 mvn dependency:go-offline -B

COPY ./src ./src
RUN --mount=type=cache,id=maven,target=/root/.m2 mvn package -DskipTests

FROM azul/zulu-openjdk-alpine:17
COPY --from=build /usr/src/app/target/ari-proxy-1.3.0-fat.jar /usr/app/ari-proxy.jar
ENTRYPOINT ["java","-Dconfig.file=/usr/app/config/ari-proxy.conf","-jar","/usr/app/ari-proxy.jar"]
