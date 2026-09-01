package io.floci.gcp.services.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import io.floci.gcp.config.EmulatorConfig;
import io.floci.gcp.core.common.GcpException;
import io.floci.gcp.core.common.EmulatorClock;
import io.floci.gcp.core.common.TestFaultInjector;
import io.floci.gcp.core.common.ServiceDescriptor;
import io.floci.gcp.core.common.ServiceProtocol;
import io.floci.gcp.core.common.ServiceRegistry;
import io.floci.gcp.core.storage.StorageBackend;
import io.floci.gcp.core.storage.StorageFactory;
import io.floci.gcp.lifecycle.GrpcServerManager;
import io.floci.gcp.services.tasks.model.StoredQueue;
import io.floci.gcp.services.tasks.model.StoredTask;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

@ApplicationScoped
public class CloudTasksService {

    private static final Logger LOG = Logger.getLogger(CloudTasksService.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final StorageBackend<String, StoredQueue> queueStore;
    private final StorageBackend<String, StoredTask> taskStore;
    private final TestFaultInjector faults;
    private final EmulatorClock clock;

    private final ServiceRegistry serviceRegistry;
    private final EmulatorConfig config;
    private final GrpcServerManager grpcServerManager;

    @Inject
    public CloudTasksService(ServiceRegistry serviceRegistry, EmulatorConfig config,
            StorageFactory storageFactory, GrpcServerManager grpcServerManager, TestFaultInjector faults,
            EmulatorClock clock) {
        this.serviceRegistry = serviceRegistry;
        this.config = config;
        this.grpcServerManager = grpcServerManager;
        this.faults = faults;
        this.clock = clock;
        this.queueStore = storageFactory.createGlobal("cloudtasks-queues", "cloudtasks-queues.json",
                new TypeReference<Map<String, StoredQueue>>() {});
        this.taskStore = storageFactory.createGlobal("cloudtasks-tasks", "cloudtasks-tasks.json",
                new TypeReference<Map<String, StoredTask>>() {});
    }

    CloudTasksService(StorageBackend<String, StoredQueue> queueStore,
            StorageBackend<String, StoredTask> taskStore) {
        this(queueStore, taskStore, new TestFaultInjector(false));
    }

    CloudTasksService(StorageBackend<String, StoredQueue> queueStore,
            StorageBackend<String, StoredTask> taskStore, TestFaultInjector faults) {
        this(queueStore, taskStore, faults, new EmulatorClock(false, null));
    }

    CloudTasksService(StorageBackend<String, StoredQueue> queueStore,
            StorageBackend<String, StoredTask> taskStore, TestFaultInjector faults, EmulatorClock clock) {
        this.queueStore = queueStore;
        this.taskStore = taskStore;
        this.faults = faults;
        this.clock = clock;
        this.serviceRegistry = null;
        this.config = null;
        this.grpcServerManager = null;
    }

    void onStart(@Observes StartupEvent ev) {
        serviceRegistry.register(ServiceDescriptor.builder("cloudtasks")
                .enabled(config.services().cloudtasks().enabled())
                .storageKey("cloudtasks")
                .protocol(ServiceProtocol.GRPC)
                .resourceClasses(CloudTasksController.class)
                .build());
        grpcServerManager.bind(new CloudTasksController(this));
    }

    // ── Queues ─────────────────────────────────────────────────────────────────

    public StoredQueue createQueue(String project, String location, String queueId,
            double maxDispatchesPerSecond, int maxConcurrentDispatches, int maxAttempts) {
        return createQueue(project, location, queueId, maxDispatchesPerSecond, maxConcurrentDispatches,
                maxAttempts, 1, 3600, 16);
    }

    public StoredQueue createQueue(String project, String location, String queueId,
            double maxDispatchesPerSecond, int maxConcurrentDispatches, int maxAttempts,
            long minBackoffSeconds, long maxBackoffSeconds, int maxDoublings) {
        String name = "projects/" + project + "/locations/" + location + "/queues/" + queueId;
        LOG.infof("createQueue name=%s", name);
        if (queueStore.get(name).isPresent()) {
            throw GcpException.alreadyExists("Queue already exists: " + name);
        }
        StoredQueue queue = new StoredQueue(name, clock.instant().toString());
        if (maxDispatchesPerSecond > 0) {
            queue.setMaxDispatchesPerSecond(maxDispatchesPerSecond);
        }
        if (maxConcurrentDispatches > 0) {
            queue.setMaxConcurrentDispatches(maxConcurrentDispatches);
        }
        if (maxAttempts > 0) {
            queue.setMaxAttempts(maxAttempts);
        }
        setRetryConfiguration(queue, minBackoffSeconds, maxBackoffSeconds, maxDoublings);
        queueStore.put(name, queue);
        return queue;
    }

    public StoredQueue getQueue(String name) {
        LOG.debugf("getQueue name=%s", name);
        return queueStore.get(name)
                .orElseThrow(() -> GcpException.notFound("Queue not found: " + name));
    }

    public List<StoredQueue> listQueues(String project, String location) {
        LOG.debugf("listQueues project=%s location=%s", project, location);
        String prefix = "projects/" + project + "/locations/" + location + "/queues/";
        return queueStore.scan(k -> k.startsWith(prefix));
    }

    public StoredQueue updateQueue(String name, double maxDispatchesPerSecond,
            int maxConcurrentDispatches, int maxAttempts) {
        return updateQueue(name, maxDispatchesPerSecond, maxConcurrentDispatches, maxAttempts, 1, 3600, 16);
    }

    public StoredQueue updateQueue(String name, double maxDispatchesPerSecond,
            int maxConcurrentDispatches, int maxAttempts, long minBackoffSeconds,
            long maxBackoffSeconds, int maxDoublings) {
        LOG.infof("updateQueue name=%s", name);
        StoredQueue queue = queueStore.get(name)
                .orElseThrow(() -> GcpException.notFound("Queue not found: " + name));
        if (maxDispatchesPerSecond > 0) {
            queue.setMaxDispatchesPerSecond(maxDispatchesPerSecond);
        }
        if (maxConcurrentDispatches > 0) {
            queue.setMaxConcurrentDispatches(maxConcurrentDispatches);
        }
        if (maxAttempts > 0) {
            queue.setMaxAttempts(maxAttempts);
        }
        setRetryConfiguration(queue, minBackoffSeconds, maxBackoffSeconds, maxDoublings);
        queueStore.put(name, queue);
        return queue;
    }

    public void deleteQueue(String name) {
        LOG.infof("deleteQueue name=%s", name);
        if (queueStore.get(name).isEmpty()) {
            throw GcpException.notFound("Queue not found: " + name);
        }
        String taskPrefix = name + "/tasks/";
        taskStore.scan(k -> k.startsWith(taskPrefix)).forEach(t -> taskStore.delete(t.getName()));
        queueStore.delete(name);
    }

    public StoredQueue purgeQueue(String name) {
        LOG.infof("purgeQueue name=%s", name);
        StoredQueue queue = queueStore.get(name)
                .orElseThrow(() -> GcpException.notFound("Queue not found: " + name));
        String taskPrefix = name + "/tasks/";
        taskStore.scan(k -> k.startsWith(taskPrefix)).forEach(t -> taskStore.delete(t.getName()));
        queue.setPurgeTime(clock.instant().toString());
        queueStore.put(name, queue);
        return queue;
    }

    public StoredQueue pauseQueue(String name) {
        LOG.infof("pauseQueue name=%s", name);
        StoredQueue queue = queueStore.get(name)
                .orElseThrow(() -> GcpException.notFound("Queue not found: " + name));
        queue.setState("PAUSED");
        queueStore.put(name, queue);
        return queue;
    }

    public StoredQueue resumeQueue(String name) {
        LOG.infof("resumeQueue name=%s", name);
        StoredQueue queue = queueStore.get(name)
                .orElseThrow(() -> GcpException.notFound("Queue not found: " + name));
        queue.setState("RUNNING");
        queueStore.put(name, queue);
        return queue;
    }

    // ── Tasks ──────────────────────────────────────────────────────────────────

    public StoredTask createTask(String queueName, String taskId, String taskType,
            String httpMethod, String url, Map<String, String> headers, byte[] body,
            String appEngineHttpMethod, String relativeUri, String scheduleTime) {
        return createTask(queueName, taskId, taskType, httpMethod, url, headers, body,
                appEngineHttpMethod, relativeUri, scheduleTime, 600);
    }

    public StoredTask createTask(String queueName, String taskId, String taskType,
            String httpMethod, String url, Map<String, String> headers, byte[] body,
            String appEngineHttpMethod, String relativeUri, String scheduleTime, long dispatchDeadlineSeconds) {
        if (queueStore.get(queueName).isEmpty()) {
            throw GcpException.notFound("Queue not found: " + queueName);
        }
        String id = (taskId != null && !taskId.isBlank()) ? taskId : UUID.randomUUID().toString();
        String name = queueName + "/tasks/" + id;
        LOG.infof("createTask name=%s", name);
        if (taskStore.get(name).isPresent()) {
            throw GcpException.alreadyExists("Task already exists: " + name);
        }
        StoredTask task = new StoredTask();
        task.setName(name);
        task.setCreateTime(clock.instant().toString());
        task.setScheduleTime(scheduleTime != null ? scheduleTime : clock.instant().toString());
        task.setDispatchDeadlineSeconds(validateDispatchDeadline(dispatchDeadlineSeconds));
        task.setTaskType(taskType);
        task.setHttpMethod(httpMethod);
        task.setUrl(url);
        task.setHeaders(headers);
        task.setBody(body);
        task.setAppEngineHttpMethod(appEngineHttpMethod);
        task.setRelativeUri(relativeUri);
        taskStore.put(name, task);
        return task;
    }

    public StoredTask getTask(String name) {
        LOG.debugf("getTask name=%s", name);
        return taskStore.get(name)
                .orElseThrow(() -> GcpException.notFound("Task not found: " + name));
    }

    public List<StoredTask> listTasks(String queueName) {
        LOG.debugf("listTasks queue=%s", queueName);
        if (queueStore.get(queueName).isEmpty()) {
            throw GcpException.notFound("Queue not found: " + queueName);
        }
        String prefix = queueName + "/tasks/";
        return taskStore.scan(k -> k.startsWith(prefix));
    }

    public void deleteTask(String name) {
        LOG.infof("deleteTask name=%s", name);
        if (taskStore.get(name).isEmpty()) {
            throw GcpException.notFound("Task not found: " + name);
        }
        taskStore.delete(name);
    }

    public StoredTask runTask(String name) {
        LOG.infof("runTask name=%s", name);
        StoredTask task = taskStore.get(name)
                .orElseThrow(() -> GcpException.notFound("Task not found: " + name));
        String injectedFailure = faults.consume("tasks.dispatch");
        if (injectedFailure != null) {
            throw GcpException.unavailable(injectedFailure);
        }
        task.setDispatchCount(task.getDispatchCount() + 1);
        if (dispatchHttpTask(task)) {
            taskStore.delete(name);
        } else {
            task.setResponseCount(task.getResponseCount() + 1);
            StoredQueue queue = getQueue(queueName(task));
            if (task.getDispatchCount() >= queue.getMaxAttempts()) {
                taskStore.delete(name);
            } else {
                task.setScheduleTime(clock.instant().plusSeconds(retryBackoffSeconds(queue, task.getResponseCount())).toString());
                taskStore.put(name, task);
            }
        }
        return task;
    }

    /** Dispatches due tasks for running queues; invoked by the background dispatcher and tests. */
    public void dispatchDue(Instant now) {
        for (StoredTask task : taskStore.scan(key -> true)) {
            try {
                if (task.getScheduleTime() == null || Instant.parse(task.getScheduleTime()).isAfter(now)) continue;
                String queueName = queueName(task);
                if (!"RUNNING".equals(getQueue(queueName).getState())) continue;
                runTask(task.getName());
            } catch (Exception e) {
                LOG.warnf("Due task %s was not dispatched: %s", task.getName(), e.getMessage());
            }
        }
    }

    private boolean dispatchHttpTask(StoredTask task) {
        if (!"HTTP".equals(task.getTaskType()) || task.getUrl() == null || task.getUrl().isBlank()) return false;
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder().uri(URI.create(task.getUrl()))
                    .timeout(Duration.ofSeconds(task.getDispatchDeadlineSeconds()));
            if (task.getHeaders() != null) task.getHeaders().forEach((name, value) -> {
                if (!isCloudTasksHeader(name)) request.header(name, value);
            });
            String queueName = queueName(task);
            request.header("X-CloudTasks-TaskName", task.getName());
            request.header("X-CloudTasks-QueueName", queueName);
            request.header("X-CloudTasks-TaskRetryCount", Integer.toString(task.getResponseCount()));
            request.header("X-CloudTasks-TaskExecutionCount", Integer.toString(task.getDispatchCount()));
            request.header("X-CloudTasks-TaskETA", task.getScheduleTime());
            byte[] body = task.getBody() == null ? new byte[0] : task.getBody();
            String method = task.getHttpMethod() == null || task.getHttpMethod().isBlank() ? "POST" : task.getHttpMethod();
            HttpResponse<Void> response = HTTP_CLIENT.send(request.method(method, HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            LOG.warnf("Task %s dispatch failed: %s", task.getName(), e.getMessage());
            return false;
        }
    }

    private static String queueName(StoredTask task) {
        return task.getName().substring(0, task.getName().lastIndexOf("/tasks/"));
    }

    private static boolean isCloudTasksHeader(String name) {
        return name.toUpperCase(Locale.ROOT).startsWith("X-CLOUDTASKS-");
    }

    private static long retryBackoffSeconds(StoredQueue queue, int responseCount) {
        int exponent = Math.min(Math.max(responseCount - 1, 0), Math.min(queue.getMaxDoublings(), 30));
        long multiplied = queue.getMinBackoffSeconds() * (1L << exponent);
        return Math.min(multiplied, queue.getMaxBackoffSeconds());
    }

    private static long validateDispatchDeadline(long seconds) {
        if (seconds < 15 || seconds > 1800) {
            throw GcpException.invalidArgument("Dispatch deadline must be between 15 seconds and 30 minutes");
        }
        return seconds;
    }

    private static void setRetryConfiguration(StoredQueue queue, long minBackoffSeconds,
            long maxBackoffSeconds, int maxDoublings) {
        if (minBackoffSeconds > 0) queue.setMinBackoffSeconds(minBackoffSeconds);
        if (maxBackoffSeconds > 0) queue.setMaxBackoffSeconds(maxBackoffSeconds);
        if (maxDoublings >= 0) queue.setMaxDoublings(maxDoublings);
    }
}
