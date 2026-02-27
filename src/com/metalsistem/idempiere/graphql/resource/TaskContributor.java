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
*                                                                     *
* This program is distributed in the hope that it will be useful,     *
* but WITHOUT ANY WARRANTY; without even the implied warranty of      *
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the        *
* GNU General Public License for more details.                        *
*                                                                     *
* You should have received a copy of the GNU General Public License   *
* along with this program; if not, write to the Free Software         *
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,          *
* MA 02110-1301, USA.                                                 *
*                                                                     *
* Contributors:                                                       *
* - Metalsistem S.p.A.                                                *
**********************************************************************/
package com.metalsistem.idempiere.graphql.resource;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.compiere.model.MRole;
import org.compiere.model.MTask;
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

public class TaskContributor implements IGraphQLSchemaContributor {

	@Override
	public String getContributorName() {
		return "task";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("tasks")
				.description("Get executable tasks")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("where").type(GraphQLInputTypes.WhereClause)
					.description("Optional task filter").build())
				.build(),
			newFieldDefinition()
				.name("task")
				.description("Get task by id, value or name")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("task").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
					.description("Task id, value or name").build())
				.build()
		};
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("runTask")
				.description("Run task by id, value or name")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("task").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
					.description("Task id, value or name").build())
				.build()
		};
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "tasks"),
				(DataFetcher<Object>) this::resolveGetTasks);
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "task"),
				(DataFetcher<Object>) env -> resolveGetTask(env.getArgument("task")));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "runTask"),
				(DataFetcher<Object>) env -> resolveRunTask(env.getArgument("task")));
	}

	private Object resolveGetTasks(DataFetchingEnvironment env) {
		Map<String, Object> where = env.getArgument("where");
		List<Map<String, Object>> order = GraphQLQueryBuilder.parseOrderBy(MTask.COLUMNNAME_Name + " ASC");
		Query query = GraphQLQueryBuilder.buildQuery(MTask.Table_Name, where, order, true, null);
		List<MTask> tasks = query.list();
		MRole role = MRole.getDefault();

		List<Map<String, Object>> rows = new ArrayList<>();
		for (MTask task : tasks) {
			Boolean hasAccess = role.getTaskAccess(task.getAD_Task_ID());
			if (hasAccess == null || !hasAccess.booleanValue()) {
				continue;
			}
			rows.add(toTaskRow(task));
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("tasks", rows);
		return result;
	}

	private Object resolveGetTask(String task) {
		MTask taskObj = findAndAuthorizeTask(task);
		return toTaskRow(taskObj);
	}

	private Object resolveRunTask(String task) {
		MTask taskObj = findAndAuthorizeTask(task);
		taskObj.execute();

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("exitValue", taskObj.getExecutedTask().getExitValue());
		result.put("stdOut", taskObj.getExecutedTask().getOut().toString());
		result.put("stdErr", taskObj.getExecutedTask().getErr().toString());
		return result;
	}

	private MTask findAndAuthorizeTask(String task) {
		MTask taskObj = findTaskByIdValueOrName(task, true);
		if (taskObj == null) {
			MTask hidden = findTaskByIdValueOrName(task, false);
			if (hidden != null) {
				throw new SecurityException("Access denied for task: " + task);
			}
			throw new IllegalArgumentException("No match found for task name: " + task);
		}

		MRole role = MRole.getDefault();
		Boolean hasAccess = role.getTaskAccess(taskObj.getAD_Task_ID());
		if (hasAccess == null || !hasAccess.booleanValue()) {
			throw new SecurityException("Access denied for task: " + task);
		}
		return taskObj;
	}

	private MTask findTaskByIdValueOrName(String task, boolean applyAccessFilter) {
		Query query = new Query(Env.getCtx(), MTask.Table_Name, "AD_Task_ID = ? OR Value = ? OR Name = ?", null);
		query.setApplyAccessFilter(applyAccessFilter).setOnlyActiveRecords(true);
		return query.setParameters(task, task, task).first();
	}

	private Map<String, Object> toTaskRow(MTask task) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("adTaskId", task.getAD_Task_ID());
		row.put("adTaskUU", task.get_ValueAsString("AD_Task_UU"));
		row.put("name", task.getName());
		row.put("description", task.getDescription());
		row.put("help", task.getHelp());
		row.put("entityType", task.getEntityType());
		return row;
	}
}
