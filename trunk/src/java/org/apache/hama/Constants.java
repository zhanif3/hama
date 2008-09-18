/**
 * Copyright 2007 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hama;

/**
 * Some constants used in the Hama
 */
public class Constants {
  
  /** 
   * Meta-columnFamily to store the matrix-info 
   */
  public final static String METADATA = "metadata";
  
  /**
   * Column index & attributes
   */
  public final static String CINDEX = "cIndex";
  
  /**
   * The attribute column family
   */
  public final static String ATTRIBUTE = "attribute:";
  
  /** 
   * The number of the matrix rows 
   */
  public final static String METADATA_ROWS = "attribute:rows";
  
  /** 
   * The number of the matrix columns 
   */
  public final static String METADATA_COLUMNS = "attribute:columns";
  
  /** 
   * The type of the matrix
   */
  public final static String METADATA_TYPE = "attribute:type";
  
  /** 
   * Default columnFamily name 
   */
  public final static String COLUMN = "column:";

  /** 
   * Temporary random matrices name prefix 
   */
  public final static String RANDOM = "rand";
  
  /**
   * Admin table name
   */
  public final static String ADMINTABLE = "admin.table";

  /**
   * Matrix path columnFamily
   */
  public static final String PATHCOLUMN = "path:";
}
