package com.metalsistem.idempiere.graphql.resource;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.compiere.util.CLogger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.metalsistem.idempiere.graphql.schemaprovider.GraphQLSchemaProvider;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;

@Path("")
public class GraphQLResource {
	
	private static final CLogger log = CLogger.getCLogger(GraphQLResource.class);
	
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public Response landing() {
		return Response.ok("iDempiere GraphQL API is running").build();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response executeQuery(String body) throws JsonProcessingException {
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode request = mapper.readValue(body, ObjectNode.class);
			String query = request.has("query") ? request.get("query").asText() : "";
			String operationName = request.has("operationName") && !request.get("operationName").isNull()
					? request.get("operationName").asText() : null;
			Map<String, Object> variables = request.has("variables") && request.get("variables").isObject()
					? mapper.convertValue(request.get("variables"), new TypeReference<Map<String,Object>>() {}) : Collections.emptyMap();
			
			if (query.isEmpty()) {
				ObjectNode error = mapper.createObjectNode();
				error.put("error", "Missing query parameter");
				return Response.status(400).entity(mapper.writeValueAsString(error)).build();
			}
			
			GraphQL graphQL = GraphQLSchemaProvider.getGraphQL();
			ExecutionInput executionInput = ExecutionInput.newExecutionInput()
					.query(query)
					.operationName(operationName)
					.variables(variables)
					.build();
			ExecutionResult result = graphQL.execute(executionInput);
			
			ObjectNode response = mapper.createObjectNode();
			
			Object data = result.getData();
			if (data != null) {
				response.set("data", mapper.valueToTree(data));
			}
			
			if (result.getErrors() != null && !result.getErrors().isEmpty()) {
				logErrors(result.getErrors());
				response.set("errors", toClientErrors(mapper, result.getErrors()));
			}
			
			return Response.ok(mapper.writeValueAsString(response)).build();
			
		} catch (Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode error = mapper.createObjectNode();
			error.put("error", "Internal server error");
			return Response.status(500).entity(mapper.writeValueAsString(error)).build();
		}
	}

	private static ArrayNode toClientErrors(ObjectMapper mapper, List<GraphQLError> errors) {
		ArrayNode array = mapper.createArrayNode();
		for (GraphQLError error : errors) {
			ObjectNode entry = mapper.createObjectNode();
			entry.put("message", error.getMessage());
			if (error.getLocations() != null && !error.getLocations().isEmpty()) {
				entry.set("locations", mapper.valueToTree(error.getLocations()));
			}
			if (error.getPath() != null) {
				entry.set("path", mapper.valueToTree(error.getPath()));
			}
			if (error.getErrorType() != null) {
				entry.put("errorType", error.getErrorType().toString());
			}
			array.add(entry);
		}
		return array;
	}

	private static void logErrors(List<GraphQLError> errors) {
		for (GraphQLError error : errors) {
			log.log(Level.SEVERE, error.getMessage(), error);
		}
	}
}