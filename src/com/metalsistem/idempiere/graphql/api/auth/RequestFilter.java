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
* - Trek Global Corporation                                           *
* - Heng Sin Low                                                      *
**********************************************************************/
package com.metalsistem.idempiere.graphql.api.auth;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Properties;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.adempiere.util.ServerContext;
import org.compiere.model.MClient;
import org.compiere.model.MRole;
import org.compiere.model.MSession;
import org.compiere.model.MUser;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Login;
import org.compiere.util.Util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metalsistem.idempiere.graphql.api.model.MGraphQLAuthToken;
import com.metalsistem.idempiere.graphql.api.model.MGraphQLRefreshToken;
import com.metalsistem.idempiere.graphql.api.util.GraphQLUtils;
import com.metalsistem.idempiere.graphql.api.util.ResponseUtils;

import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;

@Provider
@Priority(Priorities.AUTHORIZATION)
/**
 * Validate JWT token and set environment context(client,org,user,role and warehouse)
 * @author hengsin
 *
 */
public class RequestFilter implements ContainerRequestFilter {
	public static final String LOGIN_NAME = "#LoginName";
	public static final String LOGIN_CLIENTS = "#LoginClients";

	public RequestFilter() {
	}

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		Properties ctx = new Properties();
		ServerContext.setCurrentInstance(ctx);
		
		String authHeaderVal = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

		// consume JWT i.e. execute signature validation
		if (authHeaderVal != null && authHeaderVal.startsWith("Bearer")) {
			try {
				//validate Bearer token exists
				String[] authHeaderValues = authHeaderVal.split(" ");
				if (authHeaderValues.length < 2) {
					requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
					return;
				}
				validate(authHeaderValues[1], requestContext);
			} catch (JWTVerificationException ex) {
				ex.printStackTrace();				
				requestContext.abortWith(ResponseUtils.getResponseError(Status.UNAUTHORIZED, ex.getLocalizedMessage(), "", ""));
			} catch (Exception ex) {
				ex.printStackTrace();
				requestContext.abortWith(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
			}
		} else {
			if (!isRefreshTokenOperation(requestContext)) {
				requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
			}
		}
	}

	private boolean isRefreshTokenOperation(ContainerRequestContext requestContext) {
		if (!"POST".equalsIgnoreCase(requestContext.getMethod())) {
			return false;
		}

		String path = requestContext.getUriInfo().getPath();
		if (path == null || !path.endsWith("graphql")) {
			return false;
		}

		try {
			byte[] bytes = requestContext.getEntityStream().readAllBytes();
			requestContext.setEntityStream(new ByteArrayInputStream(bytes));

			if (bytes.length == 0) {
				return false;
			}

			ObjectMapper mapper = new ObjectMapper();
			JsonNode root = mapper.readTree(bytes);
			if (root == null || !root.hasNonNull("query")) {
				return false;
			}

			String query = root.get("query").asText();
			String operationName = root.hasNonNull("operationName") ? root.get("operationName").asText() : null;

			Document document = Parser.parse(query);
			OperationDefinition operation = selectOperation(document, operationName);
			if (operation == null || operation.getOperation() != OperationDefinition.Operation.MUTATION
					|| operation.getSelectionSet() == null) {
				return false;
			}

			List<?> selections = operation.getSelectionSet().getSelections();
			if (selections == null || selections.size() != 1) {
				return false;
			}

			Object selection = selections.get(0);
			if (!(selection instanceof Field)) {
				return false;
			}

			Field field = (Field) selection;
			return "refreshToken".equals(field.getName());
		} catch (Exception ex) {
			return false;
		}
	}

	private OperationDefinition selectOperation(Document document, String operationName) {
		OperationDefinition fallback = null;
		for (graphql.language.Definition<?> definition : document.getDefinitions()) {
			if (!(definition instanceof OperationDefinition)) {
				continue;
			}
			OperationDefinition operation = (OperationDefinition) definition;
			if (operationName == null || operationName.trim().isEmpty()) {
				if (fallback == null) {
					fallback = operation;
				}
			} else if (operationName.equals(operation.getName())) {
				return operation;
			}
		}
		return fallback;
	}

