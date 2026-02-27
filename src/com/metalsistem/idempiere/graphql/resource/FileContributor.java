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

import static graphql.Scalars.GraphQLFloat;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.activation.MimetypesFileTypeMap;
import javax.ws.rs.core.MediaType;

import org.compiere.model.MUser;
import org.compiere.util.Env;
import org.compiere.util.MimeType;
import org.compiere.util.Util;
import org.idempiere.distributed.IClusterMember;
import org.idempiere.distributed.IClusterService;

import com.metalsistem.idempiere.graphql.api.util.ClusterUtil;
import com.metalsistem.idempiere.graphql.resource.file.FileAccess;
import com.metalsistem.idempiere.graphql.resource.file.FileInfo;
import com.metalsistem.idempiere.graphql.resource.file.GetFileInfoCallable;
import com.metalsistem.idempiere.graphql.resource.file.RemoteFileStreamingOutput;
import com.metalsistem.idempiere.graphql.schemaprovider.IGraphQLSchemaContributor;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry.Builder;
import graphql.schema.GraphQLFieldDefinition;

public class FileContributor implements IGraphQLSchemaContributor {

	private static final int BLOCK_SIZE = 1024 * 1024 * 5;

	@Override
	public String getContributorName() {
		return "file";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("file")
				.description("Get file content as base64. Optional length check and node id.")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("fileName").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
					.description("Absolute file path").build())
				.argument(newArgument().name("length").type(GraphQLFloat)
					.description("Optional expected file length for validation").build())
				.argument(newArgument().name("nodeId").type(GraphQLString)
					.description("Optional cluster node id. Local node only in this contributor.").build())
				.build()
		};
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[0];
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "file"),
				(DataFetcher<Object>) this::resolveFile);
	}

	private Object resolveFile(DataFetchingEnvironment env) throws Exception {
		MUser user = MUser.get(Env.getCtx());
		if (user == null || !user.isAdministrator()) {
			throw new SecurityException("Access denied for get file request");
		}

		String fileName = env.getArgument("fileName");
		Double expectedLengthArg = env.getArgument("length");
		String nodeId = env.getArgument("nodeId");
		Long expectedLength = expectedLengthArg != null ? Long.valueOf(expectedLengthArg.longValue()) : null;

		if (Util.isEmpty(nodeId, true)) {
			return getLocalFile(fileName, expectedLength, null);
		}

		IClusterService service = ClusterUtil.getClusterService();
		if (service == null) {
			return getLocalFile(fileName, expectedLength, null);
		}

		IClusterMember local = service.getLocalMember();
		if (local != null && local.getId().equals(nodeId)) {
			return getLocalFile(fileName, expectedLength, nodeId);
		}

		return getRemoteFile(fileName, expectedLength, nodeId, service);
	}

	private Object getLocalFile(String fileName, Long expectedLength, String nodeId) throws IOException {
		File file = new File(fileName);
		if (!file.exists() || !file.isFile()) {
			throw new IllegalArgumentException("File not found: " + fileName);
		}
		if (!file.canRead() || !FileAccess.isAccessible(file)) {
			throw new SecurityException("File not readable: " + fileName);
		}
		if (expectedLength != null && file.length() != expectedLength.longValue()) {
			throw new SecurityException("Access denied for file: " + fileName);
		}

		byte[] binaryData = Files.readAllBytes(file.toPath());
		return buildFileResult(file.getAbsolutePath(), file.length(), resolveLocalContentType(fileName, file.getName()),
				Base64.getEncoder().encodeToString(binaryData), nodeId);
	}

	private Object getRemoteFile(String fileName, Long expectedLength, String nodeId, IClusterService service) throws Exception {
		IClusterMember member = ClusterUtil.getClusterMember(nodeId);
		if (member == null) {
			throw new IllegalArgumentException("No match found for node id: " + nodeId);
		}

		GetFileInfoCallable infoCallable = new GetFileInfoCallable(null, fileName, BLOCK_SIZE);
		FileInfo fileInfo = service.execute(infoCallable, member).get();
		if (fileInfo == null) {
			throw new IllegalArgumentException("File does not exist or is not readable: " + fileName);
		}
		if (expectedLength != null && expectedLength.longValue() != fileInfo.getLength()) {
			throw new SecurityException("Access denied for file: " + fileName);
		}

		RemoteFileStreamingOutput remoteOutput = new RemoteFileStreamingOutput(fileInfo, member);
		try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
			remoteOutput.write(stream);
			String data = Base64.getEncoder().encodeToString(stream.toByteArray());
			return buildFileResult(fileInfo.getFileName(), fileInfo.getLength(), resolveRemoteContentType(fileName, fileInfo.getFileName()), data,
					nodeId);
		}
	}

	private Map<String, Object> buildFileResult(String fileName, long length, String mimeType, String data, String nodeId) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("fileName", fileName);
		result.put("length", length);
		result.put("mimeType", mimeType);
		result.put("data", data);
		if (!Util.isEmpty(nodeId, true)) {
			result.put("nodeId", nodeId);
		}
		return result;
	}

	private String resolveLocalContentType(String fileName, String fallbackName) {
		String lfn = fileName.toLowerCase();
		if (lfn.endsWith(".html") || lfn.endsWith(".htm")) {
			return MediaType.TEXT_HTML;
		}
		if (lfn.endsWith(".csv") || lfn.endsWith(".ssv") || lfn.endsWith(".log")) {
			return MediaType.TEXT_PLAIN;
		}
		String mimeType = MimeType.getMimeType(fallbackName);
		if (Util.isEmpty(mimeType, true)) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
		return mimeType;
	}

	private String resolveRemoteContentType(String fileName, String fallbackName) {
		String lfn = fileName.toLowerCase();
		if (lfn.endsWith(".html") || lfn.endsWith(".htm")) {
			return MediaType.TEXT_HTML;
		}
		if (lfn.endsWith(".csv") || lfn.endsWith(".ssv") || lfn.endsWith(".log")) {
			return MediaType.TEXT_PLAIN;
		}
		MimetypesFileTypeMap map = new MimetypesFileTypeMap();
		String contentType = map.getContentType(fallbackName);
		if (Util.isEmpty(contentType, true)) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
		return contentType;
	}
}
