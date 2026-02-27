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

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.compiere.model.MRefList;
import org.compiere.model.MRefTable;
import org.compiere.model.MReference;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.util.Env;
import org.compiere.util.Util;
import org.compiere.util.ValueNamePair;

import com.metalsistem.idempiere.graphql.schemaprovider.IGraphQLSchemaContributor;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry.Builder;
import graphql.schema.GraphQLFieldDefinition;

public class ReferenceContributor implements IGraphQLSchemaContributor {

	@Override
	public String getContributorName() {
		return "reference";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("referenceList")
				.description("Get reference values by id/uuid/name")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("refID").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
					.description("Reference identifier: AD_Reference_ID, AD_Reference_UU or Name").build())
				.build()
		};
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[0];
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "referenceList"),
				(DataFetcher<Object>) env -> resolveReference(env.getArgument("refID")));
	}

	private Object resolveReference(String refID) {
		MReference ref = findReference(refID);
		if (ref == null || ref.get_ID() <= 0) {
			throw new IllegalArgumentException("No match found for AD_Reference_ID: " + refID);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("adReferenceId", ref.getAD_Reference_ID());
		result.put("adReferenceUU", ref.get_ValueAsString("AD_Reference_UU"));
		result.put("name", ref.getName());
		result.put("description", ref.getDescription());
		result.put("help", ref.getHelp());
		result.put("validationType", ref.getValidationType());

		if (MReference.VALIDATIONTYPE_ListValidation.equals(ref.getValidationType())) {
			result.put("reflist", resolveListValidation(ref));
			return result;
		}

		if (MReference.VALIDATIONTYPE_TableValidation.equals(ref.getValidationType())) {
			result.put("reftable", resolveTableValidation(ref));
			return result;
		}

		throw new UnsupportedOperationException("Reference validation type not implemented: " + ref.getValidationType());
	}

	private MReference findReference(String refID) {
		if (Pattern.matches("\\d+", refID)) {
			return new MReference(Env.getCtx(), Integer.parseInt(refID), null);
		}
		if (Util.isUUID(refID)) {
			return new Query(Env.getCtx(), MReference.Table_Name, "AD_Reference_UU=?", null).setParameters(refID).first();
		}
		return new Query(Env.getCtx(), MReference.Table_Name, "Name=?", null).setParameters(refID).first();
	}

	private List<Map<String, Object>> resolveListValidation(MReference ref) {
		List<Map<String, Object>> values = new ArrayList<>();
		for (ValueNamePair pair : MRefList.getList(Env.getCtx(), ref.getAD_Reference_ID(), false)) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("value", pair.getValue());
			row.put("name", pair.getName());
			values.add(row);
		}
		return values;
	}

	private List<Map<String, Object>> resolveTableValidation(MReference ref) {
		MRefTable refTable = MRefTable.get(ref.getAD_Reference_ID());
		if (refTable == null || refTable.get_ID() <= 0) {
			throw new IllegalArgumentException("No reference table for AD_Reference_ID: " + ref.getAD_Reference_ID());
		}

		MTable table = new MTable(Env.getCtx(), refTable.getAD_Table_ID(), null);
		Query query = new Query(Env.getCtx(), table, refTable.getWhereClause(), null)
				.setApplyAccessFilter(true, false)
				.setOnlyActiveRecords(true)
				.setOrderBy(refTable.getOrderByClause());

		List<PO> list = query.list();
		List<Map<String, Object>> values = new ArrayList<>();
		String keyColumn = table.getKeyColumns()[0];
		String displayColumn = table.get_ColumnName(refTable.getAD_Display());

		for (PO po : list) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", po.get_Value(keyColumn));
			row.put("name", po.get_Value(displayColumn));
			if (refTable.isValueDisplayed()) {
				row.put("value", po.get_Value("Value"));
			}
			values.add(row);
		}
		return values;
	}
}