	private void validate(String token, ContainerRequestContext requestContext) throws IllegalArgumentException, UnsupportedEncodingException {
		
		if(MGraphQLAuthToken.isBlocked(token)) {
			throw new JWTVerificationException("Token is blocked");
		}
		if(MGraphQLRefreshToken.isRevoked(token)) {
			throw new JWTVerificationException("Token is revoked");
		}
		
		Algorithm algorithm = Algorithm.HMAC512(TokenUtils.getTokenSecret());
		JWTVerifier verifier = JWT.require(algorithm)
		        .withIssuer(TokenUtils.getTokenIssuer())
		        .build(); //Reusable verifier instance
		DecodedJWT jwt = verifier.verify(token);
		String userName = jwt.getSubject();
		ServerContext.setCurrentInstance(new Properties());
		Env.setContext(Env.getCtx(), LOGIN_NAME, userName);
		Claim claim = jwt.getClaim(LoginClaims.Clients.name());
		if (!claim.isNull() && !claim.isMissing()) {
			String clients = claim.asString();
			Env.setContext(Env.getCtx(), LOGIN_CLIENTS, clients);
		}
		claim = jwt.getClaim(LoginClaims.AD_Client_ID.name());
		int AD_Client_ID = 0;
		if (!claim.isNull() && !claim.isMissing()) {
			AD_Client_ID = claim.asInt();
			MClient client = MClient.get(AD_Client_ID);
			if (client == null)
				throw new JWTVerificationException("Invalid client claim");
			if (!client.isActive())
				throw new JWTVerificationException("Client is inactive");
			Env.setContext(Env.getCtx(), Env.AD_CLIENT_ID, AD_Client_ID);				
		}
		claim = jwt.getClaim(LoginClaims.AD_User_ID.name());
		if (!claim.isNull() && !claim.isMissing()) {
			MUser user = MUser.get(claim.asInt());
			if (user == null)
				throw new JWTVerificationException("Invalid user claim");
			if (!user.isActive())
				throw new JWTVerificationException("User is inactive");
			Env.setContext(Env.getCtx(), Env.AD_USER_ID, claim.asInt());
		}
		claim = jwt.getClaim(LoginClaims.AD_Role_ID.name());
		int AD_Role_ID = 0;
		if (!claim.isNull() && !claim.isMissing()) {
			AD_Role_ID = claim.asInt();
			MRole role = MRole.get(Env.getCtx(), AD_Role_ID);
			if (role == null)
				throw new JWTVerificationException("Invalid role claim");
			if (!role.isActive())
				throw new JWTVerificationException("Role is inactive");
			Env.setContext(Env.getCtx(), Env.AD_ROLE_ID, AD_Role_ID);				
		}
		claim = jwt.getClaim(LoginClaims.AD_Org_ID.name());
		int AD_Org_ID = 0;
		if (!claim.isNull() && !claim.isMissing()) {
			AD_Org_ID = claim.asInt();
			Env.setContext(Env.getCtx(), Env.AD_ORG_ID, AD_Org_ID);				
		}
		claim = jwt.getClaim(LoginClaims.M_Warehouse_ID.name());
		if (!claim.isNull() && !claim.isMissing()) {
			Env.setContext(Env.getCtx(), Env.M_WAREHOUSE_ID, claim.asInt());				
		}
		claim = jwt.getClaim(LoginClaims.AD_Language.name());
		if (!claim.isNull() && !claim.isMissing()) {
			String AD_Language = claim.asString();
			Env.setContext(Env.getCtx(), Env.LANGUAGE, AD_Language);
		}
		claim = jwt.getClaim(LoginClaims.AD_Session_ID.name());
		int AD_Session_ID = 0;
		if (!claim.isNull() && !claim.isMissing()) {
			AD_Session_ID = claim.asInt();
			Env.setContext(Env.getCtx(), Env.AD_SESSION_ID, AD_Session_ID);
			MSession session = MSession.get(Env.getCtx());
			if (session == null)
				throw new JWTVerificationException("Invalid session claim");
			if (session.isProcessed()) {
				if (session.getWebSession().endsWith("-logout")) {
					// session was logged out
					requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
					return;
				}
				// is possible that the session was finished in a reboot instead of a logout
				// if there is a GRAPHQL_AuthToken or a GRAPHQL_RefreshToken, then the user has not logged out
				MGraphQLAuthToken authToken = MGraphQLAuthToken.get(Env.getCtx(), token);
				if (authToken != null  || MGraphQLRefreshToken.existsAuthToken(token)) {
					DB.executeUpdateEx("UPDATE AD_Session SET Processed='N', UpdatedBy=CreatedBy, Updated=getDate() WHERE AD_Session_ID=?", new Object[] {AD_Session_ID}, null);
					session.load(session.get_TrxName());
				} else {
					requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
				}
			}
			// validate login on the token to check if session is still valid
			String errorMessage = new Login(Env.getCtx()).validateLogin(new KeyNamePair(AD_Org_ID, ""));
			if (!Util.isEmpty(errorMessage))
				throw new JWTVerificationException(errorMessage);
		}
		GraphQLUtils.setSessionContextVariables(Env.getCtx());
	}

}
