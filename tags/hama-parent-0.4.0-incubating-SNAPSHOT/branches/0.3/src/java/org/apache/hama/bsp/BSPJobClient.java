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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Random;

import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.UnixUserGroupInformation;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hama.HamaConfiguration;
import org.apache.hama.ipc.JobSubmissionProtocol;

/**
 * BSPJobClient is the primary interface for the user-job to interact with the
 * BSPMaster.
 * 
 * BSPJobClient provides facilities to submit jobs, track their progress, access
 * component-tasks' reports/logs, get the BSP cluster status information etc.
 */
public class BSPJobClient extends Configured implements Tool {
  private static final Log LOG = LogFactory.getLog(BSPJobClient.class);

  public static enum TaskStatusFilter {
    NONE, KILLED, FAILED, SUCCEEDED, ALL
  }

  private static final long MAX_JOBPROFILE_AGE = 1000 * 2;

  class NetworkedJob implements RunningJob {
    JobProfile profile;
    JobStatus status;
    long statustime;

    public NetworkedJob(JobStatus job) throws IOException {
      this.status = job;
      this.profile = jobSubmitClient.getJobProfile(job.getJobID());
      this.statustime = System.currentTimeMillis();
    }

    /**
     * Some methods rely on having a recent job profile object. Refresh it, if
     * necessary
     */
    synchronized void ensureFreshStatus() throws IOException {
      if (System.currentTimeMillis() - statustime > MAX_JOBPROFILE_AGE) {
        updateStatus();
      }
    }

