/*
 * Copyright 2022 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.moko.network

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.*
import org.apache.commons.lang3.StringUtils
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.CodegenParameter
import org.openapitools.codegen.DefaultCodegen
import org.openapitools.codegen.utils.ModelUtils.getSimpleRef

fun CodegenOperation.parseNestedObjects(
    name: String,
    schema: Schema<*>?,
    config: DefaultCodegen,
    imports: Set<String?>?,
    openAPI: OpenAPI?
) {
    var schema: Schema<*>? = schema
    schema = getRefSchemaIfExists(schema, openAPI)
    if (schema == null) {
        return
    }

    val properties: Map<String, Schema<*>> = schema.properties
    for (key in properties.keys) {
        var property: Schema<*>? = properties[key]
        property = getRefSchemaIfExists(property, openAPI)

        val required = if (schema.required == null || schema.required.isEmpty()) {
            false
        } else {
            schema.required.stream().anyMatch { propertyName ->
                key.equals(
                    propertyName.toString(),
                    ignoreCase = true
                )
            }
        }
        val parameterName = if (property is ArraySchema) {
            if (name == "...") String.format("%s[]", key) else String.format("%s[%s][]", name, key)
        } else {
            if (name == "...") key else String.format("%s[%s]", name, key)
        }
        if (isObjectWithProperties(property!!)) {
            parseNestedObjects(parameterName, property, config, imports, openAPI)
            continue
        }

        val queryParameter: Parameter = QueryParameter()
            .name(parameterName)
            .required(required)
            .schema(property)

        val codegenParameter = config.fromParameter(queryParameter, imports)

        addParameters(queryParameter, codegenParameter)
    }
}

fun CodegenOperation.addParameters(parameter: Parameter, codegenParameter: CodegenParameter) {
    allParams.add(codegenParameter);

    if (parameter is QueryParameter || "query".equals(parameter.getIn(), true)) {
        queryParams.add(codegenParameter.copy());
    } else if (parameter is PathParameter || "path".equals(parameter.getIn(), true)) {
        pathParams.add(codegenParameter.copy());
    } else if (parameter is HeaderParameter || "header".equals(parameter.getIn(), true)) {
        headerParams.add(codegenParameter.copy());
    } else if (parameter is CookieParameter || "cookie".equals(parameter.getIn(), true)) {
        cookieParams.add(codegenParameter.copy());
    }
    if (codegenParameter.required) {
        requiredParams.add(codegenParameter.copy());
    }
}


private fun isObjectWithProperties(schema: Schema<*>): Boolean {
    return ("object".equals(
        schema.type,
        ignoreCase = true
    ) || schema.type == null) && (schema.properties != null) && !schema.properties.isEmpty()
}

fun getRefSchemaIfExists(schema: Schema<*>?, openAPI: OpenAPI?): Schema<*>? {
    if (schema == null) {
        return null
    }
    if (StringUtils.isBlank(schema.`$ref`) || openAPI == null || openAPI.components == null) {
        return schema
    }
    val name = getSimpleRef(schema.`$ref`)
    return getSchemaFromName(name, openAPI)
}


fun getSchemaFromName(name: String, openAPI: OpenAPI?): Schema<*>? {
    if (openAPI == null) {
        return null
    }
    if (openAPI.components == null) {
        return null
    }
    val mapSchema = openAPI.components.schemas
    return if (mapSchema == null || mapSchema.isEmpty()) {
        null
    } else mapSchema[name]
}