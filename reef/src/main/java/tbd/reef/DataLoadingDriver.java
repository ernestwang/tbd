/**
 * Copyright (C) 2013 Carnegie Mellon University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tbd.reef;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import com.microsoft.reef.annotations.audience.DriverSide;
import com.microsoft.reef.driver.context.ActiveContext;
import com.microsoft.reef.driver.context.ContextConfiguration;
import com.microsoft.reef.driver.task.CompletedTask;
import com.microsoft.reef.driver.task.TaskConfiguration;
import com.microsoft.reef.io.data.loading.api.DataLoadingService;
import com.microsoft.reef.poison.PoisonedConfiguration;
import com.microsoft.tang.Configuration;
import com.microsoft.tang.Tang;
import com.microsoft.tang.annotations.Name;
import com.microsoft.tang.annotations.NamedParameter;
import com.microsoft.tang.annotations.Unit;
import com.microsoft.tang.exceptions.BindException;
import com.microsoft.wake.EventHandler;
import com.microsoft.wake.time.event.StartTime;

@DriverSide
@Unit
public class DataLoadingDriver {
  private static final Logger LOG = Logger.getLogger(DataLoadingDriver.class.getName());

  private final AtomicInteger ctrlCtxIds = new AtomicInteger();
  private final AtomicInteger lineCnt = new AtomicInteger();
  private final AtomicInteger completedDataTasks = new AtomicInteger();

  private final DataLoadingService dataLoadingService;

  private boolean firstTask = true;

  private Map<String, ActiveContext> contexts = new HashMap<String, ActiveContext>();
  private Map<String, String> ctxt2ip = new HashMap<String, String>();
  private Map<String, Integer> ctxt2port = new HashMap<String, Integer>();

  @NamedParameter(doc = "IP address", short_name = "ip", default_value = "127.0.0.1")
  final class HostIP implements Name<String> {
  }

  @NamedParameter(doc = "port number", short_name = "port", default_value = "2555")
  final class HostPort implements Name<String> {
  }

  @NamedParameter(doc = "master akka", short_name = "master", default_value = "akka.tcp://masterSystem0@127.0.0.1:2555/user/master")
  final class MasterAkka implements Name<String> {
  }

  @Inject
  public DataLoadingDriver(final DataLoadingService dataLoadingService) {
    this.dataLoadingService = dataLoadingService;
    this.completedDataTasks.set(dataLoadingService.getNumberOfPartitions());
  }

  public class ContextActiveHandler implements EventHandler<ActiveContext> {

    @Override
    public void onNext(final ActiveContext activeContext) {

      final String contextId = activeContext.getId();
      LOG.log(Level.INFO, "Context active: {0}", contextId);

      if (dataLoadingService.isDataLoadedContext(activeContext) && !contexts.keySet().contains(contextId)) {
        contexts.put(contextId, activeContext);
        
        final String taskId = "LineCountTask-" + ctrlCtxIds.getAndIncrement();
        LOG.log(Level.INFO, "Submit LineCount task {0} to: {1}", new Object[] { taskId, contextId });

        try {
          if (firstTask) {
            firstTask = false;
          activeContext.submitTask(TaskConfiguration.CONF
              .set(TaskConfiguration.IDENTIFIER, taskId)
              .set(TaskConfiguration.TASK, DataLoadingTask.class)
              .build());
          } else {
            activeContext.submitTask(TaskConfiguration.CONF
                .set(TaskConfiguration.IDENTIFIER, taskId)
                .set(TaskConfiguration.TASK, DataLoadingTask2.class)
                .build());
          }
        } catch (final BindException ex) {
          LOG.log(Level.INFO, "Configuration error in " + contextId, ex);
          throw new RuntimeException("Configuration error in " + contextId, ex);
        }
      } else if (activeContext.getId().startsWith("LineCountCtxt")) {

        final String taskId = "LineCountTask-" + ctrlCtxIds.getAndIncrement();
        LOG.log(Level.INFO, "Submit LineCount task {0} to: {1}", new Object[] { taskId, contextId });

        try {
          activeContext.submitTask(TaskConfiguration.CONF
              .set(TaskConfiguration.IDENTIFIER, taskId)
              .set(TaskConfiguration.TASK, DataLoadingTask.class)
              .build());
          firstTask = false;
        } catch (final BindException ex) {
          LOG.log(Level.INFO, "Configuration error in " + contextId, ex);
          throw new RuntimeException("Configuration error in " + contextId, ex);
        }
      } else {
        LOG.log(Level.INFO, "Unrecognized context: {0}", contextId);
      }
    }
  }

  public class TaskCompletedHandler implements EventHandler<CompletedTask> {
    @Override
    public void onNext(final CompletedTask completedTask) {

      final String taskId = completedTask.getId();
      LOG.log(Level.INFO, "Completed Task: {0}", taskId);

      final byte[] retBytes = completedTask.get();
      final String retStr = retBytes == null ? "No RetVal": new String(retBytes);
      LOG.log(Level.INFO, "Line count from {0} : {1}", new String[] { taskId, retStr });

      lineCnt.addAndGet(Integer.parseInt(retStr));

      if (completedDataTasks.decrementAndGet() <= 0) {
        LOG.log(Level.INFO, "Total line count: {0}", lineCnt.get());
      }

      LOG.log(Level.INFO, "Releasing Context: {0}", taskId);
      completedTask.getActiveContext().close();
    }
  }
}