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
package org.apache.hama.bsp;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.zookeeper.KeeperException;

public class BSPTaskRunner extends Thread {

  public static final Log LOG = LogFactory.getLog(BSPTaskRunner.class);
  private Task task;
  private BSPJob conf;
  private BSPPeer bspPeer;

  public BSPTaskRunner(BSPTask bspTask, BSPPeer bspPeer, BSPJob conf) {
    this.task = bspTask;
    this.conf = conf;
    this.bspPeer = bspPeer;
  }

  public Task getTask() {
    return task;
  }

  public void run() {
    BSP bsp = (BSP) ReflectionUtils.newInstance(conf.getConf().getClass(
        "bsp.work.class", BSP.class), conf.getConf());

    try {
      bsp.bsp(bspPeer);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (KeeperException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      try {
        finalize();
      } catch (Throwable e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

}
