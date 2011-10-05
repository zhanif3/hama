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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.LocalDirAllocator;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;

/**
 * Base class for tasks.
 */
public abstract class Task implements Writable {
  public static final Log LOG = LogFactory.getLog(Task.class);
  // //////////////////////////////////////////
  // Fields
  // //////////////////////////////////////////

  protected BSPJobID jobId;
  protected String jobFile;
  protected TaskAttemptID taskId;
  protected int partition;

  protected LocalDirAllocator lDirAlloc;

  public Task() {
    jobId = new BSPJobID();
    taskId = new TaskAttemptID();
  }

  public Task(BSPJobID jobId, String jobFile, TaskAttemptID taskId,
      int partition) {
    this.jobId = jobId;
    this.jobFile = jobFile;
    this.taskId = taskId;
    this.partition = partition;
  }

  // //////////////////////////////////////////
  // Accessors
  // //////////////////////////////////////////
  public void setJobFile(String jobFile) {
    this.jobFile = jobFile;
  }

  public String getJobFile() {
    return jobFile;
  }

  public TaskAttemptID getTaskAttemptId() {
    return this.taskId;
  }

  public TaskAttemptID getTaskID() {
    return taskId;
  }

  /**
   * Get the job name for this task.
   * 
   * @return the job name
   */
  public BSPJobID getJobID() {
    return jobId;
  }

  /**
   * Get the index of this task within the job.
   * 
   * @return the integer part of the task id
   */
  public int getPartition() {
    return partition;
  }

  @Override
  public String toString() {
    return taskId.toString();
  }

  // //////////////////////////////////////////
  // Writable
  // //////////////////////////////////////////
  @Override
  public void write(DataOutput out) throws IOException {
    jobId.write(out);
    Text.writeString(out, jobFile);
    taskId.write(out);
    out.writeInt(partition);
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    jobId.readFields(in);
    jobFile = Text.readString(in);
    taskId.readFields(in);
    partition = in.readInt();
  }

  /**
   * Run this task as a part of the named job. This method is executed in the
   * child process.
   * 
   * @param umbilical for progress reports
   */
  public abstract void run(BSPJob job, BSPPeerProtocol umbilical)
      throws IOException;

  public abstract BSPTaskRunner createRunner(GroomServer groom);

  public void done(BSPPeerProtocol umbilical) throws IOException {
    umbilical.done(getTaskID(), true);
  }
  
  public abstract BSPJob getConf();
  public abstract void setConf(BSPJob localJobConf);
  
}