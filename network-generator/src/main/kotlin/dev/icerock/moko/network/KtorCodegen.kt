/*
 * Copyright 2019 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.moko.network

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.servers.Server
import org.apache.commons.lang3.StringUtils
import org.openapitools.codegen.*
import org.openapitools.codegen.config.GlobalSettings
import org.openapitools.codegen.languages.AbstractKotlinCodegen
import java.util.regex.Matcher
import java.util.regex.Pattern

@Suppress("TooManyFunctions")
class KtorCodegen : AbstractKotlinCodegen() {

    private val openApiProcessor = OpenApiProcessor()

    /**
     * Constructs an instance of `KtorCodegen`.
     */
    init {
        artifactId = "kotlin-ktor-client"
        packageName = "dev.icerock.moko.network.generated"

        typeMapping["array"] = "kotlin.collections.List"
        typeMapping["number"] = "kotlin.Double"

        typeMapping["date"] = "kotlin.String"
        typeMapping["date-time"] = "kotlin.String"
        typeMapping["Date"] = "kotlin.String"
        typeMapping["DateTime"] = "kotlin.String"

        typeMapping["UUID"] = "kotlin.String"
        typeMapping["URI"] = "kotlin.String"
        typeMapping["object"] = "JsonObject"
        typeMapping["decimal"] = "BigNum"
        typeMapping[ONE_OF_REPLACE_TYPE_NAME] = "JsonElement"
        typeMapping["AnyType"] = "JsonElement"
        typeMapping.remove("file")


        importMapping["JsonObject"] = "kotlinx.serialization.json.JsonObject"
        importMapping["JsonElement"] = "kotlinx.serialization.json.JsonElement"
        importMapping["BigNum"] = "com.soywiz.kbignum.BigNum"
        importMapping.remove("File")

        embeddedTemplateDir = "kotlin-ktor-client"

        modelTemplateFiles["model.mustache"] = ".kt"
        apiTemplateFiles["api.mustache"] = ".kt"
        modelDocTemplateFiles["model_doc.mustache"] = ".md"
        apiDocTemplateFiles["api_doc.mustache"] = ".md"
        apiPackage = "$packageName.apis"
        modelPackage = "$packageName.models"

        // Add all processors for openapi spec
        openApiProcessor.apply {
            addProcessor(OneOfOperatorProcessor(ONE_OF_REPLACE_TYPE_NAME))
            addProcessor(SchemaEnumNullProcessor())
            ComposedSchemaProcessor { operation, path, method ->
                getOrGenerateOperationId(operation, path, method)
            }.also { addProcessor(it) }
        }
        GlobalSettings.setProperty(CodegenConstants.SKIP_FORM_MODEL, "false")
    }

    override fun processOpts() {
        super.processOpts()

        val isOpenProp = additionalProperties[ADDITIONAL_OPTIONS_KEY_IS_OPEN]
        if (isOpenProp is String) {
            additionalProperties[ADDITIONAL_OPTIONS_KEY_IS_OPEN] = isOpenProp == "true"
        }
        val isInternalProp = additionalProperties[ADDITIONAL_OPTIONS_KEY_IS_INTERNAL]
        if (isInternalProp is String) {
            nonPublicApi = isInternalProp == "true"
            additionalProperties[ADDITIONAL_OPTIONS_KEY_IS_INTERNAL] = nonPublicApi
        }
        val enumFallbackNullProp = additionalProperties[ADDITIONAL_OPTIONS_KEY_ENUM_FALLBACK_NULL]
        if (enumFallbackNullProp is String) {
            additionalProperties[ADDITIONAL_OPTIONS_KEY_ENUM_FALLBACK_NULL] =
                enumFallbackNullProp == "true"
        }

        val useNullableProp = additionalProperties[ADDITIONAL_OPTIONS_KEY_USE_NULLABLE]
        if (useNullableProp is String) {
            additionalProperties[ADDITIONAL_OPTIONS_KEY_USE_NULLABLE] =
                useNullableProp == "true"
        }
    }

    override fun getTag(): CodegenType {
        return CodegenType.CLIENT
    }

    override fun getName(): String {
        return "kotlin-ktor-client"
    }

    override fun getHelp(): String {
        return "Generates a kotlin ktor client."
    }

    override fun toEnumName(property: CodegenProperty): String {
        return StringUtils.capitalize(property.name)
    }

    override fun preprocessOpenAPI(openAPI: OpenAPI) {
        super.preprocessOpenAPI(openAPI)

        additionalProperties[ADDITIONAL_OPTIONS_KEY_EXCLUDED_TAGS]
            ?.let { (it as? String)?.split(",")?.toSet() }
            ?.let { filterPaths(openAPI.paths, it) }

        openApiProcessor.process(openAPI)

        val schemas: MutableMap<String, Schema<*>> = openAPI.components.schemas

        openAPI.components?.requestBodies?.forEach { (requestBodyName, requestBody) ->
            val jsonContent: MediaType? = requestBody.content["application/json"]
            if (jsonContent == null) return@forEach

            val jsonSchema = jsonContent.schema

            when {
                jsonSchema is ArraySchema && jsonSchema.items.`$ref` == null -> {
                    // Create new name for component scheme of array items
                    val newArrayItemSchemaName = "${requestBodyName}Item"
                    // Add array items scheme to components
                    schemas[newArrayItemSchemaName] = jsonSchema.items
                    // Replace old requestBody item scheme to ref
                    val objectSchema = Schema<Any>().apply {
                        `$ref` = "#/components/schemas/$newArrayItemSchemaName"
                    }
                    jsonSchema.items = objectSchema
                }
                jsonSchema is ObjectSchema && jsonSchema.`$ref` == null -> {
                    // Create new name for component scheme
                    val newBodySchemaName = "${requestBodyName}Object"
                    // Add new scheme to components
                    schemas[newBodySchemaName] = jsonSchema
                    // Replace old requestBody item scheme to ref
                    val objectSchema = Schema<Any>().apply {
                        `$ref` = "#/components/schemas/$newBodySchemaName"
                    }
                    jsonContent.schema = objectSchema
                }
            }
        }
    }

    override fun fromOperation(
        path: String,
        httpMethod: String,
        operation: Operation?,
        servers: List<Server>?
    ): CodegenOperation {
        val codegenOperation = super.fromOperation(path, httpMethod, operation, servers)
        codegenOperation.httpMethod = codegenOperation.httpMethod.lowercase().capitalize()
        var currentPath = codegenOperation.path
        for (param in codegenOperation.pathParams) {
            currentPath = currentPath.replace("{" + param.baseName + "}", "$" + param.paramName)
        }
        if (currentPath[0] == '/') {
            currentPath = currentPath.substring(1)
        }
        codegenOperation.path = currentPath

        // fix imports for complex types
        val imports: MutableSet<String> = codegenOperation.imports

        var propertyProcess: (CodegenProperty) -> Unit = {}

        propertyProcess = { property ->
            imports.add(property.baseType)

            property.additionalProperties?.let(propertyProcess)
            property.items?.let(propertyProcess)
        }

        val successResponse = codegenOperation.responses.firstOrNull { it.is2xx }
        if (successResponse != null) {
            with(successResponse) {
                baseType?.let { imports.add(it) }
                additionalProperties?.let(propertyProcess)
                items?.let(propertyProcess)
            }
            codegenOperation.vendorExtensions["x-successResponse"] = successResponse
        }

        // fix query
        codegenOperation
            .queryParams
            .filter {
                it.baseName == "..." && it.isQueryParam
            }
            .forEach { param ->
                val p: Pattern = Pattern.compile("^Schema: \\[\\`([a-zA-Z0-9]+)\\`\\]")
                val m: Matcher = p.matcher(param.description)
                if (m.find()) {
                    val nestedSchema = openAPI.components.schemas[m.group(1)]
                    if (nestedSchema != null) {
                        codegenOperation.parseNestedObjects(
                            param.baseName,
                            nestedSchema,
                            this,
                            imports,
                            openAPI
                        )
                    }
                }
            }

        codegenOperation.queryParams.removeAll {
            it.isQueryParam && it.baseName == "..."
        }

        codegenOperation.allParams.removeAll {
            it.isQueryParam && it.baseName == "..."
        }

        return codegenOperation
    }

    override fun fromParameter(
        parameter: Parameter?,
        imports: MutableSet<String>?
    ): CodegenParameter {
        return super.fromParameter(parameter, imports)
    }

    override fun fromProperty(name: String?, schema: Schema<*>?): CodegenProperty {
        val property = super.fromProperty(name, schema)
        if (schema?.format?.equals("decimal") == true) {
            property.isDecimal = true
        }

        if (property.required) {
            property.required = false
            property.isNullable = true
            property.setDefaultValue(null)
        }

        if (!property.isNullable) {
            property.required = false
            property.isNullable = true
            property.setDefaultValue(null)
        }

        return property
    }

    override fun fromModel(name: String?, schema: Schema<*>?): CodegenModel {
        val model = super.fromModel(name, schema)
        model.vars.forEach {
            if (it.isDecimal) {
                model.imports.add("dev.icerock.moko.network.bignum.BigNumSerializer")
            }
        }
        return model
    }

    override fun getEnumPropertyNaming(): CodegenConstants.ENUM_PROPERTY_NAMING_TYPE {
        return CodegenConstants.ENUM_PROPERTY_NAMING_TYPE.UPPERCASE
    }

    private fun filterPaths(paths: Paths?, pathOperationsFilterSet: Set<String>) {
        paths?.forEach { (_, pathItem) ->
            with(pathItem) {
                if (get.needFilterOperation(pathOperationsFilterSet)) {
                    get = null
                }
                if (put.needFilterOperation(pathOperationsFilterSet)) {
                    put = null
                }
                if (post.needFilterOperation(pathOperationsFilterSet)) {
                    post = null
                }
                if (delete.needFilterOperation(pathOperationsFilterSet)) {
                    delete = null
                }
                if (options.needFilterOperation(pathOperationsFilterSet)) {
                    options = null
                }
                if (head.needFilterOperation(pathOperationsFilterSet)) {
                    head = null
                }
                if (patch.needFilterOperation(pathOperationsFilterSet)) {
                    patch = null
                }
                if (trace.needFilterOperation(pathOperationsFilterSet)) {
                    trace = null
                }
            }
        }
    }

    private fun Operation?.needFilterOperation(filterTagNameSet: Set<String>): Boolean {
        return this?.tags?.any(filterTagNameSet::contains) == true
    }

    companion object {
        const val ADDITIONAL_OPTIONS_KEY_EXCLUDED_TAGS = "excludedTags"
        const val ADDITIONAL_OPTIONS_KEY_IS_OPEN = "isOpen"
        const val ADDITIONAL_OPTIONS_KEY_IS_INTERNAL = "nonPublicApi"
        const val ADDITIONAL_OPTIONS_KEY_ENUM_FALLBACK_NULL = "isEnumFallbackNull"
        const val ADDITIONAL_OPTIONS_KEY_USE_NULLABLE = "useNullable"
        private const val ONE_OF_REPLACE_TYPE_NAME = "oneOfElement"
    }
}
