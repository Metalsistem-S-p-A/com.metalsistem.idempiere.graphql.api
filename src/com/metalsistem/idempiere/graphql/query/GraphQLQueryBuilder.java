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
package com.metalsistem.idempiere.graphql.query;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.ws.rs.core.Response.Status;

import org.compiere.model.MColumn;
import org.compiere.model.GridField;
import org.compiere.model.GridFieldVO;
import org.compiere.model.MRole;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Util;

import com.metalsistem.idempiere.graphql.api.util.GraphQLUtils;
import com.metalsistem.idempiere.graphql.api.util.IDempiereGraphQLException;

public class GraphQLQueryBuilder {

	private GraphQLQueryBuilder() {
	}

	private static Object convertValue(String tableName, String columnName, Object value) {
		if (value == null)
			return null;

		MTable table = MTable.get(Env.getCtx(), tableName);
		if (table == null)
			return value;

		MColumn column = table.getColumn(columnName);
		if (column == null)
			return value;

		int displayType = column.getAD_Reference_ID();

		try {
			switch (displayType) {
			case DisplayType.Integer:
			case DisplayType.ID:
			case DisplayType.TableDir:
			case DisplayType.Table:
			case DisplayType.Search:
			case DisplayType.Location:
			case DisplayType.Locator:
			case DisplayType.Account:
			case DisplayType.PAttribute:
			case DisplayType.Image:
			case DisplayType.Color:
			case DisplayType.Button:
			case DisplayType.Assignment:
				if (value instanceof Number)
					return Integer.valueOf(((Number) value).intValue());
				return Integer.valueOf(String.valueOf(value));

			case DisplayType.Amount:
			case DisplayType.Number:
			case DisplayType.CostPrice:
			case DisplayType.Quantity:
				if (value instanceof BigDecimal)
					return value;
				if (value instanceof Number)
					return BigDecimal.valueOf(((Number) value).doubleValue());
				return new BigDecimal(String.valueOf(value));

			case DisplayType.Date:
			case DisplayType.DateTime:
			case DisplayType.Time:
				if (value instanceof Timestamp)
					return value;
				if (value instanceof java.util.Date)
					return new Timestamp(((java.util.Date) value).getTime());
				String text = String.valueOf(value);
				if (text.contains("T"))
					text = text.replace("T", " ");
				if (text.length() == 10)
					text = text + " 00:00:00";
				return Timestamp.valueOf(text);

			case DisplayType.YesNo:
				if (value instanceof Boolean)
					return ((Boolean) value) ? "Y" : "N";
				String yn = String.valueOf(value);
				if ("true".equalsIgnoreCase(yn) || "y".equalsIgnoreCase(yn) || "1".equals(yn))
					return "Y";
				if ("false".equalsIgnoreCase(yn) || "n".equalsIgnoreCase(yn) || "0".equals(yn))
					return "N";
				return yn;

			default:
				return value;
			}
		} catch (Exception ex) {
			return value;
		}
	}

	public static String buildWhereClause(String tableName, Map<String, Object> filterInput, List<Object> parameters) {
		return buildWhereClause(tableName, null, filterInput, parameters);
	}

	public static String buildWhereClause(String tableName, String tableAlias, Map<String, Object> filterInput, List<Object> parameters) {
		if (filterInput == null || filterInput.isEmpty())
			return "";

		if (filterInput.containsKey("and")) {
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> andFilters = (List<Map<String, Object>>) filterInput.get("and");
			List<String> clauses = new ArrayList<>();
			if (andFilters != null) {
				for (Map<String, Object> f : andFilters) {
					if (f != null && !f.isEmpty()) {
						String clause = buildWhereClause(tableName, tableAlias, f, parameters);
						if (!Util.isEmpty(clause, true))
							clauses.add("(" + clause + ")");
					}
				}
			}
			return String.join(" AND ", clauses);
		}

		if (filterInput.containsKey("or")) {
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> orFilters = (List<Map<String, Object>>) filterInput.get("or");
			List<String> clauses = new ArrayList<>();
			if (orFilters != null) {
				for (Map<String, Object> f : orFilters) {
					if (f != null && !f.isEmpty()) {
						String clause = buildWhereClause(tableName, tableAlias, f, parameters);
						if (!Util.isEmpty(clause, true))
							clauses.add("(" + clause + ")");
					}
				}
			}
			return String.join(" OR ", clauses);
		}

		if (filterInput.containsKey("not")) {
			@SuppressWarnings("unchecked")
			Map<String, Object> notFilter = (Map<String, Object>) filterInput.get("not");
			if (notFilter != null && !notFilter.isEmpty()) {
				String clause = buildWhereClause(tableName, tableAlias, notFilter, parameters);
				if (!Util.isEmpty(clause, true))
					return "NOT (" + clause + ")";
			}
		}

		String field = (String) filterInput.get("field");
		String operator = (String) filterInput.get("operator");
		Object value = filterInput.get("value");

		if (Util.isEmpty(field, true) || Util.isEmpty(operator, true))
			return "";

		// If field contains alias (e.g., "asi.LegacyCode"), extract column name for type conversion
		String columnForConversion = field;
		String tableForConversion = tableName;
		boolean hasExplicitCast = field.contains("::");
		if (field.contains(".")) {
			String[] parts = field.split("\\.", 2);
			String qualifier = parts[0] == null ? "" : parts[0].trim();
			String extractedColumn = extractSimpleColumnName(parts[1]);

			if (extractedColumn != null) {
				columnForConversion = extractedColumn;
			} else {
				tableForConversion = null;
			}

			String resolvedTable = resolveTableForConversion(qualifier, tableName, tableAlias);
			if (!hasExplicitCast && resolvedTable != null && extractedColumn != null)
				tableForConversion = resolvedTable;
			else
				tableForConversion = null;
		} else if (hasExplicitCast) {
			// If caller explicitly casts the field in SQL, don't force Java-side conversion
			tableForConversion = null;
			columnForConversion = extractSimpleColumnName(field);
		}

		Object typedValue = tableForConversion != null ? convertValue(tableForConversion, columnForConversion, value) : value;
		switch (operator.toLowerCase()) {
		case "eq":
			if (typedValue == null)
				return field + " IS NULL";
			parameters.add(typedValue);
			return field + "=?";
		case "neq":
			if (typedValue == null)
				return field + " IS NOT NULL";
			parameters.add(typedValue);
			return field + "!=?";
		case "gt":
			parameters.add(typedValue);
			return field + ">?";
		case "ge":
			parameters.add(typedValue);
			return field + ">=?";
		case "lt":
			parameters.add(typedValue);
			return field + "<?";
		case "le":
			parameters.add(typedValue);
			return field + "<=?";
		case "in":
			List<?> values;
			if (value instanceof List<?>) {
				values = (List<?>) value;
			} else if (value instanceof String) {
				String[] split = ((String) value).split(",");
				List<String> tmp = new ArrayList<>(split.length);
				for (String v : split)
					tmp.add(v.trim());
				values = tmp;
			} else {
				values = java.util.Collections.singletonList(value);
			}
			if (values.isEmpty())
				return "1=0";
			List<String> placeholders = new ArrayList<>();
			for (Object v : values) {
				Object convertedValue = tableForConversion != null ? convertValue(tableForConversion, columnForConversion, v) : v;
				parameters.add(convertedValue);
				placeholders.add("?");
			}
			return field + " IN (" + String.join(",", placeholders) + ")";
		case "contains":
			parameters.add("%" + String.valueOf(value) + "%");
			return "UPPER(" + field + ") LIKE UPPER(?)";
		case "startswith":
			parameters.add(String.valueOf(value) + "%");
			return "UPPER(" + field + ") LIKE UPPER(?)";
		case "endswith":
			parameters.add("%" + String.valueOf(value));
			return "UPPER(" + field + ") LIKE UPPER(?)";
		default:
			return "";
		}
	}

