package com.viva.voice.agent

import com.viva.voice.intent.Intent

/**
 * Optional slow-path language planner.
 *
 * Implementations may call an on-device or remote model, but they only return
 * data. They never receive a [CommandGateway], vehicle repository, VHAL ID, or
 * any other execution capability.
 */
fun interface AgentPlanner {
    suspend fun plan(text: String, traceId: String): AgentPlanResult
}

sealed interface AgentPlanResult {
    /** A normalized proposal that must still pass through CommandGateway and Body SafetyGuard. */
    data class Action(val intent: Intent) : AgentPlanResult

    data class Actions(val intents: List<Intent>) : AgentPlanResult {
        init {
            require(intents.size in 2..MAX_ACTIONS) {
                "An agent plan needs between 2 and $MAX_ACTIONS actions"
            }
        }
    }

    data class Clarification(val promptVi: String) : AgentPlanResult {
        init {
            require(promptVi.isNotBlank()) { "Clarification prompt must not be blank" }
        }
    }

    data class Unsupported(val promptVi: String) : AgentPlanResult {
        init {
            require(promptVi.isNotBlank()) { "Unsupported prompt must not be blank" }
        }
    }

    /** Network, model, timeout, disabled feature, or invalid model output. */
    data class Unavailable(val diagnostic: String) : AgentPlanResult

    companion object {
        const val MAX_ACTIONS = 3
    }
}
