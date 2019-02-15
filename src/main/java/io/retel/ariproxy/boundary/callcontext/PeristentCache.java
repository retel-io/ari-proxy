package io.retel.ariproxy.boundary.callcontext;

import static io.vavr.API.Some;

import akka.actor.AbstractLoggingActor;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;
import java.io.Serializable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public abstract class PeristentCache extends AbstractLoggingActor {

	class SetDone implements Serializable {

		private final String key;
		private final String value;

		public SetDone(String key, String value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
		}
	}

	private PersistenceStore persistenceStore = null;

	private LoadingCache<String, Future<Option<String>>> cache = CacheBuilder.newBuilder()
			.expireAfterWrite(6, TimeUnit.HOURS)
			.build(CacheLoader.from(key -> persistenceStore.get(key)));

	abstract protected String keyPrefix();

	protected Future<SetDone> update(String key, String value) {
		String prefixedKey = keyPrefix() + ":" + key;
		cache.put(prefixedKey, Future.successful(Some(value)));
		return persistenceStore.set(prefixedKey, value).map(v -> new SetDone(prefixedKey, value);
	}

	protected Future<Option<String>> query(String key) throws ExecutionException {
		String prefixedKey = keyPrefix() + ":" + key;
		return cache.get(prefixedKey);
	}

	@Override
	public void preStart() throws Exception {
		super.preStart();
		persistenceStore = providePersistenceStore();
	}

	@Override
	public void postStop() throws Exception {
		super.postStop();
		persistenceStore.shutdown();
	}
}

//object PersistentCache {
//
//  val config: EngineConfig = EngineConfigLoader.load()
//}
//
//abstract class PersistentCache[K, V](var metricsService: ActorRef = null) extends Actor with ActorLogging {
//  Actor =>
//
//  private def providePersistenceStore(): PersistenceStore = {
//    Try(Class.forName(config.getPersistenceStore))
//      .flatMap(clazz => Try(clazz.getMethod("create")))
//      .flatMap(method => Try(method.invoke(null).asInstanceOf[PersistenceStore])) match {
//      case Success(store) =>
//        store
//      case Failure(e) =>
//        throw new RuntimeException("Failed to load any PersistenceStore", e)
//    }
//  }
//}