/**
 *  Copyright 2011 LiveRamp
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.liveramp.hank.hadoop;

import com.liveramp.hank.config.CoordinatorConfigurator;
import com.liveramp.hank.coordinator.*;
import com.liveramp.hank.storage.PartitionRemoteFileOps;
import com.liveramp.hank.storage.StorageEngine;
import com.liveramp.hank.storage.Writer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;
import org.apache.hadoop.mapred.RecordWriter;
import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

// Base class of output formats used to build domains.
public abstract class DomainBuilderAbstractOutputFormat
    implements OutputFormat<KeyAndPartitionWritable, ValueWritable> {

  public static final String CONF_PARAM_HANK_DOMAIN_NAME = "com.liveramp.hank.output.domain";
  public static final String CONF_PARAM_HANK_CONFIGURATOR = "com.liveramp.hank.configuration";
  public static final String CONF_PARAM_HANK_OUTPUT_PATH = "com.liveramp.hank.output.path";
  public static final String CONF_PARAM_HANK_TMP_OUTPUT_PATH = "com.liveramp.hank.output.tmp_path";
  public static final String CONF_PARAM_HANK_VERSION_NUMBER = "com.liveramp.hank.output.version_number";
  public static final String CONF_PARAM_HANK_NUM_PARTITIONS = "com.liveramp.hank.output.num_partitions";

  public static String createConfParamName(String domainName, String confParamName) {
    return domainName + "#" + confParamName;
  }

  @Override
  public void checkOutputSpecs(FileSystem fs, JobConf conf)
      throws IOException {
    String outputPath = getJobOutputPath(conf);

    if (fs == null) {
      fs = FileSystem.get(new Configuration());
    }

    if (fs.exists(new Path(outputPath))) {
      throw new RuntimeException("Output path already exists: " + outputPath);
    }
  }

  protected static String getTaskAttemptOutputPath(JobConf conf) {
    String outputPath = conf.get("mapred.work.output.dir");
    if (outputPath == null) {
      throw new RuntimeException("Path was not set in mapred.work.output.dir");
    }
    return outputPath;
  }

  protected String getJobOutputPath(JobConf conf) {
    String outputPath = conf.get("mapred.output.dir");
    if (outputPath == null) {
      throw new RuntimeException("Path was not set in mapred.output.dir");
    }
    return outputPath;
  }

  // Base class of record writers used to build domains.
  protected abstract static class DomainBuilderRecordWriter implements RecordWriter<KeyAndPartitionWritable, ValueWritable> {

    private Logger LOG = Logger.getLogger(DomainBuilderRecordWriter.class);

    private final CoordinatorConfigurator configurator;
    private final String domainName;
    private final Integer domainVersionNumber;
    private final String outputPath;

    private Domain domain;
    private DomainVersion domainVersion;
    private StorageEngine storageEngine;

    private Writer writer = null;
    private Integer writerPartition = null;
    protected final Set<Integer> writtenPartitions = new HashSet<Integer>();

    DomainBuilderRecordWriter(JobConf conf,
                              String outputPath) throws IOException {
      // Load configuration items
      this.configurator = DomainBuilderProperties.getConfigurator(conf);
      this.domainName = DomainBuilderProperties.getDomainName(conf);
      this.domainVersionNumber = DomainBuilderProperties.getVersionNumber(domainName, conf);
      this.outputPath = outputPath;

      RunWithCoordinator.run(configurator,
          new RunnableWithCoordinator() {
            @Override
            public void run(Coordinator coordinator) throws IOException {
              DomainBuilderRecordWriter.this.domain = DomainBuilderProperties.getDomain(coordinator, domainName);
              DomainBuilderRecordWriter.this.domainVersion = DomainBuilderProperties.getDomainVersion(coordinator, domainName, domainVersionNumber);
              DomainBuilderRecordWriter.this.storageEngine = domain.getStorageEngine();
            }
          });
    }

    protected abstract Writer getWriter(StorageEngine storageEngine,
                                        DomainVersion domainVersion,
                                        PartitionRemoteFileOps partitionRemoteFileOps,
                                        int partitionNumber) throws IOException;

    @Override
    public final void close(Reporter reporter) throws IOException {
      // Close current writer
      closeCurrentWriterIfNeeded();
    }

    @Override
    public final void write(KeyAndPartitionWritable key, ValueWritable value) throws IOException {
      int partition = key.getPartition();
      // If writing a new partition, get a new writer
      if (writerPartition == null || writerPartition != partition) {
        // Set up new writer
        setNewPartitionWriter(partition);
      }
      if (key.getKey() == null && value.getAsByteBuffer() == null) {
        // Probably a marker tuple, skip it
        LOG.info("Skipping empty tuple: key=" + key.toString() + ", value=" + value.toString());
      } else {
        // Write record
        writer.write(key.getKey(), value.getAsByteBuffer());
      }
    }

    private void setNewPartitionWriter(int partitionNumber) throws IOException {
      // First, close current writer
      closeCurrentWriterIfNeeded();
      LOG.info("Setting up new writer for partition " + partitionNumber);
      // Check for existing partitions
      if (writtenPartitions.contains(partitionNumber)) {
        throw new RuntimeException("Partition " + partitionNumber
            + " has already been written.");
      }
      // Set up new writer
      writer = getWriter(storageEngine,
          domainVersion,
          storageEngine.getPartitionRemoteFileOpsFactory().getPartitionRemoteFileOps(outputPath, partitionNumber),
          partitionNumber);
      writerPartition = partitionNumber;
      writtenPartitions.add(partitionNumber);
    }

    private void closeCurrentWriterIfNeeded() throws IOException {
      if (writer != null) {
        LOG.info("Closing current partition writer: " + writer.toString());
        writer.close();
        RunWithCoordinator.run(configurator, new RunnableWithCoordinator() {
          @Override
          public void run(Coordinator coordinator) throws IOException {
            DomainVersion domainVersion = DomainBuilderProperties.getDomainVersion(coordinator,
                domainName,
                domainVersionNumber);
            domainVersion.addPartitionProperties(writerPartition,
                writer.getNumBytesWritten(),
                writer.getNumRecordsWritten());
          }
        });
      }
    }
  }
}
