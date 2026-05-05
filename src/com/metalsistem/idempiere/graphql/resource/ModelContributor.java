package com.metalsistem.idempiere.graphql.resource;

import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.Query;
import com.metalsistem.idempiere.graphql.api.util.GraphQLUtils;
import com.metalsistem.idempiere.graphql.query.GraphQLInputTypes;
import com.metalsistem.idempiere.graphql.query.GraphQLQueryBuilder;
import com.metalsistem.idempiere.graphql.schemaprovider.IGraphQLSchemaContributor;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry.Builder;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;

public class ModelContributor implements IGraphQLSchemaContributor {

	private static final int DEFAULT_PAGE_SIZE = 100;
	private static final int MAX_PAGE_SIZE = 500;

	@Override
	public String getContributorName() {
		return "model";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[] {
				newFieldDefinition()
						.name("model")
						.description("SQL-like query builder for any iDempiere table. Returns paginated results and metadata: results, totalRecords, totalPages, page, pageSize.")
						.type(ExtendedScalars.Json)
						.argument(newArgument().name("table").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
								.description("Table name (FROM clause)").build())
						.argument(newArgument().name("select").type(GraphQLString)
								.description("Comma-separated column names (SELECT clause). Defaults to all accessible columns.").build())
						.argument(newArgument().name("join").type(new GraphQLList(GraphQLInputTypes.JoinSpec))
								.description("JOIN specifications for querying related tables").build())
						.argument(newArgument().name("tableAlias").type(GraphQLString)
								.description("Optional alias for the base table (e.g. 'asi' for 'm_attributesetinstance')").build())
						.argument(newArgument().name("where").type(GraphQLInputTypes.WhereClause)
								.description("WHERE clause condition (structured input type)").build())
						.argument(newArgument().name("orderBy").type(GraphQLString)
								.description("ORDER BY clause (e.g., 'Name ASC, Created DESC')").build())
						.argument(newArgument().name("pageSize").type(GraphQLInt)
								.description("Maximum number of records to return per page. Max 500, default 100.").build())
						.argument(newArgument().name("page").type(GraphQLInt)
								.description("Pages to skip (0-based). Use 0 for the first page.").build())
						.argument(newArgument().name("trxId").type(GraphQLString)
								.description("Optional transaction id to run the query within").build())
						.build()
						};
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[] {
				// CREATE
				newFieldDefinition()
						.name("create")
						.description("Create a new record in any iDempiere table")
						.type(ExtendedScalars.Json)
						.argument(newArgument().name("model").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLInputTypes.ModelInput))
								.description("Model input (table + values)").build())
						.argument(newArgument().name("trxId").type(GraphQLString)
								.description("Optional transaction id to run the mutation within").build())
						.build(),
				
