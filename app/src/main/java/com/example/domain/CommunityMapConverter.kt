package com.example.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/**
 * Validador e conversor autorizado de mapas externos da comunidade.
 * Garante verificação explícita de licença/autorização do criador original e impede
 * importação de arquivos protegidos sem permissão ou cópias enganosas de versões oficiais.
 */
@Serializable
data class CommunityMapImportManifest(
    val mapId: String,
    val title: String,
    val regionName: String,
    val originalCreator: String,
    val licenseType: String, // "CC-BY-4.0" | "MIT" | "FARM_BRASIL_PARTNER" | "ORIGINAL_COMMUNITY_INSPIRED" | "PROPRIETARY_UNLICENSED"
    val creatorAuthorized: Boolean,
    val authorizationReference: String,
    val areaTierId: String = "16X",
    val reliefProfile: String = "COLINAS_SUAVES",
    val biome: String = "Mata Atlântica / Terra Roxa",
    val soilName: String = "Terra Roxa Fértil",
    val hasRiver: Boolean = true,
    val featuredCrops: List<String> = listOf("soja", "milho", "trigo", "feijao_carioca"),
    val isOfficialCreatorPackage: Boolean = false,
    val packageSha256: String = "",
    val description: String = "",
)

sealed interface CommunityMapConversionResult {
    data class AuthorizedConverted(
        val mapDef: BrazilianMapDef,
        val recommendedTier: MapAreaTier,
        val licenseBadge: String,
        val integrityHash: String,
    ) : CommunityMapConversionResult

    data class Rejected(
        val reason: String,
        val complianceCode: String,
    ) : CommunityMapConversionResult
}

private val ALLOWED_COMMUNITY_LICENSES = setOf(
    "CC-BY-4.0",
    "CC-BY-SA-4.0",
    "MIT",
    "APACHE-2.0",
    "FARM_BRASIL_PARTNER",
    "ORIGINAL_COMMUNITY_INSPIRED",
)

