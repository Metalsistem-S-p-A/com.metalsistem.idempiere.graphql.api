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
*
**********************************************************************/
package com.metalsistem.idempiere.graphql.schemaprovider;

import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNonNull;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;

import static graphql.Scalars.GraphQLString;
import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLBoolean;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;

import com.metalsistem.idempiere.graphql.api.util.GraphQLTrxManager;

/**
 * Default GraphQL contributor that provides health/info queries
 * Always registered to ensure Query type has at least one field
 * 
 * @author MetalSistem
 */
public class DefaultGraphQLContributor implements IGraphQLSchemaContributor {

	@Override
	public String getContributorName() {
		return "default";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("health")
				.type(GraphQLString)
				.description("Health check - returns 'OK' if service is running")
				.build(),
			newFieldDefinition()
				.name("version")
				.type(GraphQLString)
				.description("API version")
				.build()
		};
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("increment")
				.type(GraphQLInt)
				.argument(newArgument()
					.name("value")
					.type(GraphQLNonNull.nonNull(GraphQLInt))
					.description("The number to increment")
					.build())
				.description("Increments the given number by 1")
				.build(),
			newFieldDefinition()
				.name("beginTransaction")
				.type(GraphQLString)
				.argument(newArgument()
					.name("prefix")
					.type(GraphQLString)
					.description("Optional transaction name prefix")
					.build())
				.description("Start a new transaction and return trxId")
				.build(),
			newFieldDefinition()
				.name("commitTransaction")
				.type(GraphQLBoolean)
				.argument(newArgument()
					.name("trxId")
					.type(GraphQLNonNull.nonNull(GraphQLString))
					.description("Transaction id to commit")
					.build())
				.description("Commit an existing transaction")
				.build(),
			newFieldDefinition()
				.name("rollbackTransaction")
				.type(GraphQLBoolean)
				.argument(newArgument()
					.name("trxId")
					.type(GraphQLNonNull.nonNull(GraphQLString))
					.description("Transaction id to rollback")
					.build())
				.description("Rollback an existing transaction")
				.build()
		};
	}

	@Override
	public void registerDataFetchers(GraphQLCodeRegistry.Builder registryBuilder) {
		registryBuilder.dataFetcher(
			FieldCoordinates.coordinates("Query", "health"),
			(DataFetcher<String>) env -> "OK"
		);
		
		registryBuilder.dataFetcher(
			FieldCoordinates.coordinates("Query", "version"),
			(DataFetcher<String>) env -> "1.0.0"
		);
		
		registryBuilder.dataFetcher(
			FieldCoordinates.coordinates("Mutation", "increment"),
			(DataFetcher<Integer>) env -> {
				Integer value = env.getArgument("value");
				return value != null ? value + 1 : 1;
			}
		);
		
		registryBuilder.dataFetcher(
			FieldCoordinates.coordinates("Mutation", "beginTransaction"),
			(DataFetcher<String>) env -> {
				String prefix = env.getArgument("prefix");
				return GraphQLTrxManager.begin(prefix);
			}
		);
		
		registryBuilder.dataFetcher(
			FieldCoordinates.coordinates("Mutation", "commitTransaction"),
			(DataFetcher<Boolean>) env -> {
				String trxId = env.getArgument("trxId");
				GraphQLTrxManager.commit(trxId);
				return Boolean.TRUE;
			}
		);
		
		registryBuilder.dataFetcher(
			FieldCoordinates.coordinates("Mutation", "rollbackTransaction"),
			(DataFetcher<Boolean>) env -> {
				String trxId = env.getArgument("trxId");
				GraphQLTrxManager.rollback(trxId);
				return Boolean.TRUE;
			}
		);
	}
}
