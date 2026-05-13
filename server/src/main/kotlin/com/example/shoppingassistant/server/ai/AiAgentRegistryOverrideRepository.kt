package com.example.shoppingassistant.server.ai

import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

data class AiAgentRegistryPersistentOverride(
    val flow: AiNormalizationFlow,
    val enabled: Boolean,
    val routeVersion: String,
    val contractName: String,
    val contractVersion: String,
    val killSwitchReason: String? = null,
    val updatedAtMs: Long,
    val updatedBy: String? = null,
    val note: String? = null,
)

object AiAgentRegistryOverridesTable : Table("ai_agent_registry_overrides") {
    val flow = varchar("flow", 64)
    val enabled = bool("enabled")
    val routeVersion = varchar("route_version", 128)
    val contractName = varchar("contract_name", 128)
    val contractVersion = varchar("contract_version", 128)
    val killSwitchReason = text("kill_switch_reason").nullable()
    val updatedAtMs = long("updated_at_ms")
    val updatedBy = varchar("updated_by", 255).nullable()
    val note = text("note").nullable()

    override val primaryKey = PrimaryKey(flow)
}

interface AiAgentRegistryOverrideRepository {
    suspend fun listAll(): List<AiAgentRegistryPersistentOverride>

    suspend fun upsert(override: AiAgentRegistryPersistentOverride): AiAgentRegistryPersistentOverride

    suspend fun delete(flow: AiNormalizationFlow): Boolean
}

class DatabaseAiAgentRegistryOverrideRepository : AiAgentRegistryOverrideRepository {
    override suspend fun listAll(): List<AiAgentRegistryPersistentOverride> = DatabaseFactory.dbQuery {
        AiAgentRegistryOverridesTable
            .selectAll()
            .map { it.toPersistentOverride() }
            .sortedBy { it.flow.name }
    }

    override suspend fun upsert(
        override: AiAgentRegistryPersistentOverride,
    ): AiAgentRegistryPersistentOverride = DatabaseFactory.dbQuery {
        val existing = AiAgentRegistryOverridesTable
            .selectAll()
            .where { AiAgentRegistryOverridesTable.flow eq override.flow.name }
            .singleOrNull()
        if (existing == null) {
            AiAgentRegistryOverridesTable.insert { row ->
                row[flow] = override.flow.name
                row[enabled] = override.enabled
                row[routeVersion] = override.routeVersion
                row[contractName] = override.contractName
                row[contractVersion] = override.contractVersion
                row[killSwitchReason] = override.killSwitchReason
                row[updatedAtMs] = override.updatedAtMs
                row[updatedBy] = override.updatedBy
                row[note] = override.note
            }
        } else {
            AiAgentRegistryOverridesTable.update({
                AiAgentRegistryOverridesTable.flow eq override.flow.name
            }) { row ->
                row[enabled] = override.enabled
                row[routeVersion] = override.routeVersion
                row[contractName] = override.contractName
                row[contractVersion] = override.contractVersion
                row[killSwitchReason] = override.killSwitchReason
                row[updatedAtMs] = override.updatedAtMs
                row[updatedBy] = override.updatedBy
                row[note] = override.note
            }
        }
        override
    }

    override suspend fun delete(flow: AiNormalizationFlow): Boolean = DatabaseFactory.dbQuery {
        AiAgentRegistryOverridesTable.deleteWhere {
            AiAgentRegistryOverridesTable.flow eq flow.name
        } > 0
    }

    private fun ResultRow.toPersistentOverride(): AiAgentRegistryPersistentOverride =
        AiAgentRegistryPersistentOverride(
            flow = AiNormalizationFlow.valueOf(this[AiAgentRegistryOverridesTable.flow]),
            enabled = this[AiAgentRegistryOverridesTable.enabled],
            routeVersion = this[AiAgentRegistryOverridesTable.routeVersion],
            contractName = this[AiAgentRegistryOverridesTable.contractName],
            contractVersion = this[AiAgentRegistryOverridesTable.contractVersion],
            killSwitchReason = this[AiAgentRegistryOverridesTable.killSwitchReason],
            updatedAtMs = this[AiAgentRegistryOverridesTable.updatedAtMs],
            updatedBy = this[AiAgentRegistryOverridesTable.updatedBy],
            note = this[AiAgentRegistryOverridesTable.note],
        )
}
