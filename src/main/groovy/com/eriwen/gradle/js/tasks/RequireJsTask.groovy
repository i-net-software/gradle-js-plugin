/**
 * Copyright 2012 Joe Fitzgerald
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.eriwen.gradle.js.tasks

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import com.eriwen.gradle.js.ResourceUtil
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.util.internal.PatternSetFactory
import org.gradle.process.JavaExecSpec
import org.gradle.api.tasks.JavaExec

class RequireJsTask extends SourceTask {
    private PatternSetFactory _patternSetFactory
    
    @Override
    protected PatternSetFactory getPatternSetFactory() {
        if (_patternSetFactory == null) {
            _patternSetFactory = project.services.get(PatternSetFactory.class)
        }
        return _patternSetFactory
    }
    private static final String REQUIREJS_PATH = 'r.js'
    private static final String TMP_DIR = "tmp${File.separator}js"
    private static final ResourceUtil RESOURCE_UTIL = new ResourceUtil()

    @OutputDirectory
    @Optional
    def destDir

    @OutputFile
    @Optional
    def dest

    @Input
    def ignoreExitCode = false

    @Input @Optional
    String rhinoMaxHeapSize

    @TaskAction
    def run() {
        LinkedHashMap<String, Object> options = [] // [optimize: "none", logLevel: 2, skipModuleInsertion: false, out: dest]
        options.putAll(project.requirejs.options)

        final File requireJsFile
        if (project.requirejs.impl != null && project.requirejs.impl.class == File) {
            requireJsFile = new File("${project.requirejs.impl.canonicalPath}")
        } else {
            requireJsFile = RESOURCE_UTIL.extractFileToDirectory(new File(project.buildDir, TMP_DIR), REQUIREJS_PATH)
        }

        final List<String> args = [requireJsFile.canonicalPath]
        args.add("-o")
        if (project.requirejs.buildprofile != null && project.requirejs.buildprofile.class == File && project.requirejs.buildprofile.exists()) {
            args.add("${project.requirejs.buildprofile.canonicalPath}")
        }

        if (destDir) {
            args.add("dir=${ project.file(destDir).canonicalPath}")
        }
        if (dest) {
            args.add("out=${ project.file(dest).canonicalPath}")
        }

        options.each() { key, value ->
            logger.debug("${key} == ${options[value]}")
            def keyAlreadyAdded = (key.equalsIgnoreCase("out") && dest) || (key.equalsIgnoreCase("dir") && destDir)
            if (!keyAlreadyAdded) {
                args.add("${key}=${value}")
            }
        }

        // Execute RequireJS using Rhino via JavaExec
        try {
            // Use project.javaexec if available, otherwise create JavaExec task directly
            try {
                project.javaexec { JavaExecSpec spec ->
                    spec.classpath = project.configurations.rhino
                    spec.main = 'org.mozilla.javascript.tools.shell.Main'
                    spec.args = args
                    spec.workingDir = project.projectDir
                    if (rhinoMaxHeapSize) {
                        spec.maxHeapSize = rhinoMaxHeapSize
                    }
                    spec.ignoreExitValue = ignoreExitCode
                }
            } catch (MissingMethodException e) {
                // Fallback: use ProcessBuilder for test contexts where javaexec is not available
                def javaHome = System.getProperty('java.home')
                def javaExec = new File(javaHome, 'bin/java').absolutePath
                def classpathString = project.configurations.rhino.files.collect { it.absolutePath }.join(System.getProperty('path.separator'))
                
                def command = [javaExec, '-cp', classpathString, 'org.mozilla.javascript.tools.shell.Main'] as List<String>
                command.addAll(args.collect { it.toString() })
                def processBuilder = new ProcessBuilder(command)
                processBuilder.directory(project.projectDir)
                if (rhinoMaxHeapSize) {
                    def env = processBuilder.environment()
                    env.put('JAVA_OPTS', "-Xmx${rhinoMaxHeapSize}")
                }
                
                def process = processBuilder.start()
                // Consume output streams to prevent process from hanging
                def outputThread = Thread.start {
                    process.inputStream.eachLine { line -> logger.debug(line) }
                }
                def errorThread = Thread.start {
                    process.errorStream.eachLine { line -> logger.error(line) }
                }
                def exitCode = process.waitFor()
                outputThread.join()
                errorThread.join()
                
                if (exitCode != 0 && !ignoreExitCode) {
                    throw new org.gradle.process.internal.ExecException("RequireJS execution failed with exit code ${exitCode}")
                }
            }
        } catch (org.gradle.process.internal.ExecException e) {
            if (!ignoreExitCode) {
                throw e
            }
        } catch (Exception e) {
            // For other exceptions, check if we should ignore
            if (!ignoreExitCode) {
                throw new org.gradle.process.internal.ExecException("RequireJS execution failed: ${e.message}", e)
            }
        }
    }
}
