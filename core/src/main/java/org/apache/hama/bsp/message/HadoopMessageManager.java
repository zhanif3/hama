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
package org.apache.hama.bsp.message;

import org.apache.hadoop.io.Writable;
import org.apache.hama.bsp.BSPMessageBundle;
import org.apache.hama.bsp.message.compress.BSPCompressedBundle;
import org.apache.hama.ipc.HamaRPCProtocolVersion;

/**
 * Hadoop RPC Interface for messaging.
 * 
 */
public interface HadoopMessageManager<M extends Writable> extends
    HamaRPCProtocolVersion {

  /**
   * This method puts a message for the next iteration. Accessed concurrently
   * from protocol, this must be synchronized internal.
   * 
   * @param msg
   */
  public void put(M msg);

  /**
   * This method puts a messagebundle for the next iteration. Accessed
   * concurrently from protocol, this must be synchronized internal.
   * 
   * @param messages
   */
  public void put(BSPMessageBundle<M> messages);

  /**
   * This method puts a compressed message bundle for the next iteration.
   * Accessed concurrently from protocol, this must be sychronized internally.
   * 
   * @param compMsgBundle
   */
  public void put(BSPCompressedBundle compMsgBundle);

}