    /**
     * Some methods need to update status immediately. So, refresh immediately
     * 
     * @throws IOException
     */
    synchronized void updateStatus() throws IOException {
      this.status = jobSubmitClient.getJobStatus(profile.getJobID());
      this.statustime = System.currentTimeMillis();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.hama.bsp.RunningJob#getID()
     */
    @Override
    public BSPJobID getID() {
      return profile.getJobID();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.hama.bsp.RunningJob#getJobName()
     */
    @Override
    public String getJobName() {
      return profile.getJobName();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.hama.bsp.RunningJob#getJobFile()
     */
    @Override
    public String getJobFile() {
      return profile.getJobFile();
    }

    @Override
    public long progress() throws IOException {
      ensureFreshStatus();
      return status.progress();
    }

    /**
     * Returns immediately whether the whole job is done yet or not.
     */
    public synchronized boolean isComplete() throws IOException {
      updateStatus();
      return (status.getRunState() == JobStatus.SUCCEEDED
          || status.getRunState() == JobStatus.FAILED || status.getRunState() == JobStatus.KILLED);
    }

    /**
     * True if job completed successfully.
     */
    public synchronized boolean isSuccessful() throws IOException {
      updateStatus();
      return status.getRunState() == JobStatus.SUCCEEDED;
    }

    public synchronized long getSuperstepCount() throws IOException {
      ensureFreshStatus();
      return status.getSuperstepCount();
    }

    /**
     * Blocks until the job is finished
     */
    public void waitForCompletion() throws IOException {
      while (!isComplete()) {
        try {
          Thread.sleep(5000);
        } catch (InterruptedException ie) {
        }
      }
    }

    /**
     * Tells the service to get the state of the current job.
     */
    public synchronized int getJobState() throws IOException {
      updateStatus();
      return status.getRunState();
    }

    /**
     * Tells the service to terminate the current job.
     */
    public synchronized void killJob() throws IOException {
      jobSubmitClient.killJob(getID());
    }

    @Override
    public void killTask(TaskAttemptID taskId, boolean shouldFail)
        throws IOException {
      jobSubmitClient.killTask(taskId, shouldFail);
    }
  }

  private JobSubmissionProtocol jobSubmitClient = null;
  private Path sysDir = null;
  private FileSystem fs = null;

  // job files are world-wide readable and owner writable
  final private static FsPermission JOB_FILE_PERMISSION = FsPermission
      .createImmutable((short) 0644); // rw-r--r--

  // job submission directory is world readable/writable/executable
  final static FsPermission JOB_DIR_PERMISSION = FsPermission
      .createImmutable((short) 0777); // rwx-rwx-rwx

  public BSPJobClient(Configuration conf) throws IOException {
    setConf(conf);
    init(conf);
  }

  public BSPJobClient() {
  }

  public void init(Configuration conf) throws IOException {
    String masterAdress = conf.get("bsp.master.address");
    if (masterAdress != null && !masterAdress.equals("local")) {
      this.jobSubmitClient = (JobSubmissionProtocol) RPC.getProxy(
          JobSubmissionProtocol.class, JobSubmissionProtocol.versionID,
          BSPMaster.getAddress(conf), conf,
          NetUtils.getSocketFactory(conf, JobSubmissionProtocol.class));
    } else {
      LOG.debug("Using local BSP runner.");
      this.jobSubmitClient = new LocalBSPRunner(conf);
    }
  }

  /**
   * Close the <code>JobClient</code>.
   */
  public synchronized void close() throws IOException {
    RPC.stopProxy(jobSubmitClient);
  }

  /**
   * Get a filesystem handle. We need this to prepare jobs for submission to the
   * BSP system.
   * 
   * @return the filesystem handle.
   */
  public synchronized FileSystem getFs() throws IOException {
    if (this.fs == null) {
      Path sysDir = getSystemDir();
      this.fs = sysDir.getFileSystem(getConf());
    }
    return fs;
  }

  /**
   * Gets the jobs that are submitted.
   * 
   * @return array of {@link JobStatus} for the submitted jobs.
   * @throws IOException
   */
  public JobStatus[] getAllJobs() throws IOException {
    return jobSubmitClient.getAllJobs();
  }

  /**
   * Gets the jobs that are not completed and not failed.
   * 
   * @return array of {@link JobStatus} for the running/to-be-run jobs.
   * @throws IOException
   */
  public JobStatus[] jobsToComplete() throws IOException {
    return jobSubmitClient.jobsToComplete();
  }

  private UnixUserGroupInformation getUGI(Configuration conf)
      throws IOException {
    UnixUserGroupInformation ugi = null;
    try {
      ugi = UnixUserGroupInformation.login(conf, true);
    } catch (LoginException e) {
      throw (IOException) (new IOException(
          "Failed to get the current user's information.").initCause(e));
    }
    return ugi;
  }

  /**
   * Submit a job to the BSP system. This returns a handle to the
   * {@link RunningJob} which can be used to track the running-job.
   * 
   * @param job the job configuration.
   * @return a handle to the {@link RunningJob} which can be used to track the
   *         running-job.
   * @throws FileNotFoundException
   * @throws IOException
   */
  public RunningJob submitJob(BSPJob job) throws FileNotFoundException,
      IOException {
    return submitJobInternal(job);
  }

  static Random r = new Random();

  public RunningJob submitJobInternal(BSPJob job) throws IOException {
    BSPJobID jobId = jobSubmitClient.getNewJobId();

    Path submitJobDir = new Path(getSystemDir(), "submit_"
        + Integer.toString(Math.abs(r.nextInt()), 36));
    Path submitJarFile = new Path(submitJobDir, "job.jar");
    Path submitJobFile = new Path(submitJobDir, "job.xml");
    LOG.debug("BSPJobClient.submitJobDir: " + submitJobDir);

    /*
     * set this user's id in job configuration, so later job files can be
     * accessed using this user's id
     */
    UnixUserGroupInformation ugi = getUGI(job.getConf());

    ClusterStatus clusterStatus = getClusterStatus(true);

    // check the number of BSP tasks
    int tasks = job.getNumBspTask();
    int maxTasks = clusterStatus.getMaxTasks();

    if (tasks <= 0 || tasks > maxTasks) {
      LOG.warn("The number of tasks you've entered was invalid. Using default value of "
          + maxTasks + "!");
      job.setNumBspTask(maxTasks);
    }

    // Create a number of filenames in the BSPMaster's fs namespace
    FileSystem fs = getFs();
    fs.delete(submitJobDir, true);
    submitJobDir = fs.makeQualified(submitJobDir);
    submitJobDir = new Path(submitJobDir.toUri().getPath());
    FsPermission bspSysPerms = new FsPermission(JOB_DIR_PERMISSION);
    FileSystem.mkdirs(fs, submitJobDir, bspSysPerms);
    fs.mkdirs(submitJobDir);
    short replication = (short) job.getInt("bsp.submit.replication", 10);

    String originalJarPath = job.getJar();

    if (originalJarPath != null) { // copy jar to BSPMaster's fs
      // use jar name if job is not named.
      if ("".equals(job.getJobName())) {
        job.setJobName(new Path(originalJarPath).getName());
      }
      job.setJar(submitJarFile.toString());
      fs.copyFromLocalFile(new Path(originalJarPath), submitJarFile);

      fs.setReplication(submitJarFile, replication);
      fs.setPermission(submitJarFile, new FsPermission(JOB_FILE_PERMISSION));
    } else {
      LOG.warn("No job jar file set.  User classes may not be found. "
          + "See BSPJob#setJar(String) or check Your jar file.");
    }

    // Set the user's name and working directory
    job.setUser(ugi.getUserName());
    if (ugi.getGroupNames().length > 0) {
      job.set("group.name", ugi.getGroupNames()[0]);
    }
    if (job.getWorkingDirectory() == null) {
      job.setWorkingDirectory(fs.getWorkingDirectory());
    }

    // Write job file to BSPMaster's fs
    FSDataOutputStream out = FileSystem.create(fs, submitJobFile,
        new FsPermission(JOB_FILE_PERMISSION));

    try {
      job.writeXml(out);
    } finally {
      out.close();
    }

    //
    // Now, actually submit the job (using the submit name)
    //
    JobStatus status = jobSubmitClient.submitJob(jobId,
        submitJobFile.toString());
    if (status != null) {
      return new NetworkedJob(status);
    } else {
      throw new IOException("Could not launch job");
    }
  }

  /**
   * Monitor a job and print status in real-time as progress is made and tasks
   * fail.
   * 
   * @param job
   * @param info
   * @return true, if job is successful
   * @throws IOException
   * @throws InterruptedException
   */
  public boolean monitorAndPrintJob(BSPJob job, RunningJob info)
      throws IOException, InterruptedException {

    String lastReport = null;
    LOG.info("Running job: " + info.getID());

    while (!job.isComplete()) {
      Thread.sleep(3000);
      long step = job.progress();
      String report = "Current supersteps number: " + step;

      if (!report.equals(lastReport)) {
        LOG.info(report);
        lastReport = report;
      }
    }

    LOG.info("The total number of supersteps: " + info.getSuperstepCount());
    return job.isSuccessful();
  }

  /**
   * Grab the bspmaster system directory path where job-specific files are to be
   * placed.
   * 
   * @return the system directory where job-specific files are to be placed.
   */
  public Path getSystemDir() {
    if (sysDir == null) {
      sysDir = new Path(jobSubmitClient.getSystemDir());
    }
    return sysDir;
  }

  public static void runJob(BSPJob job) throws FileNotFoundException,
      IOException {
    BSPJobClient jc = new BSPJobClient(job.getConf());

    // TODO this code must be removed
    // when GroomServer supports the multiple tasks.
    if (job.getNumBspTask() == 0
        || job.getNumBspTask() > jc.getClusterStatus(false).getGroomServers()) {
      // If the number of tasks is greater than the number of GroomServer,
      // reset the number of tasks as number of GroomServer.
      job.setNumBspTask(jc.getClusterStatus(false).getGroomServers());
    }

    RunningJob running = jc.submitJobInternal(job);
    BSPJobID jobId = running.getID();
    LOG.info("Running job: " + jobId.toString());

    while (true) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
      }

      if (running.isComplete()) {
        break;
      }

      running = jc.getJob(jobId);
    }

    LOG.info("Job complete: " + jobId);
    LOG.info("The total number of supersteps: " + running.getSuperstepCount());

    // TODO if error found, kill job
    // running.killJob();
    jc.close();
  }

