package com.example.shoppingassistant.server.ai

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

@Serializable
data class AiCircuitBreakerConfig(
    val enabled: Boolean = true,
    val failureThreshold: Int = 2,
    val cooldownMs: Long = 90_000L,
)

sealed interface AiTargetAvailability {
    data object Available : AiTargetAvailability

    data class OpenCircuit(
        val untilEpochMs: Long,
    ) : AiTargetAvailability
}

data class AiTargetHealthSnapshot(
    val targetKey: String,
    val consecutiveFailures: Int,
    val openUntilEpochMs: Long? = null,
)

interface AiTargetHealthPolicy {
    fun availabilityFor(route: AiAgentRoute, target: AiExecutionTarget): AiTargetAvailability

    fun recordAttempt(route: AiAgentRoute, attempt: AiNormalizationAttempt)

    fun snapshot(route: AiAgentRoute): List<AiTargetHealthSnapshot> = emptyList()
}

object NoopAiTargetHealthPolicy : AiTargetHealthPolicy {
    override fun availabilityFor(route: AiAgentRoute, target: AiExecutionTarget): AiTargetAvailability =
        AiTargetAvailability.Available

    override fun recordAttempt(route: AiAgentRoute, attempt: AiNormalizationAttempt) = Unit
}

class InMemoryAiTargetHealthPolicy(
    private val clock: Clock = Clock.systemUTC(),
) : AiTargetHealthPolicy {

    private val states = ConcurrentHashMap<String, TargetState>()

    override fun availabilityFor(route: AiAgentRoute, target: AiExecutionTarget): AiTargetAvailability {
        if (!route.circuitBreaker.enabled) return AiTargetAvailability.Available
        val key = stateKey(route, target)
        val now = clock.millis()
        val state = states.compute(key) { _, current ->
            current?.takeUnless { it.openUntilEpochMs in 1..now }
        }
        return if (state != null && state.openUntilEpochMs > now) {
            AiTargetAvailability.OpenCircuit(untilEpochMs = state.openUntilEpochMs)
        } else {
            AiTargetAvailability.Available
        }
    }

    override fun recordAttempt(route: AiAgentRoute, attempt: AiNormalizationAttempt) {
        if (!route.circuitBreaker.enabled) return
        if (attempt.outcome == AiNormalizationAttemptOutcome.SKIPPED_OPEN_CIRCUIT) return

        val key = stateKey(route, attempt.target)
        val now = clock.millis()
        val config = route.circuitBreaker
        states.compute(key) { _, current ->
            val activeState = current?.takeUnless { it.openUntilEpochMs in 1..now } ?: TargetState()
            when {
                attempt.outcome == AiNormalizationAttemptOutcome.SUCCESS -> null
                !attempt.affectsCircuitBreaker() -> activeState.takeIf { it.consecutiveFailures > 0 || it.openUntilEpochMs > now }
                else -> {
                    val nextFailures = activeState.consecutiveFailures + 1
                    if (nextFailures >= config.failureThreshold) {
                        TargetState(
                            consecutiveFailures = 0,
                            openUntilEpochMs = now + config.cooldownMs,
                        )
                    } else {
                        TargetState(
                            consecutiveFailures = nextFailures,
                            openUntilEpochMs = 0L,
                        )
                    }
                }
            }
        }
    }

    override fun snapshot(route: AiAgentRoute): List<AiTargetHealthSnapshot> {
        val now = clock.millis()
        return route.targets.map { target ->
            val key = stateKey(route, target)
            val state = states[key]?.takeUnless { it.openUntilEpochMs in 1..now }
            AiTargetHealthSnapshot(
                targetKey = target.key,
                consecutiveFailures = state?.consecutiveFailures ?: 0,
                openUntilEpochMs = state?.openUntilEpochMs?.takeIf { it > now },
            )
        }
    }

    private fun stateKey(route: AiAgentRoute, target: AiExecutionTarget): String =
        buildString {
            append(route.flow.name)
            append(':')
            append(target.key)
            append(':')
            append(target.promptId ?: target.modelUri)
        }

    private data class TargetState(
        val consecutiveFailures: Int = 0,
        val openUntilEpochMs: Long = 0L,
    )
}
