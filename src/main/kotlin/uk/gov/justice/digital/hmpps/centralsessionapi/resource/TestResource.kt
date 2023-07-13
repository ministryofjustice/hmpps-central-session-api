package uk.gov.justice.digital.hmpps.centralsessionapi.resource

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Scope
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisOperations
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext.newSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.centralsessionapi.config.ErrorResponse
import kotlin.collections.MutableMap
import kotlin.collections.mutableMapOf
import kotlin.collections.set

@RestController
@RequestMapping("/sessions")
class TestResource(val sessionOps: ReactiveRedisOperations<String, StoredSession>, val requestCounter: RequestCounter) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @GetMapping("/{id}/{appName}")
  suspend fun read(
    @PathVariable id: String,
    @PathVariable appName: String,
  ): Mono<Session> {
    requestCounter.addCount()
    logger.info("[get-${id}-${appName}] Request count: ${requestCounter.count}")
    return sessionOps.opsForValue().get("${id}-${appName}").map {
      Session(SessionPassport(SessionPassportUser(it.tokens[appName], it.username, it.authSource)))
    }.switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND)))
  }

  @PostMapping("/{id}/{appName}")
  suspend fun setSession(
    @PathVariable id: String,
    @PathVariable appName: String,
    @RequestBody session: Session,
  ): Mono<Boolean> {
    requestCounter.addCount()
    logger.info("[set] Request count: ${requestCounter.count}")

    return sessionOps.opsForValue().get("${id}-${appName}").defaultIfEmpty(
      StoredSession(
        mutableMapOf(),
        session.passport?.user?.username,
        session.passport?.user?.authSource,
      ),
    ).map {
      if (session.passport?.user?.token !== null) {
        it.tokens[appName] = session.passport.user.token
      }
      it
    }.flatMap {
      sessionOps.opsForValue().set("${id}-${appName}", it)
    }
  }

  @DeleteMapping("/{id}/{appName}")
  suspend fun delete(
    @PathVariable id: String,
    @PathVariable appName: String,
  ) {
    requestCounter.addCount()
    logger.info("[del] Request count: ${requestCounter.count}")
    sessionOps.delete("${id}-${appName}")
  }

  @ExceptionHandler(ResponseStatusException::class)
  fun handleResponseStatusException(e: ResponseStatusException): ResponseEntity<ErrorResponse> {
    return ResponseEntity.status(e.statusCode)
      .body(
        ErrorResponse(e.statusCode.value(), userMessage = e.message),
      )
  }
}

data class StoredSession(
  @JsonProperty("tokens") val tokens: MutableMap<String, String>,
  @JsonProperty("username") val username: String?,
  @JsonProperty("authSource") val authSource: String?,
)

class SessionPassportUser(
  val token: String?,
  val username: String?,
  val authSource: String?,
)

class SessionPassport(
  val user: SessionPassportUser?,
)

class Session(
  val passport: SessionPassport?,
)

@Configuration
@ConfigurationProperties(prefix = "redis")
class RedisProperties(
  var host: String = "localhost",
  var port: Int = 6379,
  var password: String = "",
  var tlsEnabled: Boolean = false,
)

class RequestCounter {
  public var count = 0;

  public fun addCount() {
    count += 1
  }
}

@Configuration
class SingletonRequestCounterConfig {
  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  public fun singletonRequestCounter(): RequestCounter {
    return RequestCounter()
  }
}

@Configuration
class SessionOps(val redisProperties: RedisProperties) {
  @Bean
  @Primary
  fun reactiveRedisConnectionFactory(): ReactiveRedisConnectionFactory {
    val config = RedisStandaloneConfiguration()
    config.hostName = redisProperties.host
    config.port = redisProperties.port
    config.password = if (redisProperties.password.isEmpty()) {
      RedisPassword.none()
    } else {
      RedisPassword.of(redisProperties.password)
    }

    val clientConfigBuilder = LettuceClientConfiguration.builder()
    if (redisProperties.tlsEnabled) clientConfigBuilder.useSsl()

    return LettuceConnectionFactory(config, clientConfigBuilder.build())
  }

  @Bean
  fun redisOperations(
    connectionFactory: ReactiveRedisConnectionFactory,
  ): ReactiveRedisOperations<String, StoredSession> {
    val serializer = Jackson2JsonRedisSerializer(StoredSession::class.java)
    val builder = newSerializationContext<String, StoredSession>(StringRedisSerializer())
    val context = builder.value(serializer).build()
    return ReactiveRedisTemplate(connectionFactory, context)
  }
}
