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
package com.metalsistem.idempiere.graphql.api.util;

import org.compiere.util.Trx;

public final class GraphQLTrxManager {

	private GraphQLTrxManager() {
	}

	public static String begin(String prefix) {
		String safePrefix = (prefix == null || prefix.trim().isEmpty()) ? "gql" : prefix.trim();
		String trxName = Trx.createTrxName(safePrefix);
		Trx trx = Trx.get(trxName, true);
		trx.start();
		return trx.getTrxName();
	}

	public static void commit(String trxName) {
		Trx trx = Trx.get(trxName, false);
		if (trx == null) {
			throw new IllegalArgumentException("Transaction not found: " + trxName);
		}
		trx.commit();
		trx.close();
	}

	public static void rollback(String trxName) {
		Trx trx = Trx.get(trxName, false);
		if (trx == null) {
			throw new IllegalArgumentException("Transaction not found: " + trxName);
		}
		trx.rollback();
		trx.close();
	}

	public static Trx get(String trxName) {
		return trxName == null ? null : Trx.get(trxName, false);
	}
}
