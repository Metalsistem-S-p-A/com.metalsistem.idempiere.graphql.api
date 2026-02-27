package com.metalsistem.idempiere.graphql.resource;

import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.compiere.model.MRole;
import org.compiere.model.MUser;
import org.compiere.util.CacheInfo;
import org.compiere.util.CacheMgt;
import org.compiere.util.Env;

import com.metalsistem.idempiere.graphql.schemaprovider.IGraphQLSchemaContributor;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry.Builder;
import graphql.schema.GraphQLFieldDefinition;

public class CacheContributor implements IGraphQLSchemaContributor {

	public static final int PROCESS_CACHE_RESET = 205;

	@Override
	public String getContributorName() {
		return "cache";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("caches")
				.description("List cache entries. Admin-only.")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("tableName").type(GraphQLString)
						.description("Filter by table name (optional)").build())
				.argument(newArgument().name("name").type(GraphQLString)
						.description("Filter by cache name (optional)").build())
				.build()
		};
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("resetCache")
				.description("Reset cache entries. Requires process access.")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("tableName").type(GraphQLString)
						.description("Table name to reset (optional)").build())
				.argument(newArgument().name("recordId").type(GraphQLInt)
						.description("Record ID to reset (optional)").build())
				.build()
		};
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "caches"),
				(DataFetcher<Object>) env -> resolveCaches(env));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "resetCache"),
				(DataFetcher<Object>) env -> resolveResetCache(env));
	}

	private Object resolveCaches(DataFetchingEnvironment env) {
		MUser user = MUser.get(Env.getCtx());
		if (user == null || !user.isAdministrator())
			throw new SecurityException("Access denied for get caches request");

		String tableName = env.getArgument("tableName");
		String name = env.getArgument("name");
		List<CacheInfo> cacheInfos = CacheInfo.getCacheInfos(true);
		List<Map<String, Object>> caches = new ArrayList<>();
		for (CacheInfo cacheInfo : cacheInfos) {
			if (cacheInfo.getName().endsWith("|CCacheListener"))
				continue;
			if (tableName != null && !tableName.equals(cacheInfo.getTableName()))
				continue;
			if (name != null && !name.equals(cacheInfo.getName()))
				continue;

			Map<String, Object> cache = new LinkedHashMap<>();
			cache.put("name", cacheInfo.getName());
			cache.put("tableName", cacheInfo.getTableName());
			cache.put("size", cacheInfo.getSize());
			cache.put("expireMinutes", cacheInfo.getExpireMinutes());
			cache.put("maxSize", cacheInfo.getMaxSize());
			cache.put("distributed", cacheInfo.isDistributed());
			if (cacheInfo.getNodeId() != null)
				cache.put("nodeId", cacheInfo.getNodeId());
			caches.add(cache);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("caches", caches);
		return result;
	}

	private Object resolveResetCache(DataFetchingEnvironment env) {
		Boolean hasAccess = MRole.getDefault().getProcessAccess(PROCESS_CACHE_RESET);
		if (hasAccess == null || !hasAccess)
			throw new SecurityException("Access denied for cache reset request");

		String tableName = env.getArgument("tableName");
		Integer recordId = env.getArgument("recordId");

		int count;
		if (tableName == null) {
			count = CacheMgt.get().reset();
		} else if (recordId != null && recordId.intValue() > 0) {
			count = CacheMgt.get().reset(tableName, recordId.intValue());
		} else {
			count = CacheMgt.get().reset(tableName);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("entriesReset", count);
		return result;
	}
}
