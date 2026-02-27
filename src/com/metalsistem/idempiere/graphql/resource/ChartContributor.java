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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.compiere.model.MChart;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Util;

import com.metalsistem.idempiere.graphql.api.util.GraphQLUtils;
import com.metalsistem.idempiere.graphql.schemaprovider.IGraphQLSchemaContributor;

import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry.Builder;
import graphql.schema.GraphQLFieldDefinition;

public class ChartContributor implements IGraphQLSchemaContributor {

	@Override
	public String getContributorName() {
		return "chart";
	}

	@Override
	public GraphQLFieldDefinition[] getQueryFields() {
		return new GraphQLFieldDefinition[] {
			newFieldDefinition()
				.name("chartImage")
				.description("Get chart image as base64 PNG by chart id or UUID")
				.type(ExtendedScalars.Json)
				.argument(newArgument().name("id").type(graphql.schema.GraphQLNonNull.nonNull(GraphQLString))
					.description("Chart id (AD_Chart_ID) or UUID (AD_Chart_UU)").build())
				.argument(newArgument().name("width").type(GraphQLInt)
					.description("Optional chart width in pixels").build())
				.argument(newArgument().name("height").type(GraphQLInt)
					.description("Optional chart height in pixels").build())
				.build()
		};
	}

	@Override
	public GraphQLFieldDefinition[] getMutationFields() {
		return new GraphQLFieldDefinition[0];
	}

	@Override
	public void registerDataFetchers(Builder registryBuilder) {
		registryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", "chartImage"),
				(DataFetcher<Object>) this::resolveChartImage);
	}

	private Object resolveChartImage(DataFetchingEnvironment env) throws Exception {
		GraphQLUtils.getTableAndCheckAccess(MChart.Table_Name, false);
		int chartId = resolveChartId(env.getArgument("id"));
		MChart chart = new MChart(Env.getCtx(), chartId, null);
		if (chart.get_ID() != chartId) {
			return null;
		}

		Integer widthArg = env.getArgument("width");
		Integer heightArg = env.getArgument("height");
		int width = widthArg != null ? widthArg.intValue() : 0;
		int height = heightArg != null ? heightArg.intValue() : 0;

		BufferedImage image = chart.getChartImage(width, height);
		if (image == null) {
			return null;
		}

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		String data = Base64.getEncoder().encodeToString(output.toByteArray());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", chart.getAD_Chart_ID());
		result.put("mimeType", "image/png");
		result.put("width", image.getWidth());
		result.put("height", image.getHeight());
		result.put("data", data);
		return result;
	}

	private int resolveChartId(String id) {
		if (Util.isUUID(id)) {
			return getChartIdByUUID(id);
		}
		try {
			return Integer.parseInt(id);
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Invalid chart id: " + id, ex);
		}
	}

	private int getChartIdByUUID(String uuid) {
		String sql = "SELECT AD_Chart_ID FROM AD_Chart WHERE AD_Chart_UU = ?";
		int chartId = DB.getSQLValue(null, sql, uuid);
		if (chartId <= 0) {
			throw new IllegalArgumentException("Chart not found for UUID: " + uuid);
		}
		return chartId;
	}
}
