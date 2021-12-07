# Metrics
Ari-proxy provides service specific metrics using the [micrometer framework](http://micrometer.io) which are available via JMX or HTTP.
Both ways provide the same data.

Metrics via HTTP are in Prometheus format and are provided via the route `/metrics` on the port configured by `service.httpport` in the service config file.


## Meter descriptions

* **ariproxy.persistence.WriteTime**: Measures the time it takes to persist data in the persistence store backend.
* **ariproxy.cache.Misses**: Increases every time a lookup for call context in local cache fails and it has to be retrieved from the persistence backend
* **ariproxy.cache.AccessAttempts**: Counts how often the local cache is called to lookup a call context.
* **ariproxy.errors.CacheErrors**: How often a call context lookup failed
* **ariproxy.errors.CommandResponseProcessorRestarts**: Increases every time the command processing stream restarts because of errors.
* **ariproxy.errors.EventProcessorRestarts**: Increases every time the ari event processing stream restarts because of errors.
* **ariproxy.errors.PersistenceStoreReadErrors**: errors when trying to read from persistence store backend

## ARI Event Counters
All ARI events are counted and exported as `ariproxy.events` tagged by `eventType`, this includes for example:
* DIAL
* STASIS_START
* PLAYBACK_STARTED
* CHANNEL_HANGUP_REQUEST
* ...
