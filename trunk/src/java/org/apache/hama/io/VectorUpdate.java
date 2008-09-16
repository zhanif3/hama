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
package org.apache.hama.io;

import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.hadoop.hbase.io.BatchUpdate;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hama.util.Numeric;

public class VectorUpdate {
  private BatchUpdate batchUpdate;

  public VectorUpdate(int i) {
    this.batchUpdate = new BatchUpdate(Numeric.intToBytes(i));
  }

  public VectorUpdate(String metadata) {
    this.batchUpdate = new BatchUpdate(metadata);
  }

  public void put(int j, double value) {
    this.batchUpdate.put(Numeric.getColumnIndex(j), Numeric
        .doubleToBytes(value));
  }

  public void put(String column, String val) {
    this.batchUpdate.put(column, Bytes.toBytes(val));
  }

  public void put(String metadataRows, int rows) {
    this.batchUpdate.put(metadataRows, Numeric.intToBytes(rows));
  }

  public BatchUpdate getBatchUpdate() {
    return this.batchUpdate;
  }

  public void putAll(Map<Integer, Double> buffer) {
    for (Map.Entry<Integer, Double> f : buffer.entrySet()) {
      put(f.getKey(), f.getValue());
    }
  }

  public void putAll(Set<Entry<Integer, VectorEntry>> entrySet) {
    for (Map.Entry<Integer, VectorEntry> e : entrySet) {
      put(e.getKey(), e.getValue().getValue());
    }
  }
}
