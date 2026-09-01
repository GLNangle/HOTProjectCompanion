package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.net.URI;

/** Identifies one task on a supported HOT Tasking Manager instance. */
public final class TaskReference {
    private final URI instance;
    private final long projectId;
    private final long taskId;

    TaskReference(URI instance, long projectId, long taskId) {
        this.instance = instance;
        this.projectId = projectId;
        this.taskId = taskId;
    }

    static TaskReference forHotTask(long projectId, long taskId) {
        return new TaskReference(URI.create("https://tasks.hotosm.org"), projectId, taskId);
    }

    public URI getInstance() {
        return instance;
    }

    public long getProjectId() {
        return projectId;
    }

    public long getTaskId() {
        return taskId;
    }
}
