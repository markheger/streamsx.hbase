/* Copyright (C) 2013-2018, International Business Machines Corporation  */
/* All Rights Reserved                                                   */

package com.ibm.streamsx.hbase;

import java.util.Set;
import java.io.IOException;

import org.apache.log4j.Logger;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.Type.MetaType;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.TupleAttribute;

public abstract class HBASEOperatorWithInput extends HBASEOperator {
	protected String rowAttr = null;
	protected String columnFamilyAttr = null;
	protected String columnQualifierAttr = null;
	public String tableNameAttr = null;

	protected int rowAttrIndex = -1;
	protected int colFamilyIndex = -1;
	protected int colQualifierIndex = -1;
	protected int tableNameIndex = -1;

	protected MetaType rowAttrType = null;

	protected MetaType colQualifierType = null, colFamilyType = null, tableNameType = null;

	static final String COL_FAM_PARAM_NAME = "columnFamilyAttrName";
	static final String COL_QUAL_PARAM_NAME = "columnQualifierAttrName";
	public TupleAttribute<Tuple, String> tableNameAttribute; 
	static final String TABLE_NAME_ATTRIBUTE = "tableNameAttribute";
	static final String ROW_PARAM_NAME = "rowAttrName";
	byte colFamBytes[] = null;
	byte colQualBytes[] = null;
	byte tableNameBytes[] = null;


	@Parameter(name = TABLE_NAME_ATTRIBUTE, optional = true, description = "Name of the attribute on the input tuple containing the tableName. Cannot be used with tableName.")
	public void setTableNameAttr(TupleAttribute<Tuple, String> tableNameAttribute) throws IOException {
		this.tableNameAttribute = tableNameAttribute;
	} 
	
/*	
	public void setTableNameAttr(String TableNameAttr) {
		tableNameAttr = TableNameAttr;
	}
*/
	
	
	@Parameter(name = COL_FAM_PARAM_NAME, optional = true, description = "Name of the attribute on the input tuple containing the columnFamily. Cannot be used with staticColumnFmily.")
	public void setColumnFamilyAttr(String colF) {
		columnFamilyAttr = colF;
	}
	
	
	@Parameter(name = COL_QUAL_PARAM_NAME, optional = true, description = "Name of the attribute on the input tuple containing the columnQualifier. Cannot be used with staticColumnQualifier.")
	public void setColumnQualifierAttr(String colQ) {
		columnQualifierAttr = colQ;
	}

	@Parameter(name = ROW_PARAM_NAME, optional = false, description = "Name of the attribute on the input tuple containing the row.  It is required.")
	public void setRowAttr(String row) {
		rowAttr = row;
	}

	@ContextCheck(compile = true)
	public static void checkCol(OperatorContextChecker checker) {
		// Cannot specify both columnQualifierAttrName and staticColumnQualifer
		checker.checkExcludedParameters(COL_QUAL_PARAM_NAME, STATIC_COLQ_NAME);
		checker.checkExcludedParameters(STATIC_COLQ_NAME, COL_QUAL_PARAM_NAME);
		// Cannot specify both columnFamilyAttrName and a staticColumnFamily
		checker.checkExcludedParameters(COL_FAM_PARAM_NAME, STATIC_COLF_NAME);
		checker.checkExcludedParameters(STATIC_COLF_NAME, COL_FAM_PARAM_NAME);
		// Cannot specify both tableNameAttribute and a tableName
		checker.checkExcludedParameters(TABLE_NAME_ATTRIBUTE, TABLE_PARAM_NAME);
		checker.checkExcludedParameters(TABLE_PARAM_NAME, TABLE_NAME_ATTRIBUTE);

	}

	@ContextCheck(compile = true)
	static void checkColumnQWithoutF(OperatorContextChecker checker) {
		OperatorContext context = checker.getOperatorContext();
		Set<String> params = context.getParameterNames();
		if (params.contains(STATIC_COLQ_NAME)
				|| params.contains(COL_QUAL_PARAM_NAME)) {
			if (!params.contains(STATIC_COLF_NAME)
					&& !params.contains(COL_FAM_PARAM_NAME)) {
				// A columnqualifer was specified without a column family.
				checker.setInvalidContext();
			}
		}
	}

	protected byte[] getRow(Tuple tuple) throws Exception {
		return getBytes(tuple, rowAttrIndex, rowAttrType);
	}

	protected byte[] getColumnFamily(Tuple tuple) throws Exception {
		if (colFamBytes == null) {
			return getBytes(tuple, colFamilyIndex, colFamilyType);
		} else {
			return colFamBytes;
		}
	}

	protected byte[] getColumnQualifier(Tuple tuple) throws Exception {

		if (colQualBytes == null)
			return getBytes(tuple, colQualifierIndex, colQualifierType);
		else {
			return colQualBytes;
		}
	}

	protected String getTableName(Tuple tuple) throws Exception {

		System.out.println("################### getTableName  " + tuple.toString());

		String TableName = tuple.getString(tableNameAttribute.getAttribute().getIndex()); 
		
		System.out.println("################### getTableName  " + TableName);
		return TableName;
		
	}

	
	
	/**
	 * For {rowAttrName,columnFamilyAttrName,columnQualifierAttrName}, if
	 * specified, ensures the attribute exists, and stores the index in class
	 * variable.
	 * 
	 * If there is a staticColumnFamily or staticColumnQualifier specified, it
	 * checks that list has length at most one.
	 */
	@Override
	public synchronized void initialize(OperatorContext context)
			throws Exception {
		// Must call super.initialize(context) to correctly setup an operator.
		super.initialize(context);
		Logger.getLogger(this.getClass()).trace(
				"Operator " + context.getName() + " initializing in PE: "
						+ context.getPE().getPEId() + " in Job: "
						+ context.getPE().getJobId());

		StreamingInput<Tuple> input = context.getStreamingInputs().get(0);
		StreamSchema inputSchema = input.getStreamSchema();
		if (rowAttr != null) {
			rowAttrIndex = checkAndGetIndex(inputSchema, rowAttr);
			rowAttrType = inputSchema.getAttribute(rowAttrIndex).getType()
					.getMetaType();
		}
		if (columnFamilyAttr != null) {
			colFamilyIndex = checkAndGetIndex(inputSchema, columnFamilyAttr);
			colFamilyType = inputSchema.getAttribute(colFamilyIndex).getType()
					.getMetaType();
		}
		if (columnQualifierAttr != null) {
			colQualifierIndex = checkAndGetIndex(inputSchema,
					columnQualifierAttr);
			colQualifierType = inputSchema.getAttribute(colQualifierIndex)
					.getType().getMetaType();
		}

		if (tableNameAttr != null) {
			tableNameIndex = checkAndGetIndex(inputSchema,
					tableNameAttr);
			System.out.println("################### checkAndGetIndex  " + tableNameIndex);
			System.out.println("################### checkAndGetIndex  " + tableNameAttr);

			
			tableNameType = inputSchema.getAttribute(tableNameIndex)
					.getType().getMetaType();
		}

		
		
		if (staticColumnQualifierList != null) {
			colQualBytes = staticColumnQualifierList.get(0).getBytes(charset);
			if (staticColumnQualifierList.size() > 1) {
				throw new Exception(
						"Only one staticColumnQualifier supported for this operator");
			}
		}
		if (staticColumnFamilyList != null) {
			colFamBytes = staticColumnFamilyList.get(0).getBytes(charset);
			if (staticColumnFamilyList.size() > 1) {
				throw new Exception(
						"Only one staticColumnFamily supported for this operator");
			}
		}
	}
}
