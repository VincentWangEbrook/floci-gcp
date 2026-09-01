package io.floci.gcp.services.tasks;

import io.floci.gcp.core.common.GcpException;
import io.floci.gcp.core.common.TestFaultInjector;
import io.floci.gcp.core.storage.InMemoryStorage;
import io.floci.gcp.services.tasks.model.StoredQueue;
import io.floci.gcp.services.tasks.model.StoredTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CloudTasksServiceTest {

    private CloudTasksService service;
    private static final String QUEUE = "projects/p1/locations/us-east1/queues/q1";

    @BeforeEach
    void setUp() {
        service = new CloudTasksService(
                new InMemoryStorage<>(),
                new InMemoryStorage<>());
    }

    @Test
    void createQueueIsRunningState() {
        StoredQueue queue = service.createQueue("p1", "us-east1", "q1", 0, 0, 0);
        assertEquals(QUEUE, queue.getName());
        assertEquals("RUNNING", queue.getState());
    }

    @Test
    void createQueueDuplicateThrowsAlreadyExists() {
        service.createQueue("p1", "us-east1", "q1", 0, 0, 0);

        GcpException ex = assertThrows(GcpException.class,
                () -> service.createQueue("p1", "us-east1", "q1", 0, 0, 0));
        assertEquals("ALREADY_EXISTS", ex.getGcpStatus());
    }

    @Test
    void getQueueMissingThrowsNotFound() {
        GcpException ex = assertThrows(GcpException.class,
                () -> service.getQueue("projects/p1/locations/us-east1/queues/missing"));
        assertEquals("NOT_FOUND", ex.getGcpStatus());
    }

    @Test
    void pauseQueueTransitionsToPaused() {
        service.createQueue("p1", "us-east1", "q1", 0, 0, 0);
        StoredQueue paused = service.pauseQueue(QUEUE);
        assertEquals("PAUSED", paused.getState());
    }

    @Test
    void resumeQueueTransitionsToRunning() {
        service.createQueue("p1", "us-east1", "q1", 0, 0, 0);
        service.pauseQueue(QUEUE);
        StoredQueue running = service.resumeQueue(QUEUE);
        assertEquals("RUNNING", running.getState());
    }

    @Test
    void createTaskGeneratesNameWhenIdBlank() {
        service.createQueue("p1", "us-east1", "q1", 0, 0, 0);
        StoredTask task = service.createTask(QUEUE, null, "HTTP",
                "POST", "https://example.com", Map.of(), new byte[0], null, null, null);
        assertNotNull(task.getName());
        assertTrue(task.getName().startsWith(QUEUE + "/tasks/"));
    }

    @Test
    void getTaskMissingThrowsNotFound() {
        service.createQueue("p1", "us-east1", "q1", 0, 0, 0);

        GcpException ex = assertThrows(GcpException.class,
                () -> service.getTask(QUEUE + "/tasks/missing"));
        assertEquals("NOT_FOUND", ex.getGcpStatus());
    }

    @Test
    void deleteTaskRemovedFromList() {
        service.createQueue("p1", "us-east1", "q1", 0, 0, 0);
        StoredTask task = service.createTask(QUEUE, "t1", "HTTP",
                "POST", "https://example.com", Map.of(), new byte[0], null, null, null);

        service.deleteTask(task.getName());

        List<StoredTask> tasks = service.listTasks(QUEUE);
        assertTrue(tasks.stream().noneMatch(t -> t.getName().equals(task.getName())));
    }

    @Test
    void deleteQueueCascadesTasks() {
        service.createQueue("p1", "us-east1", "q1", 0, 0, 0);
        service.createTask(QUEUE, "t1", "HTTP", "POST", "https://example.com",
                Map.of(), new byte[0], null, null, null);

        service.deleteQueue(QUEUE);

        GcpException ex = assertThrows(GcpException.class, () -> service.getQueue(QUEUE));
        assertEquals("NOT_FOUND", ex.getGcpStatus());
    }

    @Test
    void purgeQueueRemovesAllTasks() {
        service.createQueue("p1", "us-east1", "q1", 0, 0, 0);
        service.createTask(QUEUE, "t1", "HTTP", "POST", "https://example.com",
                Map.of(), new byte[0], null, null, null);
        service.createTask(QUEUE, "t2", "HTTP", "POST", "https://example.com",
                Map.of(), new byte[0], null, null, null);

        service.purgeQueue(QUEUE);

        assertTrue(service.listTasks(QUEUE).isEmpty());
    }

    @Test
    void runTaskSurfacesAnInjectedDispatchFailure() {
        TestFaultInjector faults = new TestFaultInjector(true);
        service = new CloudTasksService(new InMemoryStorage<>(), new InMemoryStorage<>(), faults);
        service.createQueue("p1", "us-east1", "q1", 0, 0, 0);
        StoredTask task = service.createTask(QUEUE, "t1", "HTTP", "POST", "http://localhost", Map.of(),
                new byte[0], null, null, null);
        faults.arm("tasks.dispatch", "simulated task timeout");

        GcpException error = assertThrows(GcpException.class, () -> service.runTask(task.getName()));
        assertEquals("UNAVAILABLE", error.getGcpStatus());
    }

    @Test
    void runTaskDispatchesHttpAndDeletesOnlyAfterA2xxResponse() throws Exception {
        AtomicReference<String> taskNameHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/worker", exchange -> {
            taskNameHeader.set(exchange.getRequestHeaders().getFirst("X-CloudTasks-TaskName"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            service.createQueue("p1", "us-east1", "q1", 0, 0, 0);
            StoredTask task = service.createTask(QUEUE, "t1", "HTTP", "POST",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/worker", Map.of(), new byte[0], null, null, null);

            service.runTask(task.getName());

            assertEquals(task.getName(), taskNameHeader.get());
            assertThrows(GcpException.class, () -> service.getTask(task.getName()));
        } finally {
            server.stop(0);
        }
    }
}
