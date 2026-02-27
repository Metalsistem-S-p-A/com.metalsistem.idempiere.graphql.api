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

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLCodeRegistry;

/**
 * Interface that resources can implement to contribute to GraphQL schema
 * Similar to how GraphQL resources register their endpoints
 * 
 * @author MetalSistem
 */
public interface IGraphQLSchemaContributor {
	
	/**
	 * Get the name of this contributor (e.g. "models", "processes", "forms")
	 * @return contributor name
	 */
	String getContributorName();
	
	/**
	 * Get GraphQL query fields contributed by this resource
	 * @return array of GraphQL field definitions for queries
	 */
	GraphQLFieldDefinition[] getQueryFields();
	
	/**
	 * Get GraphQL mutation fields contributed by this resource
	 * @return array of GraphQL field definitions for mutations, or null if no mutations
	 */
	GraphQLFieldDefinition[] getMutationFields();
	
	/**
	 * Register data fetchers for the contributed fields
	 * @param registryBuilder the code registry builder to add fetchers to
	 */
	void registerDataFetchers(GraphQLCodeRegistry.Builder registryBuilder);
}
