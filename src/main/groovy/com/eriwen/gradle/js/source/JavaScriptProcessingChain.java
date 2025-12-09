package com.eriwen.gradle.js.source;

import groovy.lang.Closure;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.tasks.SourceTask;

/**
 * A chain of JavaScript processing tasks that can be applied to a source set.
 */
public interface JavaScriptProcessingChain extends NamedDomainObjectList<SourceTask> {

    /**
     * Gets the source set associated with this processing chain.
     * @return the JavaScript source set
     */
    JavaScriptSourceSet getSource();

    /**
     * Creates a task with an auto-generated name based on the task type.
     * @param type the task class
     * @param <T> the task type
     * @return the created task
     */
    <T extends SourceTask> T task(Class<T> type);
    
    /**
     * Creates a task with the specified name.
     * @param name the task name
     * @param type the task class
     * @param <T> the task type
     * @return the created task
     */
    <T extends SourceTask> T task(String name, Class<T> type);
    
    /**
     * Creates a task with an auto-generated name and configures it with a closure.
     * @param type the task class
     * @param closure the configuration closure
     * @param <T> the task type
     * @return the created task
     */
    <T extends SourceTask> T task(Class<T> type, Closure closure);
    
    /**
     * Creates a task with the specified name and configures it with a closure.
     * @param name the task name
     * @param type the task class
     * @param closure the configuration closure
     * @param <T> the task type
     * @return the created task
     */
    <T extends SourceTask> T task(String name, Class<T> type, Closure closure);

}
