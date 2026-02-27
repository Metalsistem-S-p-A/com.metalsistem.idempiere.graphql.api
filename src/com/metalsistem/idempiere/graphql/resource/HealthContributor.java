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
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

import org.compiere.util.DB;

import com.metalsistem.idempiere.graphql.schemaprovider.IGraphQLSchemaContributor;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry.Builder;
import graphql.schema.GraphQLFieldDefinition;

public class HealthContributor implements IGraphQLSchemaContributor {

	@Override
	public String getContributorName() {
		return "health";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("healthStatus")
				.description("Get service health status, optional DB connectivity check")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("dbstatus").type(GraphQLBoolean)
					.description("Include database connectivity status").build())
				.build()
		};
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[0];
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "healthStatus"),
				(DataFetcher<Object>) env -> {
					Boolean dbstatus = env.getArgument("dbstatus");
					boolean checkDb = dbstatus != null && dbstatus.booleanValue();

					Map<String, Object> result = new LinkedHashMap<>();
					result.put("status", "UP");
					if (checkDb) {
						result.put("database", DB.isConnected() ? "connected" : "disconnected");
					}
					return result;
				});
	}
}
