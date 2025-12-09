package com.eriwen.gradle.js.source.internal;

import com.eriwen.gradle.js.source.JavaScriptProcessingChain;
import com.eriwen.gradle.js.source.JavaScriptSourceSet;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultNamedDomainObjectList;
import org.gradle.api.internal.tasks.DefaultTaskContainer;
import org.gradle.api.tasks.SourceTask;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.api.Namer;

import java.util.Collections;
import java.util.concurrent.Callable;

/**
 * Default implementation of a JavaScript processing chain.
 */
public class DefaultJavaScriptProcessingChain extends DefaultNamedDomainObjectList<SourceTask> implements JavaScriptProcessingChain {

    private final DefaultJavaScriptSourceSet source;
    private final Project project;

    /**
     * Creates a new JavaScript processing chain.
     * @param project the Gradle project
     * @param source the JavaScript source set
     * @param instantiator the instantiator for creating tasks
     */
    public DefaultJavaScriptProcessingChain(Project project, DefaultJavaScriptSourceSet source, Instantiator instantiator) {
        super(SourceTask.class, instantiator, (Namer<SourceTask>) SourceTask::getName, CollectionCallbackActionDecorator.NOOP);
        this.source = source;
        this.project = project;
        wireChain();
    }

    /**
     * {@inheritDoc}
     */
    public JavaScriptSourceSet getSource() {
        return source;
    }

    /**
     * Wires the processing chain so that each task's source is the output of the previous task.
     */
    protected void wireChain() {
        all(new Action<SourceTask>() {
            public void execute(final SourceTask sourceTask) {
                sourceTask.source(new Callable<FileCollection>() {
                    public FileCollection call() throws Exception {
                        int index = indexOf(sourceTask);
                        if (index == -1) {
                            return null; // task has been removed, noop
                        } else if (index == 0) {
                            return getSource().getJs();
                        } else {
                            SourceTask previous = get(index - 1);
                            return previous.getOutputs().getFiles();
                        }
                    }
                });
            }
        });
    }

    public <T extends SourceTask> T task(Class<T> type) {
        return task(calculateName(type), type);
    }
    
    public <T extends SourceTask> T task(String name, Class<T> type) {
        return task(name, type, null);
    }
    
    public <T extends SourceTask> T task(Class<T> type, Closure closure) {
        return task(calculateName(type), type, closure);
    }

    @SuppressWarnings("unchecked")
    public <T extends SourceTask> T task(String name, Class<T> type, Closure closure) {
        T task = project.getTasks().register(name, type, t -> {
            if (closure != null) {
                closure.setDelegate(t);
                closure.setResolveStrategy(Closure.DELEGATE_FIRST);
                closure.call(t);
            }
        }).get();
        add(task);
        return task;
    }
    
    /**
     * Calculates a task name based on the task type.
     * @param type the task class
     * @return the calculated task name
     */
    protected String calculateName(Class<? extends SourceTask> type) {
        String name = type.getName();
        if (name.endsWith("Task")) {
            name = name.substring(0, name.length() - 4);
        }

        return source.getName() + name;
    }
}