private val converterJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun convertAuthorizedCommunityMapManifest(
    rawJson: String,
    existingMaps: List<BrazilianMapDef> = INITIAL_20_MAPS + MODHUB_MAPS + COMMUNITY_INSPIRED_MAPS,
): CommunityMapConversionResult {
    return try {
        val obj = converterJson.parseToJsonElement(rawJson) as? JsonObject
            ?: return CommunityMapConversionResult.Rejected(
                reason = "Manifesto inválido: objeto JSON esperado.",
                complianceCode = "INVALID_JSON",
            )
        val mapId = obj["mapId"]?.jsonPrimitive?.content?.trim().orEmpty()
        val title = obj["title"]?.jsonPrimitive?.content?.trim().orEmpty()
        val regionName = obj["regionName"]?.jsonPrimitive?.content?.trim().orEmpty()
        val originalCreator = obj["originalCreator"]?.jsonPrimitive?.content?.trim().orEmpty()
        val licenseType = obj["licenseType"]?.jsonPrimitive?.content?.trim()?.uppercase().orEmpty()
        val creatorAuthorized = obj["creatorAuthorized"]?.jsonPrimitive?.booleanOrNull ?: false
        val authorizationRef = obj["authorizationReference"]?.jsonPrimitive?.content?.trim().orEmpty()
        val isOfficial = obj["isOfficialCreatorPackage"]?.jsonPrimitive?.booleanOrNull ?: false
        val tierId = obj["areaTierId"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "16X" }
        val reliefName = obj["reliefProfile"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "COLINAS_SUAVES" }
        val biome = obj["biome"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "Mata Atlântica / Cerrado" }
        val soilName = obj["soilName"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "Latossolo Vermelho" }
        val hasRiver = obj["hasRiver"]?.jsonPrimitive?.booleanOrNull ?: true
        val heightScale = (obj["heightScale"]?.jsonPrimitive?.doubleOrNull ?: 4.0).coerceIn(1.5, 7.5)
        val crops = (obj["featuredCrops"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.content.trim().takeIf { c -> c.isNotBlank() } }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("soja", "milho", "trigo", "feijao_carioca")
        val desc = obj["description"]?.jsonPrimitive?.content?.trim().orEmpty()
        val seed = obj["seed"]?.jsonPrimitive?.intOrNull ?: (mapId.hashCode() and 0x7FFF).coerceAtLeast(500)

        if (mapId.length < 5 || !mapId.matches(Regex("^[a-z0-9_]+$"))) {
            return CommunityMapConversionResult.Rejected(
                reason = "mapId inválido. Use minúsculas, números e '_' (ex.: comm_custom_mapa).",
                complianceCode = "INVALID_MAP_ID",
            )
        }
        if (title.length < 3 || originalCreator.length < 2) {
            return CommunityMapConversionResult.Rejected(
                reason = "Título do mapa e nome do criador original são obrigatórios.",
                complianceCode = "MISSING_ATTRIBUTION",
            )
        }
        if (existingMaps.any { it.id == mapId }) {
            return CommunityMapConversionResult.Rejected(
                reason = "Já existe um mapa registrado com ID '$mapId'.",
                complianceCode = "DUPLICATE_MAP_ID",
            )
        }
        if (!creatorAuthorized || !ALLOWED_COMMUNITY_LICENSES.contains(licenseType)) {
            return CommunityMapConversionResult.Rejected(
                reason = "Conversão bloqueada: é obrigatório possuir licença válida (${ALLOWED_COMMUNITY_LICENSES.joinToString(", ")}) e autorização verificável do criador original ($originalCreator). Use inspiração regional original caso não tenha autorização.",
                complianceCode = "LICENSE_OR_AUTH_REQUIRED",
            )
        }
        if (authorizationRef.length < 6) {
            return CommunityMapConversionResult.Rejected(
                reason = "Informe o protocolo/referência de autorização ou link de licença aberta.",
                complianceCode = "MISSING_AUTH_REFERENCE",
            )
        }
        if (isOfficial && !licenseType.equals("FARM_BRASIL_PARTNER", ignoreCase = true)) {
            return CommunityMapConversionResult.Rejected(
                reason = "Não é permitido rotular conversão independente como pacote oficial do criador sem parceria verificada.",
                complianceCode = "MISLEADING_OFFICIAL_CLAIM",
            )
        }

        val relief = runCatching { ReliefProfile.valueOf(reliefName) }.getOrDefault(ReliefProfile.COLINAS_SUAVES)
        val tier = resolveMapAreaTierById(tierId)
        val sha = MessageDigest.getInstance("SHA-256")
            .digest("$mapId|$originalCreator|$licenseType|$authorizationRef|${tier.id}".toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)

        val converted = BrazilianMapDef(
            id = mapId,
            stateName = title,
            regionName = regionName.ifBlank { "Região Agrícola Brasileira" },
            biome = biome,
            soilName = soilName,
            soilColorRgb = when (relief) {
                ReliefProfile.PLANICIE_CERRADO -> rgb(0.50, 0.24, 0.14)
                ReliefProfile.SERRAS_VALES -> rgb(0.42, 0.22, 0.15)
                ReliefProfile.PLANICIE_ALAGAVEL -> rgb(0.41, 0.27, 0.18)
                else -> rgb(0.43, 0.20, 0.14)
            },
            grassColorRgb = rgb(0.26, 0.49, 0.19),
            reliefProfile = relief,
            heightScale = heightScale,
            hasRiver = hasRiver,
            seed = seed,
            featuredCrops = crops,
            mainEstablishments = listOf(
                "Cooperativa Regional $title",
                "Armazém Graneleiro Central",
                "Posto & Balança Rodoviária",
            ),
            startingLandValuePerHa = 47000.0,
            description = desc.ifBlank {
                "Mapa comunitário convertido com licença $licenseType e autorização de $originalCreator (${tier.label})."
            },
            isModMap = true,
            isCommunityInspired = true,
            communityCreatorName = originalCreator,
            licenseNotice = "Licença $licenseType • Ref: $authorizationRef • SHA: $sha",
            isOfficialCreatorPackage = isOfficial,
            recommendedAreaTierId = tier.id,
        )

        CommunityMapConversionResult.AuthorizedConverted(
            mapDef = converted,
            recommendedTier = tier,
            licenseBadge = if (isOfficial) "Oficial Parceiro ($licenseType)" else "Autorizado ($licenseType)",
            integrityHash = sha,
        )
    } catch (_: Exception) {
        CommunityMapConversionResult.Rejected(
            reason = "Erro ao interpretar manifesto JSON de conversão de mapa.",
            complianceCode = "PARSE_ERROR",
        )
    }
}
