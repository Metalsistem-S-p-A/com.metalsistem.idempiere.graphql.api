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

import java.util.UUID;

import org.compiere.util.CCache;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;

/**
 * Provides GraphQL instances with schema built from registered contributors
 * Each GraphQL resource can contribute its own schema via IGraphQLSchemaContributor
 * 
 * @author MetalSistem
 */
public class GraphQLSchemaProvider {
	
	private static final String CACHE_NAME = "GraphQL_SchemaProviderCache";
	private static final String CACHE_KEY_SCHEMA_VERSION = "1.0";
	private static final CCache<String, String> schemaVersionCache = new CCache<String, String>(CACHE_NAME, 1, 60);

	private static volatile GraphQL graphQL;
	private static volatile String localSchemaVersion;
	
	public static GraphQL getGraphQL() {
		String currentVersion = getOrCreateSchemaVersion();
		if (graphQL == null || !currentVersion.equals(localSchemaVersion)) {
			currentVersion = getOrCreateSchemaVersion();
			if (graphQL == null || !currentVersion.equals(localSchemaVersion)) {
				graphQL = buildGraphQL();
				localSchemaVersion = currentVersion;
			}
		}
		return graphQL;
	}
	
	private static GraphQLSchema buildSchema() {
		CompositeGraphQLSchemaBuilder builder = new CompositeGraphQLSchemaBuilder();
		return builder.buildSchema();
	}
	
	private static GraphQL buildGraphQL() {
		return GraphQL.newGraphQL(buildSchema()).build();
	}

	private static String getOrCreateSchemaVersion() {
		String version = schemaVersionCache.get(CACHE_KEY_SCHEMA_VERSION);
		if (version == null) {
			version = schemaVersionCache.get(CACHE_KEY_SCHEMA_VERSION);
			if (version == null) {
				version = UUID.randomUUID().toString();
				schemaVersionCache.put(CACHE_KEY_SCHEMA_VERSION, version);
			}
		}
		return version;
	}
}