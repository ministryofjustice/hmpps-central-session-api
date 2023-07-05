package uk.gov.justice.digital.hmpps.centralsessionapi.resource

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import kotlin.collections.MutableMap
import kotlin.collections.mutableMapOf
import kotlin.collections.set

@RestController
@RequestMapping("/sessions")
class TestResource(val sessionOps: ReactiveRedisOperations<String, StoredSession>) {
  val logger = LoggerFactory.getLogger(TestResource::class.java)

  @GetMapping("/{id}/{appName}")
  suspend fun read(
    @PathVariable id: String,
    @PathVariable appName: String,
  ): Session {
    val storedSession = sessionOps.opsForValue().get(id).block()
    if (storedSession != null) {
      return Session(
        storedSession.cookie,
        SessionPassport(
          SessionPassportUser(
            storedSession.tokens[appName],
            storedSession.username,
            storedSession.authSource,
          ),
        ),
      )
    }

    throw ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "entity not found",
    )
  }

  @PostMapping("/{id}/{appName}")
  suspend fun setSession(
    @PathVariable id: String,
    @PathVariable appName: String,
    @RequestBody session: Session,
  ): Mono<Boolean> {
    logger.info(session.cookie?.expires ?: "MISSING EXPIRY")
    logger.info(session.passport?.user?.token ?: "MISSING TOKEN")
    val storedSession: StoredSession = sessionOps.opsForValue().get(id).block() ?: StoredSession(
      session.cookie,
      mutableMapOf(),
      session.passport?.user?.username,
      session.passport?.user?.authSource,
    )

    if (session.passport?.user?.token != null) {
      storedSession.tokens[appName] = session.passport.user.token
    }

    return sessionOps.opsForValue().set(id, storedSession)
  }

  @DeleteMapping("/{id}/{appName}")
  suspend fun delete(
    @PathVariable id: String,
    @PathVariable appName: String,
  ) = sessionOps.delete(id)
}

class SessionCookie(
  @JsonProperty("originalMaxAge") val originalMaxAge: Number,
  @JsonProperty("expires") val expires: String,
  @JsonProperty("secure") val secure: Boolean,
  @JsonProperty("httpOnly") val httpOnly: Boolean,
  @JsonProperty("path") val path: String,
  @JsonProperty("sameSite") val sameSite: String,
)

class StoredSession(
  @JsonProperty("cookie") val cookie: SessionCookie?,
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
  val cookie: SessionCookie?,
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
