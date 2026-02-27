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

import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.compiere.model.AdempiereProcessor;
import org.compiere.model.AdempiereProcessorLog;
import org.compiere.model.MRole;
import org.compiere.model.MScheduler;
import org.compiere.model.MTable;
import org.compiere.model.MUser;
import org.compiere.model.PO;
import org.compiere.server.AdempiereServerMgr;
import org.compiere.server.IServerManager;
import org.compiere.server.ServerInstance;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Util;
import org.idempiere.server.cluster.ClusterServerMgr;

import com.metalsistem.idempiere.graphql.api.util.ClusterUtil;
import com.metalsistem.idempiere.graphql.schemaprovider.IGraphQLSchemaContributor;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry.Builder;
import graphql.schema.GraphQLFieldDefinition;

public class ServerContributor implements IGraphQLSchemaContributor {

	@Override
	public String getContributorName() {
		return "server";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition().name("servers").description("Get server instances").type(ExtendedScalars.Json).build(),
			newFieldDefinition().name("server").description("Get server details")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.build(),
			newFieldDefinition().name("serverLogs").description("Get server logs")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.build(),
			newFieldDefinition().name("scheduler").description("Get scheduler details")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLInt)).build())
				.build()
		};
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition().name("changeServerState").type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.build(),
			newFieldDefinition().name("runServer").type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.build(),
			newFieldDefinition().name("reloadServers").type(ExtendedScalars.Json).build(),
			newFieldDefinition().name("addScheduler").type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLInt)).build())
				.build(),
			newFieldDefinition().name("removeScheduler").type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLInt)).build())
				.build()
		};
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "servers"), (DataFetcher<Object>) env -> getServers());
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "server"), (DataFetcher<Object>) env -> getServer(env.getArgument("id")));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "serverLogs"), (DataFetcher<Object>) env -> getServerLogs(env.getArgument("id")));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "scheduler"), (DataFetcher<Object>) env -> getScheduler(((Number) env.getArgument("id")).intValue()));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "changeServerState"), (DataFetcher<Object>) env -> changeServerState(env.getArgument("id")));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "runServer"), (DataFetcher<Object>) env -> runServer(env.getArgument("id")));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "reloadServers"), (DataFetcher<Object>) env -> reloadServers());
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "addScheduler"), (DataFetcher<Object>) env -> addScheduler(((Number) env.getArgument("id")).intValue()));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "removeScheduler"), (DataFetcher<Object>) env -> removeScheduler(((Number) env.getArgument("id")).intValue()));
	}

	private Object getServers() {
		requireAdmin("get servers");
		IServerManager serverMgr = getServerManager();
		ServerInstance[] instances = serverMgr.getServerInstances();
		List<Map<String, Object>> servers = new ArrayList<>();
		for (ServerInstance instance : instances) {
			servers.add(toServerSummary(instance));
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("servers", servers);
		return result;
	}

	private Object getServer(String id) {
		requireAdminOrServerAccess(id, "get server");
		ServerInstance instance = getServerInstance(id);
		Map<String, Object> server = toServerSummary(instance);
		if (!Util.isEmpty(instance.getModel().getDescription()))
			server.put("description", instance.getModel().getDescription());
		if (instance.getModel().getDateLastRun() != null)
			server.put("lastRun", DisplayType.getDateFormat(DisplayType.DateTime).format(instance.getModel().getDateLastRun()));
		server.put("info", instance.getServerInfo());
		if (instance.getModel().getDateNextRun(false) != null)
			server.put("nextRun", DisplayType.getDateFormat(DisplayType.DateTime).format(instance.getModel().getDateNextRun(false)));
		server.put("statistics", instance.getStatistics());
		server.put("sleeping", instance.isSleeping());
		server.put("interrupted", instance.isInterrupted());
		return server;
	}

	private Object getServerLogs(String id) {
		requireAdminOrServerAccess(id, "get server logs");
		ServerInstance instance = getServerInstance(id);
		AdempiereProcessorLog[] logs = instance.getModel().getLogs();
		List<Map<String, Object>> rows = new ArrayList<>();
		for (AdempiereProcessorLog log : logs) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("created", DisplayType.getDateFormat(DisplayType.DateTime).format(log.getCreated()));
			if (!Util.isEmpty(log.getSummary())) row.put("summary", log.getSummary());
			if (!Util.isEmpty(log.getDescription())) row.put("description", log.getDescription());
			if (!Util.isEmpty(log.getReference())) row.put("reference", log.getReference());
			if (!Util.isEmpty(log.getTextMsg())) row.put("textMessage", log.getTextMsg());
			row.put("error", log.isError());
			rows.add(row);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("logs", rows);
		return result;
	}

	private Object changeServerState(String id) {
		requireAdminOrServerAccess(id, "change server state");
		IServerManager serverMgr = getServerManager();
		ServerInstance instance = getServerInstance(id);
		String error = instance.isStarted() ? serverMgr.stop(id) : serverMgr.start(id);
		if (!Util.isEmpty(error, true)) {
			throw new IllegalStateException("Server error: " + error);
		}
		return getServer(id);
	}

	private Object runServer(String id) {
		requireAdminOrServerAccess(id, "run now");
		IServerManager serverMgr = getServerManager();
		String error = serverMgr.runNow(id);
		if (!Util.isEmpty(error, true)) {
			throw new IllegalStateException("Server error: " + error);
		}
		return getServer(id);
	}

	private Object reloadServers() {
		requireAdmin("reload servers");
		String error = getServerManager().reload();
		if (!Util.isEmpty(error, true)) {
			throw new IllegalStateException("Server error: " + error);
		}
		return getServers();
	}

	private Object getScheduler(int id) {
		requireAdmin("get scheduler");
		MScheduler scheduler = getSchedulerPo(id);
		ServerInstance instance = getServerManager().getServerInstance(scheduler.getServerID());
		return toSchedulerJson(scheduler, instance);
	}

	private Object addScheduler(int id) {
		MScheduler scheduler = getSchedulerPo(id);
		requireAdminOrSchedulerAccess(scheduler, "add scheduler");

		IServerManager serverMgr = getServerManager();
		ServerInstance instance = serverMgr.getServerInstance(scheduler.getServerID());
		if (instance == null) {
			String error = serverMgr.addScheduler(scheduler);
			if (!Util.isEmpty(error, true)) {
				throw new IllegalStateException("Server error: " + error);
			}
			instance = serverMgr.getServerInstance(scheduler.getServerID());
		}
		return toSchedulerJson(scheduler, instance);
	}

	private Object removeScheduler(int id) {
		MScheduler scheduler = getSchedulerPo(id);
		requireAdminOrSchedulerAccess(scheduler, "remove scheduler");

		String error = getServerManager().removeScheduler(scheduler);
		if (!Util.isEmpty(error, true)) {
			throw new IllegalStateException("Server error: " + error);
		}
		ServerInstance instance = getServerManager().getServerInstance(scheduler.getServerID());
		return toSchedulerJson(scheduler, instance);
	}

	private Map<String, Object> toServerSummary(ServerInstance instance) {
		Map<String, Object> server = new LinkedHashMap<>();
		server.put("id", instance.getServerId());
		server.put("name", instance.getModel().getName());
		if (instance.getClusterMember() != null) {
			server.put("nodeId", instance.getClusterMember().getId());
			server.put("hostName", instance.getClusterMember().getAddress().getCanonicalHostName());
			server.put("port", instance.getClusterMember().getPort());
		}
		server.put("started", instance.isStarted());
		return server;
	}

	private Map<String, Object> toSchedulerJson(MScheduler scheduler, ServerInstance instance) {
		String state;
		if (instance == null) {
			state = Msg.getMsg(Env.getCtx(), "SchedulerNotSchedule");
		} else if (instance.isStarted()) {
			state = Msg.getMsg(Env.getCtx(), "SchedulerStarted");
		} else {
			state = Msg.getMsg(Env.getCtx(), "SchedulerStopped");
		}
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("adSchedulerId", scheduler.getAD_Scheduler_ID());
		row.put("name", scheduler.getName());
		row.put("description", scheduler.getDescription());
		row.put("serverId", scheduler.getServerID());
		row.put("schedulerState", state);
		if (instance != null && instance.getClusterMember() != null) {
			row.put("nodeId", instance.getClusterMember().getId());
			row.put("nodeHostName", instance.getClusterMember().getAddress().getCanonicalHostName());
			row.put("nodePort", instance.getClusterMember().getPort());
		}
		return row;
	}

	private MScheduler getSchedulerPo(int id) {
		if (id <= 0) {
			throw new IllegalArgumentException("Invalid scheduler id: " + id);
		}
		MScheduler scheduler = new MScheduler(Env.getCtx(), id, null);
		if (scheduler.getAD_Scheduler_ID() != id) {
			throw new IllegalArgumentException("No match found for scheduler id: " + id);
		}
		return scheduler;
	}

	private ServerInstance getServerInstance(String id) {
		ServerInstance instance = getServerManager().getServerInstance(id);
		if (instance == null) {
			throw new IllegalArgumentException("No match found for server id: " + id);
		}
		return instance;
	}

	private IServerManager getServerManager() {
		if (ClusterUtil.getClusterService() != null) {
			return ClusterServerMgr.getInstance();
		}
		return AdempiereServerMgr.get();
	}

	private void requireAdmin(String action) {
		MUser user = MUser.get(Env.getCtx());
		if (user == null || !user.isAdministrator()) {
			throw new SecurityException("Access denied for " + action + " request");
		}
	}

	private void requireAdminOrServerAccess(String serverId, String action) {
		MUser user = MUser.get(Env.getCtx());
		if (user != null && user.isAdministrator()) {
			return;
		}
		ServerInstance instance = getServerInstance(serverId);
		AdempiereProcessor model = instance.getModel();
		if (!(model instanceof PO)) {
			throw new SecurityException("Access denied for " + action + " request: " + serverId);
		}
		PO po = (PO) model;
		MTable table = MTable.get(Env.getCtx(), po.get_Table_ID());
		if (!hasAccess(table, true) || po.getAD_Client_ID() != Env.getAD_Client_ID(Env.getCtx())
				|| !MRole.getDefault().isOrgAccess(po.getAD_Org_ID(), true)) {
			throw new SecurityException("Access denied for " + action + " request: " + serverId);
		}
	}

	private void requireAdminOrSchedulerAccess(MScheduler scheduler, String action) {
		MUser user = MUser.get(Env.getCtx());
		if (user != null && user.isAdministrator()) {
			return;
		}
		MTable table = MTable.get(Env.getCtx(), MScheduler.Table_ID);
		if (!hasAccess(table, true)
				|| scheduler.getAD_Client_ID() != Env.getAD_Client_ID(Env.getCtx())
				|| !MRole.getDefault().isOrgAccess(scheduler.getAD_Org_ID(), true)) {
			throw new SecurityException("Access denied for " + action + " request: " + scheduler.getServerID());
		}
	}

	private boolean hasAccess(MTable table, boolean rw) {
		MRole role = MRole.getDefault();
		if (role == null) {
			return false;
		}
		StringBuilder builder = new StringBuilder("SELECT DISTINCT a.AD_Window_ID FROM AD_Window a JOIN AD_Tab b ON a.AD_Window_ID=b.AD_Window_ID ");
		builder.append("WHERE a.IsActive='Y' AND b.IsActive='Y' AND b.AD_Table_ID=?");
		try (PreparedStatement stmt = DB.prepareStatement(builder.toString(), null)) {
			stmt.setInt(1, table.getAD_Table_ID());
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					int windowId = rs.getInt(1);
					if (role.getWindowAccess(windowId) != null && (rw ? role.isCanExport(windowId) || role.isCanReport(windowId) : true)) {
						return true;
					}
				}
			}
		} catch (SQLException ex) {
			return false;
		}
		return false;
	}
}