	private static String resolveTableForConversion(String qualifier, String baseTableName) {
		return resolveTableForConversion(qualifier, baseTableName, null);
	}

	private static String resolveTableForConversion(String qualifier, String baseTableName, String tableAlias) {
		if (Util.isEmpty(qualifier, true))
			return null;

		if (qualifier.equalsIgnoreCase(baseTableName))
			return baseTableName;

		if (!Util.isEmpty(tableAlias, true) && qualifier.equalsIgnoreCase(tableAlias))
			return baseTableName;

		MTable table = MTable.get(Env.getCtx(), qualifier);
		if (table != null && table.getAD_Table_ID() > 0)
			return table.getTableName();

		return null;
	}

	private static String extractSimpleColumnName(String expression) {
		if (Util.isEmpty(expression, true))
			return null;

		String token = expression.trim();

		int castIdx = token.indexOf("::");
		if (castIdx >= 0)
			token = token.substring(0, castIdx).trim();

		int spaceIdx = token.indexOf(' ');
		if (spaceIdx >= 0)
			token = token.substring(0, spaceIdx).trim();

		if (token.matches("[A-Za-z0-9_]+"))
			return token;

		return null;
	}

	public static String buildOrderBy(List<Map<String, Object>> orderByList) {
		if (orderByList == null || orderByList.isEmpty())
			return "";

		List<String> clauses = new ArrayList<>();
		for (Map<String, Object> orderBy : orderByList) {
			String field = (String) orderBy.get("field");
			String direction = (String) orderBy.get("direction");
			if (Util.isEmpty(field, true))
				continue;
			if (direction == null || "asc".equalsIgnoreCase(direction))
				clauses.add(field + " ASC");
			else
				clauses.add(field + " DESC");
		}
		return String.join(", ", clauses);
	}

	/**
	 * Build a Query with optional JOINs
	 * 
	 * @param tableName   Base table name
	 * @param filter      Filter map
	 * @param joinSpecs   List of join specifications (table, alias, on)
	 * @param orderByList Order by list
	 * @param onlyActive  Only active records
	 * @param trxName     Transaction name
	 * @return Configured Query object
	 */
	public static Query buildQuery(String tableName, Map<String, Object> filter, List<Map<String, Object>> joinSpecs,
			List<Map<String, Object>> orderByList, boolean onlyActive, String trxName) {
		List<Object> parameters = new ArrayList<>();
		String whereClause = buildWhereClause(tableName, filter, parameters);
		if (Util.isEmpty(whereClause, true)) {
			whereClause = "";
		}

		Query query = new Query(Env.getCtx(), tableName, whereClause, trxName)
				.setApplyAccessFilter(true, false)
				.setParameters(parameters)
				.setOnlyActiveRecords(onlyActive);

		// Add JOIN clauses if specified
		if (joinSpecs != null && !joinSpecs.isEmpty()) {
			for (Map<String, Object> joinSpec : joinSpecs) {
				String joinTable = stringValue(joinSpec.get("table"));
				if (Util.isEmpty(joinTable, true))
					continue;

				// Validate access to joined table
				GraphQLUtils.getTableAndCheckAccess(joinTable, false);

				String joinType = stringValue(joinSpec.get("type"));
				if (Util.isEmpty(joinType, true)) {
					joinType = "INNER";
				} else {
					joinType = joinType.toUpperCase();
				}

				String alias = stringValue(joinSpec.get("alias"));
				boolean hasUserAlias = !Util.isEmpty(alias, true);
				String effectiveAlias = hasUserAlias ? alias : joinTable;

				String onCondition = stringValue(joinSpec.get("on"));
				
				// CROSS JOIN doesn't require ON condition
				if ("CROSS".equalsIgnoreCase(joinType)) {
					query.addJoinClause("CROSS JOIN " + joinTable + " " + effectiveAlias);
				} else {
					// For other JOIN types, ON condition is required (or auto-detected)
					if (Util.isEmpty(onCondition, true)) {
						// Auto-detect FK relationship
						onCondition = autoDetectJoinCondition(tableName, joinTable, effectiveAlias);
					} else if (!hasUserAlias) {
						onCondition = replaceJoinQualifier(onCondition, joinTable, effectiveAlias);
					}

					if (!Util.isEmpty(onCondition, true)) {
						// Build JOIN clause: {LEFT|RIGHT|INNER|FULL} JOIN table alias ON condition
						query.addJoinClause(joinType + " JOIN " + joinTable + " " + effectiveAlias + " ON " + onCondition);
					}
				}
			}
		}

		String orderBy = buildOrderBy(orderByList);
		if (!Util.isEmpty(orderBy, true))
			query.setOrderBy(orderBy);

		return query;
	}