  /**
   * Get an RunningJob object to track an ongoing job. Returns null if the id
   * does not correspond to any known job.
   * 
   * @throws IOException
   */
  private RunningJob getJob(BSPJobID jobId) throws IOException {
    JobStatus status = jobSubmitClient.getJobStatus(jobId);
    if (status != null) {
      return new NetworkedJob(status);
    } else {
      return null;
    }
  }

  /**
   * Get status information about the BSP cluster
   * 
   * @param detailed if true then get a detailed status including the
   *          groomserver names
   * 
   * @return the status information about the BSP cluster as an object of
   *         {@link ClusterStatus}.
   * 
   * @throws IOException
   */
  public ClusterStatus getClusterStatus(boolean detailed) throws IOException {
    return jobSubmitClient.getClusterStatus(detailed);
  }

  @Override
  public int run(String[] args) throws Exception {
    int exitCode = -1;
    if (args.length < 1) {
      displayUsage("");
      return exitCode;
    }

    // process arguments
    String cmd = args[0];
    boolean listJobs = false;
    boolean listAllJobs = false;
    boolean listActiveGrooms = false;
    boolean killJob = false;
    boolean submitJob = false;
    boolean getStatus = false;
    String submitJobFile = null;
    String jobid = null;

    HamaConfiguration conf = new HamaConfiguration(getConf());
    init(conf);

    if ("-list".equals(cmd)) {
      if (args.length != 1 && !(args.length == 2 && "all".equals(args[1]))) {
        displayUsage(cmd);
        return exitCode;
      }
      if (args.length == 2 && "all".equals(args[1])) {
        listAllJobs = true;
      } else {
        listJobs = true;
      }
    } else if ("-list-active-grooms".equals(cmd)) {
      if (args.length != 1) {
        displayUsage(cmd);
        return exitCode;
      }
      listActiveGrooms = true;
    } else if ("-submit".equals(cmd)) {
      if (args.length == 1) {
        displayUsage(cmd);
        return exitCode;
      }

      submitJob = true;
      submitJobFile = args[1];
    } else if ("-kill".equals(cmd)) {
      if (args.length == 1) {
        displayUsage(cmd);
        return exitCode;
      }
      killJob = true;
      jobid = args[1];

    } else if ("-status".equals(cmd)) {
      if (args.length != 2) {
        displayUsage(cmd);
        return exitCode;
      }
      jobid = args[1];
      getStatus = true;

      // TODO Later, below functions should be implemented
      // with the Fault Tolerant mechanism.
    } else if ("-list-attempt-ids".equals(cmd)) {
      System.out.println("This function is not implemented yet.");
      return exitCode;
    } else if ("-kill-task".equals(cmd)) {
      System.out.println("This function is not implemented yet.");
      return exitCode;
    } else if ("-fail-task".equals(cmd)) {
      System.out.println("This function is not implemented yet.");
      return exitCode;
    }

    BSPJobClient jc = new BSPJobClient(new HamaConfiguration());
    if (listJobs) {
      listJobs();
      exitCode = 0;
    } else if (listAllJobs) {
      listAllJobs();
      exitCode = 0;
    } else if (listActiveGrooms) {
      listActiveGrooms();
      exitCode = 0;
    } else if (submitJob) {
      HamaConfiguration tConf = new HamaConfiguration(new Path(submitJobFile));
      RunningJob job = jc.submitJob(new BSPJob(tConf));
      System.out.println("Created job " + job.getID().toString());
    } else if (killJob) {
      RunningJob job = jc.getJob(BSPJobID.forName(jobid));
      if (job == null) {
        System.out.println("Could not find job " + jobid);
      } else {
        job.killJob();
        System.out.println("Killed job " + jobid);
      }
      exitCode = 0;
    } else if (getStatus) {
      RunningJob job = jc.getJob(BSPJobID.forName(jobid));
      if (job == null) {
        System.out.println("Could not find job " + jobid);
      } else {
        JobStatus jobStatus = jobSubmitClient.getJobStatus(job.getID());
        System.out.println("Job name: " + job.getJobName());
        System.out.printf("States are:\n\tRunning : 1\tSucceded : 2"
            + "\tFailed : 3\tPrep : 4\n");
        System.out.printf("%s\t%d\t%d\t%s\n", jobStatus.getJobID(),
            jobStatus.getRunState(), jobStatus.getStartTime(),
            jobStatus.getUsername());

        exitCode = 0;
      }
    }

    return 0;
  }

