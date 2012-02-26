/**
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
package org.apache.hama.graph;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public interface VertexInterface<MSGTYPE> {

  /**
   * @return the vertex ID.
   */
  public String getVertexID();

  public void compute(Iterator<MSGTYPE> messages) throws IOException;

  public List<Edge> getOutEdges();

  public void sendMessage(Edge e, MSGTYPE msg) throws IOException;

  public void sendMessageToNeighbors(MSGTYPE msg) throws IOException;

  public long getSuperstepCount();

  public void setValue(MSGTYPE value);

  public MSGTYPE getValue();

}
