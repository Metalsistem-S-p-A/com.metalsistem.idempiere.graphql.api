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
**********************************************************************/
package com.metalsistem.idempiere.graphql.resource;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.compiere.model.MProcess;
import org.compiere.model.MProcessPara;
import org.compiere.model.MPInstance;
import org.compiere.model.MPInstancePara;
import org.compiere.model.MTable;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ServerProcessCtl;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;

import com.metalsistem.idempiere.graphql.api.util.ThreadLocalTrx;

/**
 * Helper class for executing iDempiere processes from GraphQL
 * 
 * @author metalsis
 */
public class GraphQLProcessRunner {

	/**
	 * Execute an iDempiere process
	 * 
	 * @param process MProcess to execute
	 * @param parameters List of parameter maps (name, value, valueTo)
	 * @param recordId Record ID (0 if not applicable)
	 * @param recordUU Record UUID as alternative to recordId (optional)
	 * @param tableId Table ID (0 if not provided, can be determined from tableName)
	 * @param tableName Table name as alternative to tableId (optional)
	 * @param reportType Report output type (pdf, html, csv, xls)
	 * @param trxName Transaction name (optional)
	 * @return Map with execution results
	 */
	public static Map<String, Object> executeProcess(MProcess process, List<Map<String, Object>> parameters,
			int recordId, String recordUU, int tableId, String tableName, String reportType, String trxName) {
		
		// Resolve tableId from tableName if needed (like REST API does with model-name)
		if (recordId > 0 && tableId == 0 && tableName != null && !tableName.isEmpty()) {
			MTable table = MTable.get(Env.getCtx(), tableName);
			if (table != null) {
				tableId = table.getAD_Table_ID();
			}
		}
		
		MPInstance pInstance = new MPInstance(process, recordId, 0, recordUU);
		if (trxName != null)
			pInstance.set_TrxName(trxName);
		if (!pInstance.save()) {
			throw new RuntimeException("Failed to create process instance");
		}

		// Set parameters (excluding special context parameters)
		if (parameters != null && parameters.size() > 0) {
			setProcessParameters(process, pInstance, parameters, trxName);
		}

		// Create ProcessInfo
		ProcessInfo pi = new ProcessInfo(process.getName(), process.getAD_Process_ID());
		pi.setAD_PInstance_ID(pInstance.getAD_PInstance_ID());
		pi.setRecord_ID(recordId);
		pi.setTable_ID(tableId);
		
		// Set report type if specified
		if (process.isReport() && reportType != null) {
			String type = reportType.toUpperCase();
			if ("HTML".equals(type) || "CSV".equals(type) || "XLS".equals(type) || "PDF".equals(type)) {
				pi.setReportType(type);
				pi.setExport(true);
				
				if ("HTML".equals(type))
					pi.setExportFileExtension("html");
				else if ("CSV".equals(type))
					pi.setExportFileExtension("csv");
				else if ("XLS".equals(type))
					pi.setExportFileExtension("xls");
				else
					pi.setExportFileExtension("pdf");
			}
		}

		// Check if process is already running
		if (pi.isProcessRunning(pInstance.getParameters())) {
			throw new RuntimeException(Msg.getMsg(Env.getCtx(), "ProcessAlreadyRunning"));
		}

		// Execute process
		Trx trx = null;
		String effectiveTrxName = trxName != null ? trxName : ThreadLocalTrx.getTrxName();
		if (effectiveTrxName != null)
			trx = Trx.get(effectiveTrxName, false);
		
		ServerProcessCtl.process(pi, trx);

		// Build result
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("processId", process.getAD_Process_ID());
		result.put("processName", process.getName());
		result.put("processValue", process.getValue());
		result.put("instanceId", pInstance.getAD_PInstance_ID());
		result.put("isError", pi.isError());
		result.put("summary", pi.getSummary());
		result.put("logInfo", pi.getLogInfo());
		
		// Add report/export file if generated
		if (process.isReport() && pi.getPDFReport() != null) {
			File reportFile = pi.getPDFReport();
			result.put("reportFileName", reportFile.getName());
			result.put("reportFileSize", reportFile.length());
			// Optionally encode file as base64
			// result.put("reportContent", encodeFileToBase64(reportFile));
		}
		
		if (pi.isExport() && pi.getExportFile() != null) {
			File exportFile = pi.getExportFile();
			result.put("exportFileName", exportFile.getName());
			result.put("exportFileSize", exportFile.length());
			// Optionally encode file as base64
			// result.put("exportContent", encodeFileToBase64(exportFile));
		}

		return result;
	}