  /**
   * Display usage of the command-line tool and terminate execution
   */
  private void displayUsage(String cmd) {
    String prefix = "Usage: hama job ";
    String taskStates = "running, completed";
    if ("-submit".equals(cmd)) {
      System.err.println(prefix + "[" + cmd + " <job-file>]");
    } else if ("-status".equals(cmd) || "-kill".equals(cmd)) {
      System.err.println(prefix + "[" + cmd + " <job-id>]");
    } else if ("-list".equals(cmd)) {
      System.err.println(prefix + "[" + cmd + " [all]]");
    } else if ("-kill-task".equals(cmd) || "-fail-task".equals(cmd)) {
      System.err.println(prefix + "[" + cmd + " <task-id>]");
    } else if ("-list-active-grooms".equals(cmd)) {
      System.err.println(prefix + "[" + cmd + "]");
    } else if ("-list-attempt-ids".equals(cmd)) {
      System.err.println(prefix + "[" + cmd + " <job-id> <task-state>]. "
          + "Valid values for <task-state> are " + taskStates);
    } else {
      System.err.printf(prefix + "<command> <args>\n");
      System.err.printf("\t[-submit <job-file>]\n");
      System.err.printf("\t[-status <job-id>]\n");
      System.err.printf("\t[-kill <job-id>]\n");
      System.err.printf("\t[-list [all]]\n");
      System.err.printf("\t[-list-active-grooms]\n");
      System.err.println("\t[-list-attempt-ids <job-id> " + "<task-state>]\n");
      System.err.printf("\t[-kill-task <task-id>]\n");
      System.err.printf("\t[-fail-task <task-id>]\n\n");
    }
  }

