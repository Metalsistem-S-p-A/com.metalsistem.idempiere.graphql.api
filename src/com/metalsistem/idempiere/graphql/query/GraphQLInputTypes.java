/**********************************************************************
* This file is part of iDempiere ERP Open Source                      *
* http://www.idempiere.org                                            *
*                                                                     *
* Copyright (C) Contributors                                          *
**********************************************************************/
package com.metalsistem.idempiere.graphql.query;

import static graphql.Scalars.GraphQLString;

import graphql.Scalars;
import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLTypeReference;

public class GraphQLInputTypes {

	private GraphQLInputTypes() {
	}

	public static final GraphQLEnumType JoinType = GraphQLEnumType.newEnum()
			.name("JoinType")
			.description("SQL JOIN types")
			.value("INNER", "INNER", "INNER JOIN - returns records with matching values in both tables")
			.value("LEFT", "LEFT", "LEFT JOIN - returns all records from left table, matched from right")
			.value("RIGHT", "RIGHT", "RIGHT JOIN - returns all records from right table, matched from left")
			.value("FULL", "FULL", "FULL OUTER JOIN - returns all records when match in either table")
			.value("CROSS", "CROSS", "CROSS JOIN - returns Cartesian product of both tables")
			.build();

	public static final GraphQLEnumType ComparisonOperator = GraphQLEnumType.newEnum()
			.name("ComparisonOperator")
			.description("Comparison operators for WHERE conditions")
			.value("EQ", "eq", "Equal (=)")
			.value("NEQ", "neq", "Not equal (!=)")
			.value("GT", "gt", "Greater than (>)")
			.value("GE", "ge", "Greater than or equal (>=)")
			.value("LT", "lt", "Less than (<)")
			.value("LE", "le", "Less than or equal (<=)")
			.value("IN", "in", "IN list")
			.value("CONTAINS", "contains", "String contains (LIKE %value%)")
			.value("STARTSWITH", "startswith", "String starts with (LIKE value%)")
			.value("ENDSWITH", "endswith", "String ends with (LIKE %value)")
			.build();
    
	public static final GraphQLInputObjectType JoinSpec = GraphQLInputObjectType.newInputObject()
			.name("JoinSpec")
			.description("Specification for joining related tables")
			.field(field -> field.name("table").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
					.description("Table name to join"))
			.field(field -> field.name("type").type(JoinType)
					.description("JOIN type. Defaults to INNER if omitted."))
			.field(field -> field.name("alias").type(GraphQLString)
					.description("Optional alias for the joined table (defaults to lowercase table name)"))
			.field(field -> field.name("on").type(GraphQLString)
					.description("JOIN ON condition (e.g., 'M_Product.M_AttributeSetInstance_ID=asi.M_AttributeSetInstance_ID'). Required for all JOIN types except CROSS. If omitted for INNER/LEFT/RIGHT, auto-detects FK relationship."))
			.build();

	public static final GraphQLInputObjectType WhereClause = GraphQLInputObjectType.newInputObject()
			.name("WhereClause")
			.description("WHERE clause condition. Supports simple conditions (field+operator+value) or logical operators (and/or/not)")
			.field(field -> field.name("field").type(GraphQLString)
					.description("Column name (can be aliased like 'asi.LegacyCode'). Required for simple conditions."))
			.field(field -> field.name("operator").type(ComparisonOperator)
					.description("Comparison operator. Required for simple conditions."))
			.field(field -> field.name("value").type(ExtendedScalars.Json)
					.description("Value to compare. Can be string, number, boolean, null, or array for 'in' operator."))
			.field(field -> field.name("and").type(new GraphQLList(new GraphQLTypeReference("WhereClause")))
					.description("Logical AND: all conditions must be true"))
			.field(field -> field.name("or").type(new GraphQLList(new GraphQLTypeReference("WhereClause")))
					.description("Logical OR: at least one condition must be true"))
			.field(field -> field.name("not").type(new GraphQLTypeReference("WhereClause"))
					.description("Logical NOT: negates the condition"))
			.build();

	/**
	 * Input type for setting column values in INSERT/UPDATE operations
	 */
	public static final GraphQLInputObjectType ColumnValue = GraphQLInputObjectType.newInputObject()
			.name("ColumnValue")
			.description("Column name and value pair for INSERT/UPDATE operations")
			.field(field -> field.name("column").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
					.description("Column name"))
			.field(field -> field.name("value").type(ExtendedScalars.Json)
					.description("Value to set. Can be string, number, boolean, or null."))
			.build();

	/**
	 * Input type for CRUD operations on models (tables)
	 */
	public static final GraphQLInputObjectType ModelInput = GraphQLInputObjectType.newInputObject()
			.name("ModelInput")
			.description("Input for CRUD operations on any iDempiere table. Used with create/update/delete mutations.")
			.field(field -> field.name("table").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
					.description("Table name (e.g., 'M_Product', 'C_Order')"))
			.field(field -> field.name("values").type(new GraphQLList(ColumnValue))
					.description("Column values for INSERT/UPDATE. Required for create/update, not used in delete."))
			.field(field -> field.name("where").type(WhereClause)
					.description("WHERE clause to identify records. Required for update/delete, not used in create."))
			.build();

	/**
	 * Input type for process parameter values
	 */
	public static final GraphQLInputObjectType ProcessParameter = GraphQLInputObjectType.newInputObject()
			.name("ProcessParameter")
			.description("Process parameter name and value pair")
			.field(field -> field.name("name").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
					.description("Parameter name (e.g., 'DateFrom', 'C_BPartner_ID')"))
			.field(field -> field.name("value").type(ExtendedScalars.Json)
					.description("Parameter value. Can be string, number, boolean, or null."))
			.field(field -> field.name("valueTo").type(ExtendedScalars.Json)
					.description("Parameter 'to' value for range parameters (e.g., DateFrom/DateTo)"))
			.build();

	/**
	 * Input type for process execution
	 */
	public static final GraphQLInputObjectType ProcessInput = GraphQLInputObjectType.newInputObject()
			.name("ProcessInput")
			.description("Input for executing iDempiere processes. At least one of id, value, or name must be provided.")
			.field(field -> field.name("id").type(Scalars.GraphQLInt)
					.description("Process ID (AD_Process_ID)"))
			.field(field -> field.name("value").type(GraphQLString)
					.description("Process Value (unique identifier)"))
			.field(field -> field.name("name").type(GraphQLString)
					.description("Process Name"))
			.field(field -> field.name("parameters").type(new GraphQLList(ProcessParameter))
					.description("Process parameters (optional)"))
			.field(field -> field.name("recordId").type(Scalars.GraphQLInt)
					.description("Record ID if process is run on a specific record (optional)"))
			.field(field -> field.name("recordUU").type(GraphQLString)
					.description("Record UUID as alternative to recordId (optional)"))
			.field(field -> field.name("tableId").type(Scalars.GraphQLInt)
					.description("Table ID (AD_Table_ID) if process is run on a specific record (optional)"))
			.field(field -> field.name("tableName").type(GraphQLString)
					.description("Table name as alternative to tableId (optional)"))
			.field(field -> field.name("reportType").type(GraphQLString)
					.description("Report output type: 'pdf', 'html', 'csv', 'xls' (only for reports, defaults to 'pdf')"))
			.build();
}
