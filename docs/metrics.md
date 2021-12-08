# Metrics
Ari-proxy provides service specific metrics using the [micrometer framework](http://micrometer.io) which are available via JMX or HTTP.
Both ways provide the same data.

Metrics via HTTP are in Prometheus format and are provided via the route `/metrics` on the port configured by `service.httpport` in the service config file.


## Meter descriptions

* **ari-proxy.persistence.write.duration**: Measures the time it takes to persist data in the persistence store backend.
* **ari-proxy.cache.read.misses**: Increases every time a lookup for call context in local cache fails and it has to be retrieved from the persistence backend
* **ari-proxy.cache.read.attempts**: Counts how often the local cache is called to lookup a call context.
* **ari-proxy.cache.read.errors**: How often a call context lookup failed
* **ari-proxy.processor.restarts**: Increases every time the command or event processing stream restarts because of errors.
* **ari-proxy.persistence.read.errors**: Errors when trying to read from persistence store
* **ari-proxy.persistence.write.duration**: Duration of writing to persistence store
* **ari-proxy.events**: Tagged by `eventType`. Counts ARI events of the respective type.

## ARI Event Counters

All ARI events are counted and exported as `ariproxy.events` tagged by `eventType`, this includes for example:
* DIAL
* STASIS_START
* PLAYBACK_STARTED
* CHANNEL_HANGUP_REQUEST
* ...
