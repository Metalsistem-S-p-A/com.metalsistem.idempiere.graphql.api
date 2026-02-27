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

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.compiere.model.MStatusLine;
import org.compiere.model.Query;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Util;

import com.metalsistem.idempiere.graphql.query.GraphQLInputTypes;
import com.metalsistem.idempiere.graphql.query.GraphQLQueryBuilder;
import com.metalsistem.idempiere.graphql.schemaprovider.IGraphQLSchemaContributor;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry.Builder;
import graphql.schema.GraphQLFieldDefinition;

public class StatusLineContributor implements IGraphQLSchemaContributor {

	@Override
	public String getContributorName() {
		return "statusLine";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("statusLines")
				.description("Get status lines with optional where filter")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("where").type(GraphQLInputTypes.WhereClause)
					.description("Optional status line filter").build())
				.argument(newArgument().name("includeMsg").type(GraphQLBoolean)
					.description("Include parsed message values").build())
				.build(),
			newFieldDefinition()
				.name("statusLineValue")
				.description("Get status line parsed message by id or UUID")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
					.description("AD_StatusLine_ID or AD_StatusLine_UU").build())
				.build()
		};
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[0];
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "statusLines"),
				(DataFetcher<Object>) env -> {
					Map<String, Object> where = env.getArgument("where");
					Boolean includeMsgArg = env.getArgument("includeMsg");
					boolean includeMsg = includeMsgArg != null && includeMsgArg.booleanValue();

					List<Map<String, Object>> order = GraphQLQueryBuilder.parseOrderBy("Name ASC");
					Query query = GraphQLQueryBuilder.buildQuery(MStatusLine.Table_Name, where, order, true, null);
					List<MStatusLine> statusLines = query.list();

					List<Map<String, Object>> rows = new ArrayList<>();
					for (MStatusLine statusLine : statusLines) {
						Map<String, Object> row = new LinkedHashMap<>();
						row.put("adStatusLineId", statusLine.getAD_StatusLine_ID());
						row.put("adStatusLineUU", statusLine.get_ValueAsString("AD_StatusLine_UU"));
						row.put("name", statusLine.getName());
						row.put("entityType", statusLine.getEntityType());
						if (includeMsg) {
							row.put("message", statusLine.parseLine(0));
						}
						rows.add(row);
					}

					Map<String, Object> result = new LinkedHashMap<>();
					result.put("statusLines", rows);
					return result;
				});

		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "statusLineValue"),
				(DataFetcher<Object>) env -> {
					String id = env.getArgument("id");
					int statusLineId = Util.isUUID(id) ? getStatusLineID(id) : Integer.parseInt(id);
					MStatusLine statusLine = new MStatusLine(Env.getCtx(), statusLineId, null);
					if (statusLine.get_ID() <= 0 || statusLine.getSQLStatement() == null) {
						throw new IllegalArgumentException("No valid status line with id: " + id);
					}
					Map<String, Object> result = new LinkedHashMap<>();
					result.put("message", statusLine.parseLine(0));
					return result;
				});
	}

	private int getStatusLineID(String uuid) {
		String sql = "SELECT AD_StatusLine_ID FROM AD_StatusLine WHERE AD_StatusLine_UU = ?";
		return DB.getSQLValue(null, sql, uuid);
	}
}
