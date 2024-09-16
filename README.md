# ari-proxy
Ari-proxy connects [Asterisk](https://www.asterisk.org/), an open source communication server, to the [Apache Kafka](https://kafka.apache.org/intro) distributed streaming platform.

## Table of contents
1. [Abstract](#abstract)
2. [Getting started](#getting-started)
3. [Metrics](#metrics)
4. [Compatibility](#compatibility)
5. [Contributing & feedback](#contributing--feedback)
6. [Credit & License](#credit--license)
7. [Acknowledgements](#acknowledgements)

## Abstract
The motivation to create ari-proxy arose from the need to build distributed and resilient telephony services scaling up to millions of active users. Ari-proxy makes use of Kafka’s built-in routing concepts to ensure consistency of message streams and proper dispatching to the *call-controller*, the application implementing the service logic.

![Architecture Overview](docs/images/architecture_overview.png "Architecture Overview")

Please see [docs/concepts.md](/public/docs/concepts.md) for details on the concepts of message routing and session mapping.

## Getting started
In order to operate ari-proxy, make sure you have a running instance of both Asterisk and Kafka server.

#### Prerequisites
ari-proxy is written in Java, so you should [install and setup Java](https://www.java.com/en/download/help/download_options.xml) before continuing.
The project is managed by maven, which requires you to [install maven](https://maven.apache.org/install.html) as well.

#### Building
Build the fat jar in `target/`:
```bash
mvn package
```

#### Configuration
ari-proxy expects the following configuration files, which should be passed to the jvm when running the fat-jar:

| config               | optional | purpose                                                                                                                      |
| -------------------- | -------- | ---------------------------------------------------------------------------------------------------------------------------- |
| service.conf         | no       | configure the service, see our template: [service.conf.sample](service.conf.sample)                                          |
| log4j2.xml           | yes      | configure logging (if not specified, [a bundled config](/src/main/resources/log4j2.xml) will be used logging to STDOUT only) |
| jolokia.properties   | no       | configure jolokia agent properties, see our template: [jolokia.properties.sample](jolokia.properties.sample)                 |

#### Running
Run the fat jar:
```bash
java -Dconfig.file=/path/to/service.conf [-Dlog4j.configurationFile=/path/to/log4j2.xml] -jar target/ari-proxy-1.3.0-fat.jar
```

#### Persistence-store

There are two ways to persist the in-memory data storage (Asterisk Object ID -> Kafka Routing Key).

First there is Redis (default). The redis needs to be configured in service.conf file in order to be able to connect. Also a keyspace needs to be configured.
Second possibility to store the data is Cassandra. This is a more robust but more complex configuration as it provides HA and better scalability. You need to configure the Cassandra nodes in the "datastax" section of "service.conf"

In order to choose one option you need to enable the one or the other backend by set the parameter `persistence-store` to one of these values:

- "io.retel.ariproxy.persistence.plugin.CassandraPersistenceStore"

- "io.retel.ariproxy.persistence.plugin.RedisPersistenceStore"


In case you want to use Cassandra you need to create a keyspace and table in Cassandra. This snippet might help to create one. (please adapt replication factor and names according your setup)

```cql
CREATE KEYSPACE retel with replication = {'class':'SimpleStrategy','replication_factor':1};

USE retel;

CREATE TABLE retel (
"key" text primary key,
"value" text
);
```

Hint: do not forget some kind of housekeeping by adding TTL or a cleanup job.

## Monitoring
### Health-Check
There are three routes to check for service health:
 - `/health`
 - `/health/smoke`
 - `/health/backing-services`

`/health` is meant to check if the service itself is healthy in terms of JVM,Akka etc. settings. Yet to be implemented correctly.

`/health/smoke` is used for deployment to check if the service is started. Only checks for open port.

`/health/backing-services` checks all services that are necessary for running the application e.g. Kafka, Asterisk, Redis/Cassandra.

### Metrics
Ari-proxy provides service specific metrics using the [micrometer framework](http://micrometer.io) which are available via JMX or HTTP.

For further details see: [Metrics](docs/metrics.md)


## Compatibility
We aim for compatibility with the latest stable release of
- [Java OpenJDK 17](https://openjdk.java.net/projects/jdk/17/)
- [Asterisk](https://wiki.asterisk.org/wiki/display/AST/Asterisk+Versions)
- the utilized [Akka Modules](https://akka.io/docs/)

## Contributing & feedback
To report a bug or make a request for new features, use the [Issues Page](https://github.com/retel-io/ari-proxy/issues) in the ari-proxy Github project.
We welcome any contributions. Please see the [Developer Guidelines](/public/CONTRIBUTING.md) for instructions on how to submit changes.

## Credit & License
ari-proxy is maintained by the folks at [sipgate](https://www.sipgate.de) and licensed under the terms of the [AGPL license](/public/LICENSE.txt).

Maintainers of this repository:

- Jöran [@vinzens](https://github.com/vinzens)
- Maya [@ironmaya](https://github.com/ironmaya)
- Mia [@mia-krause](https://github.com/mia-krause)
- Sven [@SvenKube](https://github.com/SvenKube)
- Max [@IllTemperedMax](https://github.com/IllTemperedMax)

Please refer to the Git commit log for a complete list of contributors.

## Acknowledgements
ari-proxy is not the first of its kind. This project was inspired by the concepts underlying both [go-ari-proxy by N-Visible](https://github.com/nvisibleinc/go-ari-proxy) as well as [ari-proxy by CyCore Systems](https://github.com/CyCoreSystems/ari-proxy).
