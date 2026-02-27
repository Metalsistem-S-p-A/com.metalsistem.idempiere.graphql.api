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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.compiere.model.MUser;
import org.compiere.server.LogFileInfo;
import org.compiere.server.SystemInfo;
import org.compiere.util.CLogMgt;
import org.compiere.util.Env;
import org.compiere.util.TimeUtil;
import org.compiere.util.Util;
import org.idempiere.distributed.IClusterMember;
import org.idempiere.distributed.IClusterService;
import org.idempiere.server.cluster.callable.DeleteLogsCallable;
import org.idempiere.server.cluster.callable.RotateLogCallable;
import org.idempiere.server.cluster.callable.SetTraceLevelCallable;

import com.metalsistem.idempiere.graphql.api.util.ClusterUtil;
import com.metalsistem.idempiere.graphql.schemaprovider.IGraphQLSchemaContributor;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry.Builder;
import graphql.schema.GraphQLFieldDefinition;

public class NodeContributor implements IGraphQLSchemaContributor {

	private static final String LOCAL_ID = "local";

	@Override
	public String getContributorName() {
		return "node";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("nodes")
				.description("Get cluster nodes")
				.type(ExtendedScalars.Json)
				.build(),
			newFieldDefinition()
				.name("nodeInfo")
				.description("Get node system information")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
					.description("Node id").build())
				.build(),
			newFieldDefinition()
				.name("nodeLogs")
				.description("Get node log files")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
					.description("Node id").build())
				.build()
		};
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("deleteNodeLogs")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.build(),
			newFieldDefinition()
				.name("rotateNodeLogs")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.build(),
			newFieldDefinition()
				.name("updateNodeLogLevel")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(GraphQLString)
					.description("Optional node id; empty means local").build())
				.argument(newArgument().name("logLevel").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString)).build())
				.build()
		};
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "nodes"),
				(DataFetcher<Object>) env -> resolveGetNodes());
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "nodeInfo"),
				(DataFetcher<Object>) env -> resolveGetNodeInfo(env.getArgument("id")));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "nodeLogs"),
				(DataFetcher<Object>) env -> resolveGetNodeLogs(env.getArgument("id")));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "deleteNodeLogs"),
				(DataFetcher<Object>) env -> resolveDeleteNodeLogs(env.getArgument("id")));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "rotateNodeLogs"),
				(DataFetcher<Object>) env -> resolveRotateNodeLogs(env.getArgument("id")));
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "updateNodeLogLevel"),
				(DataFetcher<Object>) env -> resolveUpdateNodeLogLevel(env.getArgument("id"), env.getArgument("logLevel")));
	}

	private Object resolveGetNodes() throws Exception {
		requireAdmin("get nodes");
		List<Map<String, Object>> nodes = new ArrayList<>();
		IClusterService service = ClusterUtil.getClusterService();
		if (service == null) {
			Map<String, Object> local = new LinkedHashMap<>();
			local.put("id", LOCAL_ID);
			local.put("hostName", InetAddress.getLocalHost().getCanonicalHostName());
			nodes.add(local);
		} else {
			Collection<IClusterMember> members = service.getMembers();
			for (IClusterMember member : members) {
				Map<String, Object> node = new LinkedHashMap<>();
				node.put("id", member.getId());
				node.put("hostName", member.getAddress().getCanonicalHostName());
				node.put("port", member.getPort());
				nodes.add(node);
			}
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("nodes", nodes);
		return result;
	}

	private Object resolveGetNodeInfo(String id) {
		requireAdmin("get node info");
		SystemInfo info = getSystemInfo(id);
		if (info == null) {
			throw new IllegalArgumentException("No match found for node id: " + id);
		}
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("id", id);
		try {
			InetAddress address = info.getAddress() != null ? info.getAddress() : InetAddress.getLocalHost();
			json.put("hostName", address.getCanonicalHostName());
		} catch (Exception e) {
		}
		json.put("home", info.getIDempiereHome());
		json.put("os", info.getOperatingSystem());
		json.put("jvm", info.getJavaVM());
		json.put("databaseDescription", info.getDatabaseDescription());
		json.put("databaseConnectionURL", info.getDatabaseConnectionURL());
		json.put("databaseStatus", info.getDatabaseStatus());
		json.put("availableProcessors", info.getAvailableProcessors());
		json.put("averageSystemLoad", info.getAverageSystemLoad());
		json.put("memoryUsage", info.getMemoryUsage());
		json.put("heapMemoryUsage", info.getHeapMemoryUsage());
		json.put("runtime", info.getRuntimeName());
		json.put("runtimeUptime", TimeUtil.formatElapsed(info.getRuntimeUpTime()));
		json.put("threadCount", info.getThreadCount());
		json.put("peakThreadCount", info.getPeakThreadCount());
		json.put("daemonThreadCount", info.getDaemonThreadCount());
		json.put("totalStartedThreadCount", info.getTotalStartedThreadCount());
		json.put("logLevel", info.getLogLevel().getName());
		json.put("currentLogFile", info.getCurrentLogFile());
		json.put("sessionCount", info.getSessionCount());
		json.put("garbageCollectionCount", info.getGarbageCollectionCount());
		json.put("garbageCollectionTime", info.getGarbageCollectionTime());
		return json;
	}

	private Object resolveGetNodeLogs(String id) {
		requireAdmin("get node logs");
		SystemInfo info = getSystemInfo(id);
		if (info == null) {
			throw new IllegalArgumentException("No match found for node id: " + id);
		}
		LogFileInfo[] logInfos = info.getLogFileInfos();
		List<Map<String, Object>> logs = new ArrayList<>();
		for (LogFileInfo logInfo : logInfos) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("fileName", logInfo.getFileName());
			row.put("fileSize", logInfo.getFileSize());
			logs.add(row);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("logs", logs);
		return result;
	}

	private Object resolveDeleteNodeLogs(String id) throws Exception {
		requireAdmin("delete logs");
		Boolean success = executeNodeCallable(id, new DeleteLogsCallable());
		SystemInfo info = getSystemInfo(id);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", Boolean.TRUE.equals(success));
		if (info != null) {
			result.put("currentLogFile", info.getCurrentLogFile());
		}
		return result;
	}

	private Object resolveRotateNodeLogs(String id) throws Exception {
		requireAdmin("rotate logs");
		Boolean success = executeNodeCallable(id, new RotateLogCallable());
		SystemInfo info = getSystemInfo(id);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", Boolean.TRUE.equals(success));
		if (info != null) {
			result.put("currentLogFile", info.getCurrentLogFile());
		}
		return result;
	}

	private Object resolveUpdateNodeLogLevel(String id, String logLevel) throws Exception {
		requireAdmin("set log level");
		if (Util.isEmpty(logLevel, true)) {
			throw new IllegalArgumentException("No log level parameter");
		}
		String levelName = null;
		for (Level level : CLogMgt.LEVELS) {
			if (level.getName().equalsIgnoreCase(logLevel)) {
				levelName = level.getName();
				break;
			}
		}
		if (levelName == null) {
			throw new IllegalArgumentException("Invalid log level parameter: " + logLevel);
		}

		SetTraceLevelCallable callable = new SetTraceLevelCallable(levelName);
		if (!Util.isEmpty(id, true)) {
			IClusterService service = ClusterUtil.getClusterService();
			IClusterMember member = ClusterUtil.getClusterMember(id);
			if (service == null || member == null) {
				throw new IllegalArgumentException("No match found for node id: " + id);
			}
			service.execute(callable, member).get();
		} else {
			callable.call();
		}

		SystemInfo info = getSystemInfo(id);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("logLevel", info.getLogLevel().getName());
		return result;
	}

	private SystemInfo getSystemInfo(String id) {
		if (LOCAL_ID.equals(id)) {
			return SystemInfo.getLocalSystemInfo();
		}
		IClusterService service = ClusterUtil.getClusterService();
		if (service != null && service.getLocalMember() != null && service.getLocalMember().getId().equals(id)) {
			return SystemInfo.getLocalSystemInfo();
		}
		return SystemInfo.getClusterNodeInfo(id);
	}

	private Boolean executeNodeCallable(String id, Object callable) throws Exception {
		if (LOCAL_ID.equals(id)) {
			if (callable instanceof DeleteLogsCallable) {
				return ((DeleteLogsCallable) callable).call();
			}
			if (callable instanceof RotateLogCallable) {
				return ((RotateLogCallable) callable).call();
			}
		}
		IClusterService service = ClusterUtil.getClusterService();
		if (service != null && service.getLocalMember() != null && service.getLocalMember().getId().equals(id)) {
			if (callable instanceof DeleteLogsCallable) {
				return ((DeleteLogsCallable) callable).call();
			}
			if (callable instanceof RotateLogCallable) {
				return ((RotateLogCallable) callable).call();
			}
		}
		if (service == null) {
			throw new IllegalArgumentException("No match found for node id: " + id);
		}
		IClusterMember member = ClusterUtil.getClusterMember(id);
		if (member == null) {
			throw new IllegalArgumentException("No match found for node id: " + id);
		}
		if (callable instanceof DeleteLogsCallable) {
			return service.execute((DeleteLogsCallable) callable, member).get();
		}
		if (callable instanceof RotateLogCallable) {
			return service.execute((RotateLogCallable) callable, member).get();
		}
		return Boolean.FALSE;
	}

	private void requireAdmin(String action) {
		MUser user = MUser.get(Env.getCtx());
		if (user == null || !user.isAdministrator()) {
			throw new SecurityException("Access denied for " + action + " request");
		}
	}
}
