# Metrics
Ari-proxy provides service specific metrics using the [micrometer framework](http://micrometer.io) which are available via JMX or HTTP.
Both ways provide the same data.

Metrics via HTTP are in Prometheus format and are provided via the route `/metrics` on the port configured by `service.httpport` in the service config file.


## Meter descriptions

* **CallsStarted**: Increases for every `StasisStart` event
* **CallsEnded**: Increases for every `StasisEnd` event
* **CallSetupDelay**: Measures the duration between a `StasisStart` event and the first response to an ari-command
* **PersistenceUpdateDelay**: Measures the time it takes to persist data in the persistence store backend.
* **CacheMisses**: Increases every time a lookup for call context in local cache fails and it has to be retrieved from the persistence backend

## ARI Event Counters
All ARI events are counted and exported, this includes for example:
* DIAL
* STASIS_START
* PLAYBACK_STARTED
* CHANNEL_HANGUP_REQUEST
* ...