	/**
	 * Build a Query without JOINs (backward compatibility)
	 */
	public static Query buildQuery(String tableName, Map<String, Object> filter, List<Map<String, Object>> orderByList,
			boolean onlyActive, String trxName) {
		return buildQuery(tableName, filter, null, orderByList, onlyActive, trxName);
	}

	/**
	 * Auto-detect JOIN condition based on FK relationships
	 * 
	 * @param baseTable Base table name
	 * @param joinTable Table to join
	 * @param alias     Alias for the joined table
	 * @return JOIN ON condition or empty string if cannot detect
	 */
	private static String autoDetectJoinCondition(String baseTable, String joinTable, String joinReference) {
		return autoDetectJoinCondition(baseTable, null, joinTable, joinReference);
	}

	private static String autoDetectJoinCondition(String baseTable, String baseAlias, String joinTable, String joinReference) {
		// Try to find FK column in base table pointing to join table
		MTable base = MTable.get(Env.getCtx(), baseTable);
		if (base == null)
			return "";

		String baseQualifier = !Util.isEmpty(baseAlias, true) ? baseAlias : baseTable;

		// Look for column named <JoinTable>_ID in base table
		String fkColumnName = joinTable + "_ID";
		MColumn fkColumn = base.getColumn(fkColumnName);
		if (fkColumn != null) {
			// Assuming PK of joined table is <JoinTable>_ID
			return baseQualifier + "." + fkColumnName + "=" + joinReference + "." + fkColumnName;
		}

		return "";
	}

	private static String replaceJoinQualifier(String condition, String tableName, String alias) {
		if (Util.isEmpty(condition, true) || Util.isEmpty(tableName, true) || Util.isEmpty(alias, true))
			return condition;
		String regex = "(?i)\\b" + Pattern.quote(tableName) + "\\.";
		return condition.replaceAll(regex, alias + ".");
	}

	private static String buildJoinsSql(String tableName, List<Map<String, Object>> joinSpecs) {
		return buildJoinsSql(tableName, null, joinSpecs);
	}

	private static String buildJoinsSql(String tableName, String tableAlias, List<Map<String, Object>> joinSpecs) {
		if (joinSpecs == null || joinSpecs.isEmpty())
			return "";
		StringBuilder joins = new StringBuilder();
		for (Map<String, Object> joinSpec : joinSpecs) {
			String joinTable = stringValue(joinSpec.get("table"));
			if (Util.isEmpty(joinTable, true))
				continue;
			String joinType = stringValue(joinSpec.get("type"));
			if (Util.isEmpty(joinType, true)) joinType = "INNER";
			else joinType = joinType.toUpperCase();
			String alias = stringValue(joinSpec.get("alias"));
			boolean hasUserAlias = !Util.isEmpty(alias, true);
			String effectiveAlias = hasUserAlias ? alias : joinTable;
			String onCondition = stringValue(joinSpec.get("on"));
			if ("CROSS".equalsIgnoreCase(joinType)) {
				joins.append(" CROSS JOIN ").append(joinTable).append(' ').append(effectiveAlias);
			} else {
				if (Util.isEmpty(onCondition, true))
					onCondition = autoDetectJoinCondition(tableName, tableAlias, joinTable, effectiveAlias);
				else if (!hasUserAlias)
					onCondition = replaceJoinQualifier(onCondition, joinTable, effectiveAlias);
				if (!Util.isEmpty(onCondition, true)) {
					joins.append(' ').append(joinType).append(" JOIN ")
						 .append(joinTable).append(' ').append(effectiveAlias)
						 .append(" ON ").append(onCondition);
				}
			}
		}
		return joins.toString();
	}

	public static int countWithJoins(String tableName, Map<String, Object> filter,
			List<Map<String, Object>> joinSpecs, boolean onlyActive, String trxName) {
		return countWithJoins(tableName, filter, joinSpecs, onlyActive, trxName, null);
	}

