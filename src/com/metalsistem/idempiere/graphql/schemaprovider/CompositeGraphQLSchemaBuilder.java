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

import java.util.ArrayList;
import java.util.List;

import com.metalsistem.idempiere.graphql.resource.AuthContributor;
import com.metalsistem.idempiere.graphql.resource.CacheContributor;
import com.metalsistem.idempiere.graphql.resource.ChartContributor;
import com.metalsistem.idempiere.graphql.resource.FileContributor;
import com.metalsistem.idempiere.graphql.resource.HealthContributor;
import com.metalsistem.idempiere.graphql.resource.MenuTreeContributor;
import com.metalsistem.idempiere.graphql.resource.ModelContributor;
import com.metalsistem.idempiere.graphql.resource.NodeContributor;
import com.metalsistem.idempiere.graphql.resource.ProcessContributor;
import com.metalsistem.idempiere.graphql.resource.ReferenceContributor;
import com.metalsistem.idempiere.graphql.resource.ServerContributor;
import com.metalsistem.idempiere.graphql.resource.StatusLineContributor;
import com.metalsistem.idempiere.graphql.resource.TaskContributor;
import com.metalsistem.idempiere.graphql.resource.WorkflowContributor;

import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import static graphql.schema.GraphQLObjectType.newObject;

/**
 * Composite GraphQL schema builder that combines schemas from known contributors
 * Allows each resource (models, processes, forms, etc) to contribute its own part of the schema
 * 
 * @author MetalSistem
 */
public class CompositeGraphQLSchemaBuilder {
	
	/**
	 * Build GraphQL schema by discovering all IGraphQLSchemaContributor implementations
	 */
	public GraphQLSchema buildSchema() {
		GraphQLCodeRegistry.Builder registryBuilder = GraphQLCodeRegistry.newCodeRegistry();
		GraphQLObjectType.Builder queryBuilder = newObject().name("Query");
		GraphQLObjectType.Builder mutationBuilder = newObject().name("Mutation");
		
		// Manually register in-bundle contributors (no OSGi discovery)
		List<IGraphQLSchemaContributor> contributors = new ArrayList<>();
		contributors.add(new DefaultGraphQLContributor());
		contributors.add(new AuthContributor());
		contributors.add(new ModelContributor());
		contributors.add(new ProcessContributor());
		contributors.add(new CacheContributor());
		contributors.add(new ChartContributor());
		contributors.add(new FileContributor());
		contributors.add(new HealthContributor());
		contributors.add(new ReferenceContributor());
		contributors.add(new MenuTreeContributor());
		contributors.add(new StatusLineContributor());
		contributors.add(new TaskContributor());
		contributors.add(new NodeContributor());
		contributors.add(new ServerContributor());
		contributors.add(new WorkflowContributor());
        
		// Always add default contributor to ensure Query has fields
		if (contributors.isEmpty()) {
			contributors.add(new DefaultGraphQLContributor());
		}
        
		// Let each contributor add its fields and fetchers
		boolean hasMutations = false;
		for (IGraphQLSchemaContributor contributor : contributors) {
			addContributorQueries(queryBuilder, registryBuilder, contributor);
			if (addContributorMutations(mutationBuilder, registryBuilder, contributor)) {
				hasMutations = true;
			}
		}
		
		GraphQLObjectType queryType = queryBuilder.build();
		
		GraphQLSchema.Builder schemaBuilder = GraphQLSchema.newSchema()
			.query(queryType)
			.codeRegistry(registryBuilder.build());
		
		// Only add mutation type if there are mutations
		if (hasMutations) {
			schemaBuilder.mutation(mutationBuilder.build());
		}
		
		return schemaBuilder.build();
	}
	
	/**
	 * Add queries from a contributor
	 */
	private void addContributorQueries(
		GraphQLObjectType.Builder queryBuilder,
		GraphQLCodeRegistry.Builder registryBuilder,
		IGraphQLSchemaContributor contributor) {
		
		// Add all fields from this contributor
		GraphQLFieldDefinition[] fields = contributor.getQueryFields();
		if (fields != null) {
			for (GraphQLFieldDefinition field : fields) {
				queryBuilder.field(field);
			}
		}
		
		// Register data fetchers
		contributor.registerDataFetchers(registryBuilder);
	}
	
	/**
	 * Add mutations from a contributor
	 * @return true if mutations were added
	 */
	private boolean addContributorMutations(
		GraphQLObjectType.Builder mutationBuilder,
		GraphQLCodeRegistry.Builder registryBuilder,
		IGraphQLSchemaContributor contributor) {
		
		// Add all mutation fields from this contributor
		GraphQLFieldDefinition[] fields = contributor.getMutationFields();
		if (fields != null && fields.length > 0) {
			for (GraphQLFieldDefinition field : fields) {
				mutationBuilder.field(field);
			}
			return true;
		}
		return false;
	}
}
