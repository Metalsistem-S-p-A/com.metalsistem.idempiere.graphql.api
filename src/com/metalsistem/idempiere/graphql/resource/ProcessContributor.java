/**********************************************************************
* This file is part of iDempiere ERP Open Source                      *
* http://www.idempiere.org                                            *
*                                                                     *
* Copyright (C) Contributors                                          *
*                                                                     *
* This program is free software; you can redistribute it and/or       *
* modify it under the terms of the GNU General Public License         *
* as published by the Free Software Foundation; either version 2      *
* of the License, or (at your option) any later version.              *
**********************************************************************/
package com.metalsistem.idempiere.graphql.resource;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.compiere.model.MProcess;
import org.compiere.model.MRole;
import org.compiere.model.Query;
import org.compiere.util.Env;

import com.metalsistem.idempiere.graphql.query.GraphQLInputTypes;
import com.metalsistem.idempiere.graphql.query.GraphQLQueryBuilder;
import com.metalsistem.idempiere.graphql.schemaprovider.IGraphQLSchemaContributor;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry.Builder;
import graphql.schema.GraphQLFieldDefinition;

public class ProcessContributor implements IGraphQLSchemaContributor {

	@Override
	public String getContributorName() {
		return "process";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[0];
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[] {
				// Execute process
				newFieldDefinition()
						.name("execute")
						.description("Execute an iDempiere process or report. Identify process by id, value, or name (at least one required).")
						.type(ExtendedScalars.Json)
						.argument(newArgument().name("process").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLInputTypes.ProcessInput))
								.description("Process execution input (id/value/name + parameters)")
								.build())
						.argument(newArgument().name("trxId").type(GraphQLString)
								.description("Optional transaction id to run the process within")
								.build())
						.build()
		};
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		// Mutation data fetcher
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "execute"),
				(DataFetcher<Object>) env -> resolveExecuteProcess(env));
	}

	/**
	 * Resolve 'execute' mutation - execute a process
	 */
	private Object resolveExecuteProcess(DataFetchingEnvironment env) {
		Map<String, Object> processInput = env.getArgument("process");
		Integer id = (Integer) processInput.get("id");
		String value = (String) processInput.get("value");
		String name = (String) processInput.get("name");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> parameters = (List<Map<String, Object>>) processInput.get("parameters");
		Integer recordId = (Integer) processInput.get("recordId");
		String recordUU = (String) processInput.get("recordUU");
		Integer tableId = (Integer) processInput.get("tableId");
		String tableName = (String) processInput.get("tableName");
		String reportType = (String) processInput.get("reportType");
		String trxId = env.getArgument("trxId");
		
		// At least one identifier must be provided
		if (id == null && value == null && name == null) {
			throw new IllegalArgumentException("At least one of 'id', 'value', or 'name' must be provided to identify the process");
		}
		
		// Build WHERE clause based on provided identifiers
		StringBuilder where = new StringBuilder();
		List<Object> params = new ArrayList<>();
		
		if (id != null) {
			where.append("AD_Process_ID=?");
			params.add(id);
		}
		
		if (value != null) {
			if (where.length() > 0) where.append(" AND ");
			where.append("Value=?");
			params.add(value);
		}
		
		if (name != null) {
			if (where.length() > 0) where.append(" AND ");
			where.append("Name=?");
			params.add(name);
		}
		
		// Find process
		Query query = new Query(Env.getCtx(), MProcess.Table_Name, where.toString(), null);
		query.setApplyAccessFilter(true).setOnlyActiveRecords(true);
		query.setParameters(params);
		
		List<MProcess> processes = query.list();
		
		if (processes == null || processes.isEmpty()) {
			throw new IllegalArgumentException("Process not found with provided criteria");
		}
		
		if (processes.size() > 1) {
			throw new IllegalArgumentException("Multiple processes found with provided criteria. Please be more specific (found " + processes.size() + " processes)");
		}
		
		MProcess process = processes.get(0);
		
		// Check access
		MRole role = MRole.getDefault();
		Boolean hasAccess = role.getProcessAccess(process.getAD_Process_ID());
		if (hasAccess == null || !hasAccess) {
			throw new SecurityException("Access denied for process: " + process.getValue());
		}
		
		// Execute process using GraphQLProcessRunner
		Map<String, Object> result = GraphQLProcessRunner.executeProcess(
			process, 
			parameters,
			recordId != null ? recordId : 0,
			recordUU,
			tableId != null ? tableId : 0,
			tableName,
			reportType,
			trxId
		);
		
		return GraphQLQueryBuilder.toLowerCaseKeys(result);
	}
}
