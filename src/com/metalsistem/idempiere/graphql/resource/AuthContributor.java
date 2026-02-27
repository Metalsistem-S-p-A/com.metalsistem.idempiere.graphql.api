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

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.compiere.model.PO;
import org.compiere.util.Env;

import com.metalsistem.idempiere.graphql.api.auth.TokenUtils;
import com.metalsistem.idempiere.graphql.api.model.MGraphQLAuthToken;
import com.metalsistem.idempiere.graphql.api.model.MGraphQLRefreshToken;
import com.metalsistem.idempiere.graphql.schemaprovider.IGraphQLSchemaContributor;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry.Builder;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNonNull;

public class AuthContributor implements IGraphQLSchemaContributor {

	@Override
	public String getContributorName() {
		return "auth";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[0];
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("refreshToken")
				.description("Rotate refresh token and issue a new access token")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("refreshToken").type(GraphQLNonNull.nonNull(GraphQLString)).build())
				.build()
		};
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", "refreshToken"),
				(DataFetcher<Object>) env -> rotateRefreshToken(env.getArgument("refreshToken")));
	}

	private Object rotateRefreshToken(String refreshToken) {
		if (refreshToken == null || refreshToken.trim().isEmpty()) {
			throw new IllegalArgumentException("refreshToken is mandatory");
		}

		if (MGraphQLRefreshToken.isParent(refreshToken)) {
			MGraphQLRefreshToken.breachDetected(refreshToken);
			throw new SecurityException("Refresh token reuse detected");
		}

		MGraphQLRefreshToken currentRefresh = MGraphQLRefreshToken.getValidForRefresh(refreshToken);
		if (currentRefresh == null) {
			throw new SecurityException("Invalid or expired refresh token");
		}

		MGraphQLAuthToken currentAuth = MGraphQLAuthToken.get(Env.getCtx(), currentRefresh.getToken());
		if (currentAuth == null || !currentAuth.isActive() || currentAuth.isExpired()) {
			currentRefresh.revoke(new Timestamp(System.currentTimeMillis()), MGraphQLRefreshToken.GraphQL_REVOKECAUSE_ManualExpire);
			currentRefresh.saveEx();
			throw new SecurityException("Token session is no longer active");
		}

		MGraphQLAuthToken newAuth = new MGraphQLAuthToken(Env.getCtx(), 0, null);
		newAuth.setAD_Org_ID(currentAuth.getAD_Org_ID());
		newAuth.setAD_User_ID(currentAuth.getAD_User_ID());
		newAuth.setAD_Role_ID(currentAuth.getAD_Role_ID());
		newAuth.setM_Warehouse_ID(currentAuth.getM_Warehouse_ID());
		newAuth.setAD_Language(currentAuth.getAD_Language());
		newAuth.setExpireInMinutes(currentAuth.getExpireInMinutes());
		newAuth.setIsActive(true);
		newAuth.saveEx();

		MGraphQLRefreshToken newRefresh = new MGraphQLRefreshToken(Env.getCtx(), PO.UUID_NEW_RECORD, null);
		newRefresh.setToken(newAuth.getToken());
		newRefresh.setRefreshToken(UUID.randomUUID().toString());
		newRefresh.setParentToken(currentRefresh.getRefreshToken());
		newRefresh.setExpiresAt(TokenUtils.getRefreshTokenExpiresAt());
		newRefresh.setAbsoluteExpiresAt(currentRefresh.getAbsoluteExpiresAt());
		newRefresh.setIsActive(true);
		newRefresh.saveEx();

		Timestamp now = new Timestamp(System.currentTimeMillis());
		currentRefresh.revoke(now, MGraphQLRefreshToken.GraphQL_REVOKECAUSE_ManualExpire);
		currentRefresh.saveEx();

		currentAuth.setIsActive(false);
		currentAuth.saveEx();

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("accessToken", newAuth.getToken());
		result.put("refreshToken", newRefresh.getRefreshToken());
		result.put("tokenType", "Bearer");
		result.put("accessTokenExpiresAt", newAuth.getExpiresAt());
		result.put("refreshTokenExpiresAt", newRefresh.getExpiresAt());
		result.put("refreshTokenAbsoluteExpiresAt", newRefresh.getAbsoluteExpiresAt());
		return result;
	}
}