	public static int countWithJoins(String tableName, Map<String, Object> filter,
			List<Map<String, Object>> joinSpecs, boolean onlyActive, String trxName, String tableAlias) {

		String effectiveAlias = Util.isEmpty(tableAlias, true) ? null : tableAlias;
		List<Object> parameters = new ArrayList<>();
		String whereClause = buildWhereClause(tableName, effectiveAlias, filter, parameters);
		if (onlyActive) {
			String qualifier = effectiveAlias != null ? effectiveAlias : tableName;
			String activeClause = qualifier + ".IsActive='Y'";
			whereClause = Util.isEmpty(whereClause, true)
					? activeClause : "(" + whereClause + ") AND " + activeClause;
		}

		String fromClause = tableName + (effectiveAlias != null ? " " + effectiveAlias : "");
		String baseSql = "SELECT COUNT(*) FROM " + fromClause
				+ buildJoinsSql(tableName, effectiveAlias, joinSpecs)
				+ (Util.isEmpty(whereClause, true) ? "" : " WHERE " + whereClause);

		String accessQualifier = effectiveAlias != null ? effectiveAlias : tableName;
		String finalSql = MRole.getDefault(Env.getCtx(), false)
				.addAccessSQL(baseSql, accessQualifier, MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO);

		try (PreparedStatement pstmt = DB.prepareStatement(finalSql, trxName)) {
			for (int i = 0; i < parameters.size(); i++)
				pstmt.setObject(i + 1, parameters.get(i));
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) return rs.getInt(1);
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex.getMessage(), ex);
		}
		return 0;
	}

	public static List<Map<String, Object>> listWithJoins(String tableName, String[] selectedColumns,
			Map<String, Object> filter, List<Map<String, Object>> joinSpecs,
			List<Map<String, Object>> orderByList, boolean onlyActive, int pageSize, int page,
			String trxName) {
		return listWithJoins(tableName, selectedColumns, filter, joinSpecs, orderByList, onlyActive, pageSize, page, trxName, null);
	}

	public static List<Map<String, Object>> listWithJoins(String tableName, String[] selectedColumns,
			Map<String, Object> filter, List<Map<String, Object>> joinSpecs,
			List<Map<String, Object>> orderByList, boolean onlyActive, int pageSize, int page,
			String trxName, String tableAlias) {

		String effectiveAlias = Util.isEmpty(tableAlias, true) ? null : tableAlias;
		List<Object> parameters = new ArrayList<>();
		String whereClause = buildWhereClause(tableName, effectiveAlias, filter, parameters);
		if (onlyActive) {
			String qualifier = effectiveAlias != null ? effectiveAlias : tableName;
			String activeClause = qualifier + ".IsActive='Y'";
			whereClause = Util.isEmpty(whereClause, true)
					? activeClause : "(" + whereClause + ") AND " + activeClause;
		}

		String selectList = (selectedColumns == null || selectedColumns.length == 0)
				? (effectiveAlias != null ? effectiveAlias : tableName) + ".*"
				: String.join(", ", selectedColumns);

		String fromClause = tableName + (effectiveAlias != null ? " " + effectiveAlias : "");
		StringBuilder baseSql = new StringBuilder("SELECT ").append(selectList)
				.append(" FROM ").append(fromClause)
				.append(buildJoinsSql(tableName, effectiveAlias, joinSpecs));
		if (!Util.isEmpty(whereClause, true))
			baseSql.append(" WHERE ").append(whereClause);

		String accessQualifier = effectiveAlias != null ? effectiveAlias : tableName;
		String filteredSql = MRole.getDefault(Env.getCtx(), false)
				.addAccessSQL(baseSql.toString(), accessQualifier, MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO);

		StringBuilder finalSql = new StringBuilder(filteredSql);
		String orderBy = buildOrderBy(orderByList);
		if (!Util.isEmpty(orderBy, true))
			finalSql.append(" ORDER BY ").append(orderBy);
		finalSql.append(" LIMIT ").append(pageSize).append(" OFFSET ").append((long) page * pageSize);

		List<Map<String, Object>> results = new ArrayList<>();
		try (PreparedStatement pstmt = DB.prepareStatement(finalSql.toString(), trxName)) {
			for (int i = 0; i < parameters.size(); i++)
				pstmt.setObject(i + 1, parameters.get(i));
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					Map<String, Object> row = new LinkedHashMap<>();
					for (int i = 0; i < selectedColumns.length; i++)
						row.put(selectedColumns[i], rs.getObject(i + 1));
					results.add(row);
				}
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex.getMessage(), ex);
		}
		return results;
	}

	/**
	 * Parse ORDER BY string into a list of order specification maps
	 * 
	 * @param orderBy String like "Name ASC, Created DESC" or "Name, Created DESC"
	 * @return List of maps with "field" and "direction" keys
	 */
	public static List<Map<String, Object>> parseOrderBy(String orderBy) {
		if (Util.isEmpty(orderBy, true))
			return null;
		
		String[] parts = orderBy.split(",");
		List<Map<String, Object>> result = new ArrayList<>();
		
		for (String part : parts) {
			if (Util.isEmpty(part, true))
				continue;
			
			part = part.trim();
			String[] tokens = part.split("\\s+");
			
			Map<String, Object> orderSpec = new LinkedHashMap<>();
			orderSpec.put("field", tokens[0]);
			
			if (tokens.length > 1 && "DESC".equalsIgnoreCase(tokens[1])) {
				orderSpec.put("direction", "desc");
			} else {
				orderSpec.put("direction", "asc");
			}
			
			result.add(orderSpec);
		}
		
		return result.isEmpty() ? null : result;
	}

	/**
	 * Split a comma-separated string into a list of trimmed values
	 * 
	 * @param csv Comma-separated string
	 * @return List of trimmed values, or null if input is empty
	 */
	public static List<String> splitCsv(String csv) {
		if (Util.isEmpty(csv, true))
			return null;
		String[] split = csv.split(",");
		List<String> out = new ArrayList<>();
		for (String s : split) {
			if (!Util.isEmpty(s, true))
				out.add(s.trim());
		}
		return out;
	}

	/**
	 * Extract columns that belong to the base table and are safe to pass to
	 * Query.selectColumns (unqualified).
	 *
	 * @param tableName       Base table name
	 * @param selectedColumns Validated selected columns (possibly qualified)
	 * @return Unqualified base-table column names plus key columns
	 */
	public static String[] extractBaseTableColumnsForQuery(String tableName, String[] selectedColumns) {
		MTable table = MTable.get(Env.getCtx(), tableName);
		if (table == null || table.getAD_Table_ID() <= 0)
			return selectedColumns == null ? new String[0] : selectedColumns;

		Set<String> baseColumns = new LinkedHashSet<>();
		if (selectedColumns != null) {
			for (String selected : selectedColumns) {
				if (Util.isEmpty(selected, true))
					continue;
				String normalized = selected.trim();
				if (!normalized.contains(".")) {
					baseColumns.add(normalized);
					continue;
				}

				String[] parts = normalized.split("\\.", 2);
				String qualifier = parts[0].trim();
				String column = parts[1].trim();
				if (qualifier.equalsIgnoreCase(tableName))
					baseColumns.add(column);
			}
		}

		String[] keys = table.getKeyColumns();
		if (keys != null) {
			for (String key : keys) {
				if (!Util.isEmpty(key, true))
					baseColumns.add(key);
			}
		}

		return baseColumns.toArray(new String[0]);
	}

	/**
	 * Convert an object to Integer
	 * 
	 * @param value Value to convert
	 * @return Integer representation, or null
	 */
	public static Integer asInteger(Object value) {
		if (value == null)
			return null;
		if (value instanceof Number)
			return Integer.valueOf(((Number) value).intValue());
		return Integer.valueOf(String.valueOf(value));
	}

	/**
	 * Convert an object to String
	 * 
	 * @param value Value to convert
	 * @return String representation, or null
	 */
	public static String stringValue(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	/**
	 * Resolve and validate selected columns for a table, applying role access checks
	 * 
	 * @param tableName      Table name
	 * @param selectColumns  List of column names to select, or null for all readable
	 *                       columns
	 * @return Array of validated column names the user can access
	 */
	public static String[] resolveSelectedColumns(String tableName, List<String> selectColumns) {
		return resolveSelectedColumns(tableName, selectColumns, null);
	}

	/**
	 * Resolve and validate selected columns for a table (and optional joined tables),
	 * applying role access checks.
	 *
	 * @param tableName     Base table name
	 * @param selectColumns List of column names to select, or null for defaults
	 * @param joinSpecs     Optional JOIN specifications
	 * @return Array of validated column names the user can access
	 */
	public static String[] resolveSelectedColumns(String tableName, List<String> selectColumns,
			List<Map<String, Object>> joinSpecs) {
		return resolveSelectedColumns(tableName, selectColumns, joinSpecs, null);
	}

	public static String[] resolveSelectedColumns(String tableName, List<String> selectColumns,
			List<Map<String, Object>> joinSpecs, String tableAlias) {
		MTable table = MTable.get(Env.getCtx(), tableName);
		if (table == null || table.getAD_Table_ID() <= 0)
			throw new IDempiereGraphQLException("Invalid table name", "No match found for table name: " + tableName,
					Status.NOT_FOUND);

		Map<String, String> joinTargetToTable = buildJoinTargetToTable(tableName, tableAlias, joinSpecs);

		if (selectColumns == null || selectColumns.isEmpty()) {
			if (joinSpecs != null && !joinSpecs.isEmpty()) {
				List<String> allColumns = new ArrayList<>();
				allColumns.addAll(getReadableColumnsForTable(tableName, tableName));
				for (Map<String, Object> joinSpec : joinSpecs) {
					String joinTable = stringValue(joinSpec.get("table"));
					if (Util.isEmpty(joinTable, true))
						continue;
					String alias = stringValue(joinSpec.get("alias"));
					String qualifier = Util.isEmpty(alias, true) ? joinTable : alias;
					allColumns.addAll(getReadableColumnsForTable(joinTable, qualifier));
				}
				return allColumns.toArray(new String[0]);
			}

			MColumn[] columns = table.getColumns(false);
			List<String> readableColumns = new ArrayList<>();
			for (MColumn column : columns) {
				if (GraphQLUtils.hasRoleColumnAccess(table.getAD_Table_ID(), column.getAD_Column_ID(), true))
					readableColumns.add(column.getColumnName());
			}
			return readableColumns.toArray(new String[0]);
		}

		List<String> selected = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (String columnName : selectColumns) {
			if (Util.isEmpty(columnName, true))
				continue;
			String normalized = columnName.trim();
			String target = null;
			String effectiveColumn = normalized;
			if (normalized.contains(".")) {
				String[] parts = normalized.split("\\.", 2);
				target = parts[0].trim();
				effectiveColumn = parts[1].trim();
			}

			String resolvedTableName = tableName;
			if (!Util.isEmpty(target, true)) {
				resolvedTableName = joinTargetToTable.get(target.toLowerCase());
				if (Util.isEmpty(resolvedTableName, true)) {
					throw new IDempiereGraphQLException("Invalid column",
							"No match found for column name: " + normalized, Status.BAD_REQUEST);
				}
			}

			MTable resolvedTable = MTable.get(Env.getCtx(), resolvedTableName);
			if (resolvedTable == null || resolvedTable.getAD_Table_ID() <= 0)
				throw new IDempiereGraphQLException("Invalid table name",
						"No match found for table name: " + resolvedTableName, Status.NOT_FOUND);

			MColumn column = resolvedTable.getColumn(effectiveColumn);
			if (column == null)
				throw new IDempiereGraphQLException("Invalid column",
						"No match found for column name: " + normalized, Status.BAD_REQUEST);
			if (!GraphQLUtils.hasRoleColumnAccess(resolvedTable.getAD_Table_ID(), column.getAD_Column_ID(), true))
				throw new IDempiereGraphQLException("Access denied", "Access denied for column: " + normalized,
						Status.FORBIDDEN);

			String canonical;
			if (Util.isEmpty(target, true)) {
				canonical = column.getColumnName();
			} else {
				canonical = target + "." + column.getColumnName();
			}

			if (seen.add(canonical))
				selected.add(canonical);
		}
		return selected.toArray(new String[0]);
	}

	private static List<String> getReadableColumnsForTable(String tableName, String qualifier) {
		MTable table = MTable.get(Env.getCtx(), tableName);
		if (table == null || table.getAD_Table_ID() <= 0)
			return Collections.emptyList();

		List<String> readable = new ArrayList<>();
		MColumn[] columns = table.getColumns(false);
		for (MColumn column : columns) {
			if (GraphQLUtils.hasRoleColumnAccess(table.getAD_Table_ID(), column.getAD_Column_ID(), true)) {
				if (Util.isEmpty(qualifier, true))
					readable.add(column.getColumnName());
				else
					readable.add(qualifier + "." + column.getColumnName());
			}
		}
		return readable;
	}

	private static Map<String, String> buildJoinTargetToTable(String baseTableName, List<Map<String, Object>> joinSpecs) {
		return buildJoinTargetToTable(baseTableName, null, joinSpecs);
	}

	private static Map<String, String> buildJoinTargetToTable(String baseTableName, String tableAlias, List<Map<String, Object>> joinSpecs) {
		Map<String, String> targetToTable = new HashMap<>();
		targetToTable.put(baseTableName.toLowerCase(), baseTableName);
		if (!Util.isEmpty(tableAlias, true))
			targetToTable.put(tableAlias.toLowerCase(), baseTableName);

		if (joinSpecs == null || joinSpecs.isEmpty())
			return targetToTable;

		for (Map<String, Object> joinSpec : joinSpecs) {
			String joinTable = stringValue(joinSpec.get("table"));
			if (Util.isEmpty(joinTable, true))
				continue;
			targetToTable.put(joinTable.toLowerCase(), joinTable);

			String alias = stringValue(joinSpec.get("alias"));
			if (!Util.isEmpty(alias, true))
				targetToTable.put(alias.toLowerCase(), joinTable);
		}

		return targetToTable;
	}

	/**
	 * Convert a PO to a Map with selected columns
	 * 
	 * @param po              PO to convert
	 * @param tableName       Table name
	 * @param selectedColumns Array of column names to include, or null for none
	 * @return Map with id, uid (if exists), and selected column values
	 */
	public static Map<String, Object> poToMap(PO po, String tableName, String[] selectedColumns) {
		return poToMap(po, tableName, selectedColumns, null, null);
	}

	/**
	 * Convert a PO to a Map with selected columns, supporting joined table columns.
	 *
	 * @param po              PO to convert
	 * @param tableName       Base table name
	 * @param selectedColumns Array of column names to include
	 * @param joinSpecs       Optional JOIN specifications
	 * @param trxName         Optional transaction name
	 * @return Map with selected column values
	 */
	public static Map<String, Object> poToMap(PO po, String tableName, String[] selectedColumns,
			List<Map<String, Object>> joinSpecs, String trxName) {
		Map<String, Object> row = new LinkedHashMap<>();

		if (selectedColumns != null) {
			for (String columnName : selectedColumns) {
				if (Util.isEmpty(columnName, true))
					continue;

				String normalized = columnName.trim();
				if (!normalized.contains(".")) {
					row.put(normalized, po.get_Value(normalized));
					continue;
				}

				String[] parts = normalized.split("\\.", 2);
				String qualifier = parts[0].trim();
				String column = parts[1].trim();

				if (qualifier.equalsIgnoreCase(tableName)) {
					row.put(normalized, po.get_Value(column));
					continue;
				}

				Object joinedValue = fetchJoinedColumnValue(po, tableName, joinSpecs, qualifier, column, trxName);
				row.put(normalized, joinedValue);
			}
		}
		return row;
	}

	private static Object fetchJoinedColumnValue(PO po, String baseTableName, List<Map<String, Object>> joinSpecs,
			String qualifier, String columnName, String trxName) {
		if (joinSpecs == null || joinSpecs.isEmpty())
			return null;

		Map<String, String> targetToTable = buildJoinTargetToTable(baseTableName, joinSpecs);
		String resolvedTable = targetToTable.get(qualifier.toLowerCase());
		if (Util.isEmpty(resolvedTable, true))
			return null;

		StringBuilder sql = new StringBuilder("SELECT ");
		sql.append(qualifier).append('.').append(columnName)
				.append(" FROM ").append(baseTableName).append(' ');

		for (Map<String, Object> joinSpec : joinSpecs) {
			String joinTable = stringValue(joinSpec.get("table"));
			if (Util.isEmpty(joinTable, true))
				continue;

			String joinType = stringValue(joinSpec.get("type"));
			if (Util.isEmpty(joinType, true))
				joinType = "INNER";
			else
				joinType = joinType.toUpperCase();

			String alias = stringValue(joinSpec.get("alias"));
			String effectiveAlias = Util.isEmpty(alias, true) ? joinTable : alias;

			String onCondition = stringValue(joinSpec.get("on"));
			if (!"CROSS".equalsIgnoreCase(joinType) && Util.isEmpty(onCondition, true)) {
				onCondition = autoDetectJoinCondition(baseTableName, joinTable, effectiveAlias);
			}

			sql.append(' ').append(joinType).append(" JOIN ").append(joinTable).append(' ').append(effectiveAlias);
			if (!"CROSS".equalsIgnoreCase(joinType) && !Util.isEmpty(onCondition, true)) {
				sql.append(" ON ").append(onCondition);
			}
		}

		String[] keyColumns = po.get_KeyColumns();
		if (keyColumns == null || keyColumns.length == 0)
			return null;

		sql.append(" WHERE ").append(baseTableName).append('.').append(keyColumns[0]).append("=?");

		try (PreparedStatement pstmt = DB.prepareStatement(sql.toString(), trxName)) {
			pstmt.setInt(1, po.get_ID());
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next())
					return rs.getObject(1);
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex.getMessage(), ex);
		}

		return null;
	}

	/**
	 * Convert all map keys to lowercase
	 * 
	 * @param row Map with mixed-case keys
	 * @return New map with all lowercase keys
	 */
	public static Map<String, Object> toLowerCaseKeys(Map<String, Object> row) {
		Map<String, Object> normalized = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : row.entrySet()) {
			String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase();
			normalized.put(key, entry.getValue());
		}
		return normalized;
	}

	/**
	 * Create a new record in the specified table.
	 * 
	 * @param tableName Name of the table
	 * @param values List of column-value pairs to insert
	 * @return Map representing the newly created record
	 */
	public static Map<String, Object> createModel(String tableName, List<Map<String, Object>> values) {
		return createModel(tableName, values, null);
	}

	/**
	 * Create a new record in the specified table with optional transaction.
	 * 
	 * @param tableName Name of the table
	 * @param values List of column-value pairs to insert
	 * @param trxName Transaction name (optional)
	 * @return Map representing the newly created record
	 */
	public static Map<String, Object> createModel(String tableName, List<Map<String, Object>> values, String trxName) {
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("At least one column value must be provided");
		}

		// Create new PO instance
		MTable table = MTable.get(Env.getCtx(), tableName);
		if (table == null || table.getAD_Table_ID() == 0) {
			throw new IllegalArgumentException("Table not found: " + tableName);
		}

		PO po = table.isUUIDKeyTable() ? table.getPOByUU(PO.UUID_NEW_RECORD, trxName) : table.getPO(0, trxName);
		if (po == null && trxName != null) {
			po = table.isUUIDKeyTable() ? table.getPOByUU(PO.UUID_NEW_RECORD, null) : table.getPO(0, null);
		}
		if (po == null) {
			throw new IllegalArgumentException("Cannot create PO for table: " + tableName);
		}
		if (trxName != null)
			po.set_TrxName(trxName);

		Map<String, Object> providedValues = normalizeProvidedValues(table, po, values);

		MColumn[] allColumns = table.getColumns(false);
		if (allColumns != null) {
			for (MColumn column : allColumns) {
				if (column == null || column.isVirtualColumn())
					continue;

				String columnName = column.getColumnName();
				if (Util.isEmpty(columnName, true))
					continue;

				Object providedValue = providedValues.get(columnName);
				if (providedValues.containsKey(columnName)) {
					setTypedColumnValue(po, tableName, column, providedValue);
					continue;
				}

				setDefaultValue(po, column);
			}
		}

		// Save the record
		try {
			po.saveEx();
		} catch (Exception ex) {
			throw buildOperationException("create", tableName, po, ex);
		}

		// Return the created record as a Map
		String[] responseColumns = resolveSelectedColumns(tableName, Collections.emptyList());
		Map<String, Object> result = poToMap(po, tableName, responseColumns);
		return toLowerCaseKeys(result);
	}

	/**
	 * Update records matching the WHERE clause.
	 * 
	 * @param tableName Name of the table
	 * @param values List of column-value pairs to update
	 * @param where WHERE clause condition
	 * @return Number of records updated
	 */
	public static int updateModel(String tableName, List<Map<String, Object>> values, Map<String, Object> where) {
		return updateModel(tableName, values, where, null);
	}

	/**
	 * Update records matching the WHERE clause using an optional transaction.
	 * 
	 * @param tableName Name of the table
	 * @param values List of column-value pairs to update
	 * @param where WHERE clause condition
	 * @param trxName Transaction name (optional)
	 * @return Number of records updated
	 */
	public static int updateModel(String tableName, List<Map<String, Object>> values, Map<String, Object> where,
			String trxName) {
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("At least one column value must be provided");
		}

		MTable table = MTable.get(Env.getCtx(), tableName);
		if (table == null || table.getAD_Table_ID() == 0) {
			throw new IllegalArgumentException("Table not found: " + tableName);
		}

		// Build query to find matching records
		Query query = buildQuery(tableName, where, null, null, true, trxName);
		List<PO> records = query.list();

		if (records == null || records.isEmpty()) {
			return 0;
		}

		int updatedCount = 0;

		// Update each record
		for (PO po : records) {
			if (trxName != null)
				po.set_TrxName(trxName);

			Map<String, Object> providedValues = normalizeProvidedValues(table, po, values);
			MColumn[] allColumns = table.getColumns(false);
			if (allColumns != null) {
				for (MColumn column : allColumns) {
					if (column == null || column.isVirtualColumn())
						continue;
					String columnName = column.getColumnName();
					if (Util.isEmpty(columnName, true))
						continue;
					if (!providedValues.containsKey(columnName))
						continue;
					setTypedColumnValue(po, tableName, column, providedValues.get(columnName));
				}
			}

			// Save the record
			try {
				po.saveEx();
				updatedCount++;
			} catch (Exception ex) {
				throw buildOperationException("update", tableName, po, ex);
			}
		}

		return updatedCount;
	}

	/**
	 * Delete records matching the WHERE clause.
	 * 
	 * @param tableName Name of the table
	 * @param where WHERE clause condition (required)
	 * @return Number of records deleted
	 */
	public static int deleteModel(String tableName, Map<String, Object> where) {
		return deleteModel(tableName, where, null);
	}

	/**
	 * Delete records matching the WHERE clause using an optional transaction.
	 * 
	 * @param tableName Name of the table
	 * @param where WHERE clause condition (required)
	 * @param trxName Transaction name (optional)
	 * @return Number of records deleted
	 */
	public static int deleteModel(String tableName, Map<String, Object> where, String trxName) {
		if (where == null || where.isEmpty()) {
			throw new IllegalArgumentException("WHERE clause is required for delete operations");
		}

		// Build query to find matching records
		Query query = buildQuery(tableName, where, null, null, true, trxName);
		List<PO> records = query.list();

		if (records == null || records.isEmpty()) {
			return 0;
		}

		int deletedCount = 0;

		// Delete each record
		for (PO po : records) {
			if (trxName != null)
				po.set_TrxName(trxName);
			try {
				if (po.delete(false)) {
					deletedCount++;
				} else {
					throw buildOperationException("delete", tableName, po, null);
				}
			} catch (RuntimeException ex) {
				throw buildOperationException("delete", tableName, po, ex);
			}
		}

		return deletedCount;
	}

	private static RuntimeException buildOperationException(String operation, String tableName, PO po, Exception ex) {
		StringBuilder message = new StringBuilder();
		message.append("Failed to ").append(operation).append(" record in ").append(tableName);

		if (po != null && po.get_ID() > 0)
			message.append(" (id=").append(po.get_ID()).append(')');

		String processMsg = extractProcessMessage(po);
		if (!Util.isEmpty(processMsg, true))
			message.append(": ").append(processMsg);

		String causeMessage = getDeepestCauseMessage(ex);
		if (!Util.isEmpty(causeMessage, true))
			message.append(" | cause: ").append(causeMessage);

		if (ex == null)
			return new RuntimeException(message.toString());
		return new RuntimeException(message.toString(), ex);
	}

	private static String getDeepestCauseMessage(Throwable throwable) {
		if (throwable == null)
			return null;

		Throwable current = throwable;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}

		if (!Util.isEmpty(current.getMessage(), true))
			return current.getMessage();

		return current.getClass().getName();
	}

	private static String extractProcessMessage(PO po) {
		if (po == null)
			return null;

		String[] candidates = new String[] { "getProcessMsg", "getProcessMessage" };
		for (String methodName : candidates) {
			try {
				Method method = po.getClass().getMethod(methodName);
				Object value = method.invoke(po);
				if (value != null) {
					String msg = String.valueOf(value);
					if (!Util.isEmpty(msg, true))
						return msg;
				}
			} catch (Exception ignore) {
				// Method not available in this platform version
			}
		}

		return null;
	}

	private static String resolveCanonicalColumnName(MTable table, String inputColumnName) {
		if (table == null || Util.isEmpty(inputColumnName, true))
			return null;

		MColumn column = table.getColumn(inputColumnName);
		if (column != null)
			return column.getColumnName();

		MColumn[] columns = table.getColumns(false);
		if (columns != null) {
			for (MColumn candidate : columns) {
				if (candidate != null && inputColumnName.equalsIgnoreCase(candidate.getColumnName()))
					return candidate.getColumnName();
			}
		}

		return inputColumnName;
	}

	private static void setDefaultValue(PO po, MColumn column) {		
		if (!column.isVirtualColumn() && !Util.isEmpty(column.getDefaultValue(), true)) {
			GridFieldVO vo = GridFieldVO.createParameter(Env.getCtx(), -1, 0, 0, column.getAD_Column_ID(), column.getColumnName(), column.getName(), 
						DisplayType.isLookup(column.getAD_Reference_ID()) 
						? (DisplayType.isText(column.getAD_Reference_ID()) || DisplayType.isList(column.getAD_Reference_ID()) ? DisplayType.String : DisplayType.ID) 
						: column.getAD_Reference_ID(), 0, false, false, "");
			vo.DefaultValue = column.getDefaultValue();
			GridField gridField = new GridField(vo);
			Object defaultValue = gridField.getDefault();
			if (defaultValue != null)
				po.set_ValueOfColumn(column.getAD_Column_ID(), defaultValue);
		}
	}

	private static Map<String, Object> normalizeProvidedValues(MTable table, PO po, List<Map<String, Object>> values) {
		Map<String, Object> providedValues = new LinkedHashMap<>();
		for (Map<String, Object> columnValue : values) {
			String column = (String) columnValue.get("column");
			Object value = columnValue.get("value");

			if (column == null || column.trim().isEmpty())
				throw new IllegalArgumentException("Column name is required");

			String canonicalColumn = resolveCanonicalColumnName(table, column);
			if (Util.isEmpty(canonicalColumn, true))
				canonicalColumn = column;

			int index = po.get_ColumnIndex(canonicalColumn);
			if (index < 0)
				throw new IllegalArgumentException("Column not found: " + column);

			providedValues.put(canonicalColumn, value);
		}
		return providedValues;
	}

	private static void setTypedColumnValue(PO po, String tableName, MColumn column, Object value) {
		if (po == null || column == null)
			return;
		Object typedValue = convertValue(tableName, column.getColumnName(), value);
		po.set_ValueOfColumn(column.getAD_Column_ID(), typedValue);
	}
}
