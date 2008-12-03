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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.log4j.Logger;

/**
 * A sub matrix is a matrix formed by selecting certain rows and columns from a
 * bigger matrix. This is a in-memory operation only.
 */
public class SubMatrix implements java.io.Serializable {
  private static final long serialVersionUID = 1L;
  static final Logger LOG = Logger.getLogger(SubMatrix.class);
  private double[][] matrix;

  /**
   * Constructor
   * 
   * @param i the size of rows
   * @param j the size of columns
   */
  public SubMatrix(int i, int j) {
    this.matrix = new double[i][j];
  }

  /**
   * Constructor
   * 
   * @param c a two dimensional double array
   */
  public SubMatrix(double[][] c) {
    double[][] matrix = c;
    this.matrix = matrix;
  }

  public SubMatrix(byte[] matrix) throws IOException {
    ByteArrayInputStream bos = new ByteArrayInputStream(matrix);
    ObjectInputStream oos = new ObjectInputStream(bos);
    Object obj = null;
    try {
      obj = oos.readObject();
      this.matrix = ((SubMatrix)obj).getDoubleArray();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    oos.close();
    bos.close();
  }
  
  /**
   * Sets the value
   * 
   * @param row
   * @param column
   * @param value
   */
  public void set(int row, int column, double value) {
    matrix[row][column] = value;
  }

  /**
   * Gets the value
   * 
   * @param i
   * @param j
   * @return the value of submatrix(i, j)
   */
  public double get(int i, int j) {
    return matrix[i][j];
  }

  /**
   * c = a+b
   * 
   * @param b
   * @return c
   */
  public SubMatrix add(SubMatrix b) {
    double[][] C = new double[getRows()][getColumns()];
    for (int i = 0; i < this.getRows(); i++) {
      for (int j = 0; j < this.getColumns(); j++) {
        C[i][j] += this.get(i, j) + b.get(i, j);
      }
    }

    return new SubMatrix(C);
  }

  /**
   * c = a*b
   * 
   * @param b
   * @return c
   */
  public SubMatrix mult(SubMatrix b) {
    double[][] C = new double[getRows()][getColumns()];
    for (int i = 0; i < this.getRows(); i++) {
      for (int j = 0; j < b.getColumns(); j++) {
        for (int k = 0; k < this.getColumns(); k++) {
          C[i][k] += this.get(i, j) * b.get(j, k);
        }
      }
    }

    return new SubMatrix(C);
  }

  /**
   * Gets the number of rows
   * 
   * @return the number of rows
   */
  public int getRows() {
    return this.matrix.length;
  }

  /**
   * Gets the number of columns
   * 
   * @return the number of columns
   */
  public int getColumns() {
    return this.matrix[0].length;
  }

  /**
   * Close
   */
  public void close() {
    matrix = null;
  }

  /**
   * @return the 2d double array
   */
  public double[][] getDoubleArray() {
    double[][] result = matrix;
    return result;
  }

  /**
   * Gets the bytes of the sub matrix
   * 
   * @return the bytes of the sub matrix
   * @throws IOException
   */
  public byte[] getBytes() throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(bos);
    oos.writeObject(this);
    oos.flush();
    oos.close();
    bos.close();
    byte[] data = bos.toByteArray();
    return data;
  }
}