	/**
	 * Set process parameters from input list
	 */
	private static void setProcessParameters(MProcess process, MPInstance pInstance, List<Map<String, Object>> parameters,
			String trxName) {
		MProcessPara[] processParams = process.getParameters();
		MPInstancePara[] instanceParams = pInstance.getParameters();
		Map<String, MPInstancePara> instanceByName = new java.util.HashMap<>();
		for (MPInstancePara instanceParam : instanceParams) {
			instanceByName.put(instanceParam.getParameterName(), instanceParam);
		}
		
		for (Map<String, Object> paramInput : parameters) {
			String paramName = (String) paramInput.get("name");
			Object value = paramInput.get("value");
			Object valueTo = paramInput.get("valueTo");
			
			// Find parameter definition
			MProcessPara paramDef = null;
			for (MProcessPara p : processParams) {
				if (p.getColumnName().equals(paramName)) {
					paramDef = p;
					break;
				}
			}
			
			if (paramDef == null) {
				throw new IllegalArgumentException("Invalid parameter: " + paramName);
			}
			
			MPInstancePara iPara = instanceByName.get(paramName);
			if (iPara == null) {
				throw new IllegalArgumentException("Missing instance parameter for: " + paramName);
			}
			
			// Convert and set value based on display type
			int displayType = paramDef.getAD_Reference_ID();
			
			if (value != null) {
				Object convertedValue = convertValue(value, displayType);
				setParameterValue(iPara, convertedValue, displayType);
			}
			
			if (valueTo != null && paramDef.isRange()) {
				Object convertedValueTo = convertValue(valueTo, displayType);
				setParameterValueTo(iPara, convertedValueTo, displayType);
			}

			if (trxName != null)
				iPara.set_TrxName(trxName);
			if (iPara.is_Changed() && !iPara.save()) {
				throw new RuntimeException("Failed to save parameter: " + paramName);
			}
		}
	}

	/**
	 * Convert value based on display type
	 */
	private static Object convertValue(Object value, int displayType) {
		if (value == null)
			return null;
		
		String valueStr = value.toString();
		
		try {
			switch (displayType) {
				case DisplayType.Integer:
				case DisplayType.ID:
				case DisplayType.TableDir:
				case DisplayType.Table:
				case DisplayType.Search:
					return Integer.parseInt(valueStr);
					
				case DisplayType.Number:
				case DisplayType.Amount:
				case DisplayType.Quantity:
				case DisplayType.CostPrice:
					return new BigDecimal(valueStr);
					
				case DisplayType.Date:
				case DisplayType.DateTime:
				case DisplayType.Time:
					return parseTimestamp(valueStr, displayType);
					
				case DisplayType.YesNo:
					return "true".equalsIgnoreCase(valueStr) || "Y".equalsIgnoreCase(valueStr);
					
				default:
					return valueStr;
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid value '" + valueStr + "' for display type " + displayType);
		}
	}

	/**
	 * Parse timestamp from string based on display type
	 */
	private static Timestamp parseTimestamp(String valueStr, int displayType) {
		if (valueStr == null || valueStr.trim().isEmpty())
			return null;
		
		try {
			// Try ISO 8601 formats first
			String pattern;
			if (displayType == DisplayType.Date) {
				pattern = "yyyy-MM-dd";
			} else if (displayType == DisplayType.Time) {
				pattern = "HH:mm:ss";
			} else { // DateTime
				pattern = "yyyy-MM-dd'T'HH:mm:ss";
			}
			
			SimpleDateFormat sdf = new SimpleDateFormat(pattern);
			java.util.Date parsed = sdf.parse(valueStr.replace("Z", "").trim());
			return new Timestamp(parsed.getTime());
		} catch (Exception e) {
			// Try SQL standard format as fallback
			try {
				return Timestamp.valueOf(valueStr);
			} catch (Exception e2) {
				throw new IllegalArgumentException("Invalid timestamp format: " + valueStr + ". Expected ISO 8601 (yyyy-MM-dd or yyyy-MM-dd'T'HH:mm:ss) or SQL format");
			}
		}
	}

	/**
	 * Set parameter value based on type
	 */
	private static void setParameterValue(MPInstancePara iPara, Object value, int displayType) {
		if (value == null) {
			iPara.setP_String(null);
			return;
		}

		if (value instanceof Integer) {
			iPara.setP_Number((Integer) value);
		} else if (value instanceof BigDecimal) {
			iPara.setP_Number((BigDecimal) value);
		} else if (value instanceof Timestamp) {
			iPara.setP_Date((Timestamp) value);
		} else if (value instanceof Boolean) {
			iPara.setP_String(((Boolean) value) ? "Y" : "N");
		} else {
			iPara.setP_String(value.toString());
		}
	}

	/**
	 * Set parameter 'to' value based on type
	 */
	private static void setParameterValueTo(MPInstancePara iPara, Object value, int displayType) {
		if (value == null) {
			iPara.setP_String_To(null);
			return;
		}

		if (value instanceof Integer) {
			iPara.setP_Number_To((Integer) value);
		} else if (value instanceof BigDecimal) {
			iPara.setP_Number_To((BigDecimal) value);
		} else if (value instanceof Timestamp) {
			iPara.setP_Date_To((Timestamp) value);
		} else if (value instanceof Boolean) {
			iPara.setP_String_To(((Boolean) value) ? "Y" : "N");
		} else {
			iPara.setP_String_To(value.toString());
		}
	}
}
