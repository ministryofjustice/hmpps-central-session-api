package uk.gov.justice.digital.hmpps.hmppscentralsessionapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication()
class HmppsCentralSessionApi

fun main(args: Array<String>) {
  runApplication<HmppsCentralSessionApi>(*args)
}