				// UPDATE
				newFieldDefinition()
						.name("update")
						.description("Update existing records in any iDempiere table")
						.type(GraphQLInt)
						.argument(newArgument().name("model").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLInputTypes.ModelInput))
								.description("Model input (table + values + where)").build())
						.argument(newArgument().name("trxId").type(GraphQLString)
								.description("Optional transaction id to run the mutation within").build())
						.build(),
				
				// DELETE
				newFieldDefinition()
						.name("delete")
						.description("Delete records from any iDempiere table")
						.type(GraphQLInt)
						.argument(newArgument().name("model").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLInputTypes.ModelInput))
								.description("Model input (table + where)").build())
						.argument(newArgument().name("trxId").type(GraphQLString)
								.description("Optional transaction id to run the mutation within").build())
						.build()
		};
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		// Query data fetcher
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "model"),
				(DataFetcher<Object>) env -> {
					String tableName = env.getArgument("table");
					MTable table = GraphQLUtils.getTableAndCheckAccess(tableName, false);
					return resolveTableQuery(env, table.getTableName());
				});
		
		// Mutation data fetchers
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "create"),
				(DataFetcher<Object>) env -> {
					Map<String, Object> model = env.getArgument("model");
					String tableName = (String) model.get("table");
					MTable table = GraphQLUtils.getTableAndCheckAccess(tableName, false);
					return resolveCreate(env, table.getTableName());
				});
		
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "update"),
				(DataFetcher<Object>) env -> {
					Map<String, Object> model = env.getArgument("model");
					String tableName = (String) model.get("table");
					MTable table = GraphQLUtils.getTableAndCheckAccess(tableName, false);
					return resolveUpdate(env, table.getTableName());
				});
		
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "delete"),
				(DataFetcher<Object>) env -> {
					Map<String, Object> model = env.getArgument("model");
					String tableName = (String) model.get("table");
					MTable table = GraphQLUtils.getTableAndCheckAccess(tableName, false);
					return resolveDelete(env, table.getTableName());
				});
	}
	
	private Map<String, Object> resolveTableQuery(DataFetchingEnvironment env, String tableName) {
		Map<String, Object> where = env.getArgument("where");
		String select = env.getArgument("select");
		String orderBy = env.getArgument("orderBy");
		List<Map<String, Object>> join = env.getArgument("join");
		String tableAlias = env.getArgument("tableAlias");
		Integer pageSize = env.getArgument("pageSize");
		Integer page = env.getArgument("page");
		String trxId = env.getArgument("trxId");

		String[] selectedColumns = GraphQLQueryBuilder.resolveSelectedColumns(tableName,
				GraphQLQueryBuilder.splitCsv(select), join, tableAlias);
		List<Map<String, Object>> orderByList = GraphQLQueryBuilder.parseOrderBy(orderBy);

		int safePageSize = pageSize == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(pageSize.intValue(), MAX_PAGE_SIZE));
		int safePage = page == null ? 0 : Math.max(0, page.intValue());

		List<Object> rows = new ArrayList<>();
		int totalRecords;

		if (join != null && !join.isEmpty()) {
			// path JDBC diretto: evita duplicazione PO con JOIN 1-a-molti
			totalRecords = GraphQLQueryBuilder.countWithJoins(tableName, where, join, true, trxId, tableAlias);
			List<Map<String, Object>> rawRows = GraphQLQueryBuilder.listWithJoins(
					tableName, selectedColumns, where, join, orderByList, true, safePageSize, safePage, trxId, tableAlias);
			for (Map<String, Object> row : rawRows)
				rows.add(GraphQLQueryBuilder.toLowerCaseKeys(row));
		} else {
			// path PO esistente (nessun JOIN)
			String[] queryColumns = GraphQLQueryBuilder.extractBaseTableColumnsForQuery(tableName, selectedColumns);
			Query countQuery = GraphQLQueryBuilder.buildQuery(tableName, where, null, orderByList, true, trxId);
			totalRecords = countQuery.count();
			Query query = GraphQLQueryBuilder.buildQuery(tableName, where, null, orderByList, true, trxId);
			if (queryColumns.length > 0)
				query.selectColumns(queryColumns);
			query.setPage(safePageSize, safePage);
			List<PO> list = query.list();
			if (list != null) {
				for (PO po : list)
					rows.add(GraphQLQueryBuilder.toLowerCaseKeys(
							GraphQLQueryBuilder.poToMap(po, tableName, selectedColumns)));
			}
		}

		int totalPages = totalRecords <= 0 ? 0 : (int) Math.ceil((double) totalRecords / (double) safePageSize);

		Map<String, Object> response = new java.util.LinkedHashMap<>();
		response.put("results", rows);
		response.put("totalRecords", Integer.valueOf(totalRecords));
		response.put("totalPages", Integer.valueOf(totalPages));
		response.put("page", Integer.valueOf(safePage));
		response.put("pageSize", Integer.valueOf(safePageSize));
		return response;
	}

	@SuppressWarnings("unchecked")
	private Object resolveCreate(DataFetchingEnvironment env, String tableName) {
		Map<String, Object> model = env.getArgument("model");
		List<Map<String, Object>> values = (List<Map<String, Object>>) model.get("values");
		String trxId = env.getArgument("trxId");
		return GraphQLQueryBuilder.createModel(tableName, values, trxId);
	}

	@SuppressWarnings("unchecked")
	private Object resolveUpdate(DataFetchingEnvironment env, String tableName) {
		Map<String, Object> model = env.getArgument("model");
		Map<String, Object> where = (Map<String, Object>) model.get("where");
		List<Map<String, Object>> values = (List<Map<String, Object>>) model.get("values");
		String trxId = env.getArgument("trxId");
		return GraphQLQueryBuilder.updateModel(tableName, values, where, trxId);
	}

	@SuppressWarnings("unchecked")
	private Object resolveDelete(DataFetchingEnvironment env, String tableName) {
		Map<String, Object> model = env.getArgument("model");
		Map<String, Object> where = (Map<String, Object>) model.get("where");
		String trxId = env.getArgument("trxId");
		return GraphQLQueryBuilder.deleteModel(tableName, where, trxId);
	}
}
