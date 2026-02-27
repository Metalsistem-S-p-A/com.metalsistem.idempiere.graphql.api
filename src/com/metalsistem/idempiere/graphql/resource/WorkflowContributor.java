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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.compiere.model.MBPartner;
import org.compiere.model.MTable;
import org.compiere.model.MUser;
import org.compiere.model.Query;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.compiere.util.Util;
import org.compiere.wf.MWFActivity;
import org.compiere.wf.MWFProcess;

import com.metalsistem.idempiere.graphql.api.converters.DateTypeConverter;
import com.metalsistem.idempiere.graphql.api.util.ThreadLocalTrx;
import com.metalsistem.idempiere.graphql.schemaprovider.IGraphQLSchemaContributor;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry.Builder;
import graphql.schema.GraphQLFieldDefinition;

public class WorkflowContributor implements IGraphQLSchemaContributor {

	@Override
	public String getContributorName() {
		return "workflow";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("workflowNodes")
				.description("Get pending workflow nodes for user")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("userId").type(GraphQLString)
					.description("Optional AD_User_ID or AD_User_UU").build())
				.build()
		};
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition().name("approveWorkflowNode").type(ExtendedScalars.Json)
				.argument(newArgument().name("nodeId").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.argument(newArgument().name("message").type(GraphQLString).build())
				.build(),
			newFieldDefinition().name("rejectWorkflowNode").type(ExtendedScalars.Json)
				.argument(newArgument().name("nodeId").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.argument(newArgument().name("message").type(GraphQLString).build())
				.build(),
			newFieldDefinition().name("setWorkflowUserChoice").type(ExtendedScalars.Json)
				.argument(newArgument().name("nodeId").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.argument(newArgument().name("value").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.argument(newArgument().name("message").type(GraphQLString).build())
				.build(),
			newFieldDefinition().name("acknowledgeWorkflowNode").type(ExtendedScalars.Json)
				.argument(newArgument().name("nodeId").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.argument(newArgument().name("message").type(GraphQLString).build())
				.build(),
			newFieldDefinition().name("forwardWorkflowNode").type(ExtendedScalars.Json)
				.argument(newArgument().name("nodeId").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.argument(newArgument().name("userTo").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.argument(newArgument().name("message").type(GraphQLString).build())
				.build()
		};
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "workflowNodes"),
				(DataFetcher<Object>) env -> getNodes(env.getArgument("userId")));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "approveWorkflowNode"),
				(DataFetcher<Object>) env -> actionActivity(env.getArgument("nodeId"), env.getArgument("message"), "Y", true, false));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "rejectWorkflowNode"),
				(DataFetcher<Object>) env -> actionActivity(env.getArgument("nodeId"), env.getArgument("message"), "N", true, false));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "setWorkflowUserChoice"),
				(DataFetcher<Object>) env -> actionActivity(env.getArgument("nodeId"), env.getArgument("message"), env.getArgument("value"), false, false));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "acknowledgeWorkflowNode"),
				(DataFetcher<Object>) env -> actionActivity(env.getArgument("nodeId"), env.getArgument("message"), null, false, true));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "forwardWorkflowNode"),
				(DataFetcher<Object>) env -> forward(env.getArgument("nodeId"), env.getArgument("userTo"), env.getArgument("message")));
	}

	private Object getNodes(String userId) {
		int adUserId = getADUserId(userId);
		if (adUserId <= 0) {
			throw new IllegalArgumentException("No user found matching id " + userId);
		}
		List<MWFActivity> activities = getUserPendingActivities(adUserId);
		List<Map<String, Object>> nodes = new ArrayList<>();
		for (MWFActivity activity : activities) {
			nodes.add(getActivityJsonObject(activity));
		}
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("rowCount", nodes.size());
		json.put("nodes", nodes);
		return json;
	}

	private int getADUserId(String userId) {
		if (Util.isEmpty(userId, true)) {
			return Env.getAD_User_ID(Env.getCtx());
		}
		MUser user;
		if (Util.isUUID(userId)) {
			user = new Query(Env.getCtx(), MUser.Table_Name, "AD_User_UU=?", null).setParameters(userId).first();
		} else {
			try {
				user = new MUser(Env.getCtx(), Integer.parseInt(userId), null);
			} catch (NumberFormatException e) {
				user = null;
			}
		}
		return user == null ? -1 : user.getAD_User_ID();
	}

	private List<MWFActivity> getUserPendingActivities(int adUserId) {
		String whereClause = getWhereUserPendingActivities();
		return new Query(Env.getCtx(), MWFActivity.Table_Name, whereClause, null)
				.setParameters(adUserId, adUserId, adUserId, adUserId, adUserId, Env.getAD_Client_ID(Env.getCtx()))
				.setClient_ID()
				.setOnlyActiveRecords(true)
				.setOrderBy("AD_WF_Activity.Priority DESC, AD_WF_Activity.Created")
				.list();
	}

	private Map<String, Object> getActivityJsonObject(MWFActivity activity) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("id", activity.getAD_WF_Activity_ID());
		if (!Util.isEmpty(activity.getAD_WF_Activity_UU())) {
			json.put("uid", activity.getAD_WF_Activity_UU());
		}
		json.put("modelName", activity.get_TableName().toLowerCase());
		json.put("nodeName", activity.getNodeName());
		json.put("priority", activity.getPriority());
		if (!Util.isEmpty(activity.getSummary(), true)) json.put("summary", activity.getSummary());
		if (!Util.isEmpty(activity.getNodeDescription(), true)) json.put("nodeDescription", activity.getNodeDescription());
		if (!Util.isEmpty(activity.getNodeHelp(), true)) json.put("nodeHelp", activity.getNodeHelp());
		if (!Util.isEmpty(activity.getHistoryHTML(), true)) json.put("historyRecords", activity.getHistoryHTML());
		int tableId = activity.getAD_Table_ID();
		if (tableId > 0) {
			json.put("tableName", MTable.getTableName(Env.getCtx(), tableId));
			json.put("adTableId", tableId);
		}
		if (activity.getRecord_ID() > 0) json.put("recordId", activity.getRecord_ID());
		json.put("nodeApproval", activity.isUserApproval());
		json.put("nodeConfirmation", activity.isUserManual());

		Object created = new DateTypeConverter().toJsonValue(DisplayType.DateTime, new Date(activity.getCreated().getTime()));
		if (created != null) {
			json.put("created", String.valueOf(created));
		}
		return json;
	}

	private Object actionActivity(String nodeId, String message, String value, boolean isApproval, boolean isConfirmation) {
		Trx trx = null;
		MWFActivity activity = null;
		String threadLocalTrxName = ThreadLocalTrx.getTrxName();
		try {
			trx = threadLocalTrxName != null ? Trx.get(threadLocalTrxName, false) : Trx.get(Trx.createTrxName("RWFS"), true);
			if (threadLocalTrxName == null) {
				trx.setDisplayName(getClass().getName() + "_setUserChoice");
			}

			activity = getActivity(nodeId, trx.getTrxName());
			int currentUserId = Env.getAD_User_ID(Env.getCtx());
			if (!isValidActionUser(currentUserId, activity)) {
				throw new IllegalArgumentException("The current User cannot action this Activity");
			}

			if (isApproval && !activity.getNode().isUserApproval()) {
				throw new IllegalArgumentException("Not an approval node");
			}
			if (isConfirmation && !activity.getNode().isUserManual()) {
				throw new IllegalArgumentException("Not an acknowledgment node");
			}
			if (!isApproval && !isConfirmation && !activity.getNode().isUserChoice()) {
				throw new IllegalArgumentException("Not a user choice node");
			}

			if (isConfirmation) {
				activity.setUserConfirmation(currentUserId, message);
			} else {
				activity.setUserChoice(currentUserId, value, activity.getNode().getColumn().getAD_Reference_ID(), message);
			}
			MWFProcess wfpr = new MWFProcess(activity.getCtx(), activity.getAD_WF_Process_ID(), activity.get_TrxName());
			wfpr.checkCloseActivities(activity.get_TrxName());
			if (threadLocalTrxName == null) {
				trx.commit();
			}
		} catch (Exception ex) {
			if (trx != null) trx.rollback();
			throw new IllegalStateException(ex.getMessage(), ex);
		} finally {
			if (trx != null && threadLocalTrxName == null) trx.close();
		}

		Map<String, Object> json = new LinkedHashMap<>();
		json.put("msg", Msg.getMsg(Env.getCtx(), "Updated"));
		if (!Util.isEmpty(activity.getSummary(), true)) json.put("summary", activity.getSummary());
		if (!Util.isEmpty(activity.getHistoryHTML(), true)) json.put("historyRecords", activity.getHistoryHTML());
		return json;
	}

	private Object forward(String nodeId, String userTo, String message) {
		MUser user = getUser(userTo);
		if (user == null) {
			throw new IllegalArgumentException("userTo is mandatory and must be valid");
		}
		MBPartner bp = user.getC_BPartner_ID() > 0 ? MBPartner.get(Env.getCtx(), user.getC_BPartner_ID()) : null;
		if (bp == null || !(bp.isEmployee() || bp.isSalesRep())) {
			throw new IllegalArgumentException("Invalid user - not Internal");
		}

		Trx trx = null;
		MWFActivity activity = null;
		String threadLocalTrxName = ThreadLocalTrx.getTrxName();
		try {
			trx = threadLocalTrxName != null ? Trx.get(threadLocalTrxName, false) : Trx.get(Trx.createTrxName("RWFF"), true);
			if (threadLocalTrxName == null) {
				trx.setDisplayName(getClass().getName() + "_forward");
			}
			activity = getActivity(nodeId, trx.getTrxName());
			int currentUserId = Env.getAD_User_ID(Env.getCtx());
			if (!isValidActionUser(currentUserId, activity)) {
				throw new IllegalArgumentException("The current User cannot action this Activity");
			}
			if (!activity.forwardTo(user.getAD_User_ID(), message)) {
				trx.rollback();
				throw new IllegalStateException(Msg.getMsg(Env.getCtx(), "CannotForward"));
			}
			if (threadLocalTrxName == null) {
				trx.commit();
			}
		} catch (Exception ex) {
			if (trx != null) trx.rollback();
			throw new IllegalStateException(ex.getMessage(), ex);
		} finally {
			if (trx != null && threadLocalTrxName == null) trx.close();
		}

		Map<String, Object> json = new LinkedHashMap<>();
		json.put("msg", Msg.getMsg(Env.getCtx(), "Updated"));
		if (!Util.isEmpty(activity.getSummary(), true)) json.put("summary", activity.getSummary());
		if (!Util.isEmpty(activity.getHistoryHTML(), true)) json.put("historyRecords", activity.getHistoryHTML());
		return json;
	}

	private MWFActivity getActivity(String nodeId, String trxName) {
		MWFActivity activity;
		if (Util.isUUID(nodeId)) {
			activity = new Query(Env.getCtx(), MWFActivity.Table_Name, "AD_WF_Activity_UU=?", trxName)
					.setParameters(nodeId).first();
		} else {
			activity = new MWFActivity(Env.getCtx(), Integer.parseInt(nodeId), trxName);
		}
		if (activity == null || activity.get_ID() <= 0) {
			throw new IllegalArgumentException("Activity not found: " + nodeId);
		}
		activity.set_TrxName(trxName);
		return activity;
	}

	private MUser getUser(String userId) {
		if (Util.isEmpty(userId, true)) {
			return null;
		}
		if (Util.isUUID(userId)) {
			return new Query(Env.getCtx(), MUser.Table_Name, "AD_User_UU=?", null).setParameters(userId).first();
		}
		try {
			return new MUser(Env.getCtx(), Integer.parseInt(userId), null);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private boolean isValidActionUser(int adUserId, MWFActivity activity) {
		String whereClause = getWhereUserPendingActivities() + " AND AD_WF_Activity.AD_WF_Activity_ID=?";
		int cnt = new Query(Env.getCtx(), MWFActivity.Table_Name, whereClause, null)
				.setParameters(adUserId, adUserId, adUserId, adUserId, adUserId, Env.getAD_Client_ID(Env.getCtx()), activity.getAD_WF_Activity_ID())
				.setClient_ID()
				.setOnlyActiveRecords(true)
				.count();
		return cnt == 1;
	}

	private static String getWhereUserPendingActivities() {
		return "AD_WF_Activity.Processed='N' AND AD_WF_Activity.WFState='OS' AND ("
				+ " AD_WF_Activity.AD_User_ID=?"
				+ " OR EXISTS (SELECT * FROM AD_WF_Responsible r WHERE AD_WF_Activity.AD_WF_Responsible_ID=r.AD_WF_Responsible_ID"
				+ " AND r.ResponsibleType='H' AND COALESCE(r.AD_User_ID,0)=0 AND COALESCE(r.AD_Role_ID,0)=0 AND (AD_WF_Activity.AD_User_ID=? OR AD_WF_Activity.AD_User_ID IS NULL))"
				+ " OR EXISTS (SELECT * FROM AD_WF_Responsible r WHERE AD_WF_Activity.AD_WF_Responsible_ID=r.AD_WF_Responsible_ID"
				+ " AND r.ResponsibleType='H' AND r.AD_User_ID=?)"
				+ " OR EXISTS (SELECT * FROM AD_WF_Responsible r INNER JOIN AD_User_Roles ur ON (r.AD_Role_ID=ur.AD_Role_ID)"
				+ " WHERE AD_WF_Activity.AD_WF_Responsible_ID=r.AD_WF_Responsible_ID AND r.ResponsibleType='R' AND ur.AD_User_ID=? AND ur.isActive = 'Y')"
				+ " OR EXISTS (SELECT * FROM AD_WF_ActivityApprover r "
				+ " WHERE AD_WF_Activity.AD_WF_Activity_ID=r.AD_WF_Activity_ID AND r.AD_User_ID=? AND r.isActive = 'Y')"
				+ ") AND AD_WF_Activity.AD_Client_ID=?";
	}
}
