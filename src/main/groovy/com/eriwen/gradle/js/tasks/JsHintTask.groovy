/**
 * Copyright 2012 Eric Wendelin
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

import com.google.javascript.jscomp.*
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.util.internal.PatternSetFactory
import org.gradle.api.GradleException
import javax.inject.Inject

class JsHintTask extends SourceTask {
    @Inject
    private PatternSetFactory patternSetFactory
    
    protected PatternSetFactory getPatternSetFactory() {
        return patternSetFactory
    }
    @OutputFile def dest = new File(project.buildDir, "jshint.log")
    @Input def ignoreExitCode = true
    @Input def outputToStdOut = false
    @Input def reporter = ''

    File getDest() {
        project.file(dest)
    }

    @TaskAction
    def run() {
        def compiler = new Compiler()
        def compilerOptions = new CompilerOptions()
        
        // Configure warning levels based on JSHint options
        configureWarningLevels(compilerOptions)
        
        // Set up source files
        def sourceFiles = source.files.collect { 
            SourceFile.fromFile(it)
        }
        
        // Run the compiler
        def result = compiler.compile(CommandLineRunner.getDefaultExterns(), sourceFiles, compilerOptions)
        
        // Process results
        def warnings = compiler.getWarnings()
        def errors = compiler.getErrors()
        
        // Write output
        def output = new StringBuilder()
        if (reporter == 'checkstyle') {
            output.append('<?xml version="1.0" encoding="UTF-8"?>\n')
            output.append('<checkstyle version="4.3">\n')
            
            // Group warnings by file
            def warningsByFile = warnings.groupBy { it.sourceName }
            warningsByFile.each { fileName, fileWarnings ->
                output.append("  <file name=\"${fileName}\">\n")
                fileWarnings.each { warning ->
                    output.append("    <error line=\"${warning.lineNumber}\" ")
                    output.append("severity=\"warning\" ")
                    output.append("message=\"${warning.description}\" ")
                    output.append("source=\"com.google.javascript.jscomp.ClosureCompiler\"/>\n")
                }
                output.append("  </file>\n")
            }
            output.append('</checkstyle>')
        } else {
            warnings.each { warning ->
                output.append("${warning.sourceName}:${warning.lineNumber}: ${warning.description}\n")
            }
        }
        
        if (outputToStdOut) {
            println output.toString()
        } else {
            dest.text = output.toString()
        }
        
        if (!ignoreExitCode && (errors.size() > 0 || warnings.size() > 0)) {
            throw new GradleException("JavaScript validation failed with ${errors.size()} errors and ${warnings.size()} warnings")
        }
    }
    
    private void configureWarningLevels(CompilerOptions options) {
        // Set default warning levels
        options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING)
        options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_PROPERTIES, CheckLevel.WARNING)
        options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_REQUIRE, CheckLevel.WARNING)
        options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_RETURN, CheckLevel.WARNING)
        options.setWarningLevel(DiagnosticGroups.STRICT_MODULE_DEP_CHECK, CheckLevel.WARNING)
        options.setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.WARNING)
        options.setWarningLevel(DiagnosticGroups.STRICT_TYPE_CHECKS, CheckLevel.WARNING)
        options.setWarningLevel(DiagnosticGroups.STRICT_VARIABLE_CHECKS, CheckLevel.WARNING)
        
        // Apply custom options from project.jshint.options
        if (project.jshint.options) {
            project.jshint.options.each { key, value ->
                switch (key) {
                    case 'undef':
                        options.setWarningLevel(DiagnosticGroups.STRICT_VARIABLE_CHECKS, 
                            value == 'true' ? CheckLevel.WARNING : CheckLevel.OFF)
                        break
                    case 'unused':
                        options.setWarningLevel(DiagnosticGroups.UNUSED_LOCAL_VARIABLE, 
                            value == 'true' ? CheckLevel.WARNING : CheckLevel.OFF)
                        break
                    case 'strict':
                        options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_PROPERTIES, 
                            value == 'true' ? CheckLevel.WARNING : CheckLevel.OFF)
                        break
                    // Add more option mappings as needed
                }
            }
        }
    }
}
