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
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.compiere.model.MMenu;
import org.compiere.model.MTree;
import org.compiere.model.MTreeNode;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Util;

import com.metalsistem.idempiere.graphql.schemaprovider.IGraphQLSchemaContributor;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry.Builder;
import graphql.schema.GraphQLFieldDefinition;

public class MenuTreeContributor implements IGraphQLSchemaContributor {

	@Override
	public String getContributorName() {
		return "menuTree";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("menu")
				.description("Get menu tree by id or UUID")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
					.description("Menu tree id or AD_Tree_UU").build())
				.build()
		};
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[0];
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "menu"),
				(DataFetcher<Object>) env -> {
					String id = env.getArgument("id");
					int menuTreeId = Util.isUUID(id) ? getMenuTreeID(id) : Integer.parseInt(id);
					if (menuTreeId <= 0) {
						throw new IllegalArgumentException("No valid menu tree id: " + id);
					}
					MTree tree = new MTree(Env.getCtx(), menuTreeId, false, true, null);
					MTreeNode root = tree.getRoot();
					Map<String, Object> result = new LinkedHashMap<>();
					result.put("name", root.getName());
					result.put("description", root.getDescription());
					result.put("entries", generateMenu(root));
					return result;
				});
	}

	private List<Map<String, Object>> generateMenu(MTreeNode node) {
		List<Map<String, Object>> entries = new ArrayList<>();
		Enumeration<?> nodeEnum = node.children();
		while (nodeEnum.hasMoreElements()) {
			MTreeNode child = (MTreeNode) nodeEnum.nextElement();
			if (child.getNode_ID() <= 0) {
				continue;
			}
			MMenu menu = MMenu.get(child.getNode_ID());
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("adMenuId", menu.getAD_Menu_ID());
			row.put("name", menu.getName());
			row.put("description", menu.getDescription());
			row.put("action", menu.getAction());
			row.put("adWindowId", menu.getAD_Window_ID());
			row.put("adWorkflowId", menu.getAD_Workflow_ID());
			row.put("adTaskId", menu.getAD_Task_ID());
			row.put("adProcessId", menu.getAD_Process_ID());
			row.put("adFormId", menu.getAD_Form_ID());
			row.put("adInfoWindowId", menu.getAD_InfoWindow_ID());

			if (child.getChildCount() > 0) {
				row.put("entries", generateMenu(child));
			}
			entries.add(row);
		}
		return entries;
	}

	private int getMenuTreeID(String uuid) {
		String sql = "SELECT AD_Tree_ID FROM AD_Tree WHERE AD_Tree_UU = ?";
		return DB.getSQLValue(null, sql, uuid);
	}
}