  /**
   * Dump a list of currently running jobs
   * 
   * @throws IOException
   */
  private void listJobs() throws IOException {
    JobStatus[] jobs = jobsToComplete();
    if (jobs == null)
      jobs = new JobStatus[0];

    System.out.printf("%d jobs currently running\n", jobs.length);
    displayJobList(jobs);
  }

  /**
   * Dump a list of all jobs submitted.
   * 
   * @throws IOException
   */
  private void listAllJobs() throws IOException {
    JobStatus[] jobs = getAllJobs();
    if (jobs == null)
      jobs = new JobStatus[0];
    System.out.printf("%d jobs submitted\n", jobs.length);
    System.out.printf("States are:\n\tRunning : 1\tSucceded : 2"
        + "\tFailed : 3\tPrep : 4\n");
    displayJobList(jobs);
  }

  void displayJobList(JobStatus[] jobs) {
    System.out.printf("JobId\tState\tStartTime\tUserName\n");
    for (JobStatus job : jobs) {
      System.out.printf("%s\t%d\t%d\t%s\n", job.getJobID(), job.getRunState(),
          job.getStartTime(), job.getUsername());
    }
  }

  /**
   * Display the list of active groom servers
   */
  private void listActiveGrooms() throws IOException {
    ClusterStatus c = jobSubmitClient.getClusterStatus(true);
    Map<String, String> grooms = c.getActiveGroomNames();
    for (String groomName : grooms.keySet()) {
      System.out.println(groomName);
    }
  }

  /**
   */
  public static void main(String[] args) throws Exception {
    int res = ToolRunner.run(new BSPJobClient(), args);
    System.exit(res);
  }
}
