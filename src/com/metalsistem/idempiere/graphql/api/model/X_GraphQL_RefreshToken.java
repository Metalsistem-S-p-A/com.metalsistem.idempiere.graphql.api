/******************************************************************************
 * Product: iDempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2012 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
/** Generated Model - DO NOT CHANGE */
package com.metalsistem.idempiere.graphql.api.model;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Properties;
import org.compiere.model.*;

/** Generated Model for GraphQL_RefreshToken
 *  @author iDempiere (generated)
 *  @version Release 12 - $Id$ */
@org.adempiere.base.Model(table="GraphQL_RefreshToken")
public class X_GraphQL_RefreshToken extends PO implements I_GraphQL_RefreshToken, I_Persistent
{

	/**
	 *
	 */
	private static final long serialVersionUID = 20241022L;

    /** Standard Constructor */
    public X_GraphQL_RefreshToken (Properties ctx, String GraphQL_RefreshToken_UU, String trxName)
    {
      super (ctx, GraphQL_RefreshToken_UU, trxName);
      /** if (GraphQL_RefreshToken_UU == null)
        {
        } */
    }

    /** Standard Constructor */
    public X_GraphQL_RefreshToken (Properties ctx, String GraphQL_RefreshToken_UU, String trxName, String ... virtualColumns)
    {
      super (ctx, GraphQL_RefreshToken_UU, trxName, virtualColumns);
      /** if (GraphQL_RefreshToken_UU == null)
        {
        } */
    }

    /** Load Constructor */
    public X_GraphQL_RefreshToken (Properties ctx, ResultSet rs, String trxName)
    {
      super (ctx, rs, trxName);
    }

    /** AccessLevel
      * @return 6 - System - Client
      */
    protected int get_AccessLevel()
    {
      return accessLevel.intValue();
    }

    /** Load Meta Data */
    protected POInfo initPO (Properties ctx)
    {
      POInfo poi = POInfo.getPOInfo (ctx, Table_ID, get_TrxName());
      return poi;
    }

    public String toString()
    {
      StringBuilder sb = new StringBuilder ("X_GraphQL_RefreshToken[")
        .append(get_UUID()).append("]");
      return sb.toString();
    }

	/** Set Absolute Expires At.
		@param AbsoluteExpiresAt Absolute Expires At
	*/
	public void setAbsoluteExpiresAt (Timestamp AbsoluteExpiresAt)
	{
		set_Value (COLUMNNAME_AbsoluteExpiresAt, AbsoluteExpiresAt);
	}

	/** Get Absolute Expires At.
		@return Absolute Expires At	  */
	public Timestamp getAbsoluteExpiresAt()
	{
		return (Timestamp)get_Value(COLUMNNAME_AbsoluteExpiresAt);
	}

	/** Set Expires At.
		@param ExpiresAt Expires At
	*/
	public void setExpiresAt (Timestamp ExpiresAt)
	{
		set_Value (COLUMNNAME_ExpiresAt, ExpiresAt);
	}

	/** Get Expires At.
		@return Expires At	  */
	public Timestamp getExpiresAt()
	{
		return (Timestamp)get_Value(COLUMNNAME_ExpiresAt);
	}

	/** Set Parent Token.
		@param ParentToken Parent Token
	*/
	public void setParentToken (String ParentToken)
	{
		set_ValueNoCheck (COLUMNNAME_ParentToken, ParentToken);
	}

	/** Get Parent Token.
		@return Parent Token	  */
	public String getParentToken()
	{
		return (String)get_Value(COLUMNNAME_ParentToken);
	}

	/** Set GraphQL_RefreshToken_UU.
		@param GraphQL_RefreshToken_UU GraphQL_RefreshToken_UU
	*/
	public void setGraphQL_RefreshToken_UU (String GraphQL_RefreshToken_UU)
	{
		set_Value (COLUMNNAME_GraphQL_RefreshToken_UU, GraphQL_RefreshToken_UU);
	}

	/** Get GraphQL_RefreshToken_UU.
		@return GraphQL_RefreshToken_UU	  */
	public String getGraphQL_RefreshToken_UU()
	{
		return (String)get_Value(COLUMNNAME_GraphQL_RefreshToken_UU);
	}

	/** Breach = B */
	public static final String GraphQL_REVOKECAUSE_Breach = "B";
	/** Breach Chain = C */
	public static final String GraphQL_REVOKECAUSE_BreachChain = "C";
	/** Logout = L */
	public static final String GraphQL_REVOKECAUSE_Logout = "L";
	/** Manual Expire = M */
	public static final String GraphQL_REVOKECAUSE_ManualExpire = "M";
	/** Password Change = P */
	public static final String GraphQL_REVOKECAUSE_PasswordChange = "P";
	/** Set Revocation Cause.
		@param GraphQL_RevokeCause Revocation Cause
	*/
	public void setGraphQL_RevokeCause (String GraphQL_RevokeCause)
	{

		set_Value (COLUMNNAME_GraphQL_RevokeCause, GraphQL_RevokeCause);
	}

	/** Get Revocation Cause.
		@return Revocation Cause	  */
	public String getGraphQL_RevokeCause()
	{
		return (String)get_Value(COLUMNNAME_GraphQL_RevokeCause);
	}

	/** Set Refresh Token.
		@param RefreshToken Refresh Token
	*/
	public void setRefreshToken (String RefreshToken)
	{
		set_Value (COLUMNNAME_RefreshToken, RefreshToken);
	}

	/** Get Refresh Token.
		@return Refresh Token	  */
	public String getRefreshToken()
	{
		return (String)get_Value(COLUMNNAME_RefreshToken);
	}

	/** Set Revoked At.
		@param RevokedAt Revoked At
	*/
	public void setRevokedAt (Timestamp RevokedAt)
	{
		set_Value (COLUMNNAME_RevokedAt, RevokedAt);
	}

	/** Get Revoked At.
		@return Revoked At	  */
	public Timestamp getRevokedAt()
	{
		return (Timestamp)get_Value(COLUMNNAME_RevokedAt);
	}

	/** Set Token.
		@param Token Token
	*/
	public void setToken (String Token)
	{
		set_ValueNoCheck (COLUMNNAME_Token, Token);
	}

	/** Get Token.
		@return Token	  */
	public String getToken()
	{
		return (String)get_Value(COLUMNNAME_Token);
	}
}