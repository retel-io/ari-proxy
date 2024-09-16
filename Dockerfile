FROM openjdk:22-jdk-slim AS build

ARG MAVEN_VERSION=3.9.6
RUN apt-get update \
    && apt-get install -y wget \
    && wget https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    && tar -xvzf apache-maven-${MAVEN_VERSION}-bin.tar.gz -C /opt \
    && ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/bin/mvn \
    && rm apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    && apt-get clean

WORKDIR /usr/src/app/

COPY ./pom.xml ./pom.xml
RUN --mount=type=cache,id=maven,target=/root/.m2 mvn dependency:go-offline -B

COPY ./src ./src
RUN --mount=type=cache,id=maven,target=/root/.m2 mvn package -DskipTests

FROM azul/zulu-openjdk-alpine:22
COPY --from=build /usr/src/app/target/ari-proxy-1.3.0-fat.jar /usr/app/ari-proxy.jar
ENTRYPOINT ["java","-Dconfig.file=/usr/app/config/ari-proxy.conf","-jar","/usr/app/ari-proxy.jar"]
