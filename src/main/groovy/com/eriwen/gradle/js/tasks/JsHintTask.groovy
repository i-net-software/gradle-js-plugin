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

class JsHintTask extends SourceTask {
    private PatternSetFactory _patternSetFactory
    
    @Override
    protected PatternSetFactory getPatternSetFactory() {
        if (_patternSetFactory == null) {
            _patternSetFactory = project.services.get(PatternSetFactory.class)
        }
        return _patternSetFactory
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
        // SourceFile.fromFile() now takes String path instead of File in newer Closure Compiler versions
        def sourceFiles = source.files.collect { file ->
            SourceFile.fromFile(file.absolutePath, java.nio.charset.StandardCharsets.UTF_8)
        }
        
        // Run the compiler
        def result = compiler.compile(CommandLineRunner.getDefaultExterns(), sourceFiles, compilerOptions)
        
        // Process results - get errors and warnings from the compiler
        // Note: result.success indicates compilation success, but we check errors/warnings separately
        def errors = compiler.errors
        def warnings = compiler.warnings
        
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
        
        // Only throw exception on errors, not warnings (warnings are informational)
        // This matches JSHint behavior where warnings don't fail the build
        if (!ignoreExitCode && errors.size() > 0) {
            throw new GradleException("JavaScript validation failed with ${errors.size()} errors and ${warnings.size()} warnings")
        }
    }
    
    private void configureWarningLevels(CompilerOptions options) {
        // Set default warning levels - only use diagnostic groups that exist in current Closure Compiler
        options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING)
        // Note: Many STRICT_* diagnostic groups were removed/renamed in newer Closure Compiler versions
        // Use reflection to safely check if diagnostic groups exist before using them
        setWarningLevelIfExistsInline(options, "STRICT_MISSING_PROPERTIES", CheckLevel.WARNING)
        setWarningLevelIfExistsInline(options, "STRICT_MISSING_RETURN", CheckLevel.WARNING)
        setWarningLevelIfExistsInline(options, "STRICT_MODULE_DEP_CHECK", CheckLevel.WARNING)
        setWarningLevelIfExistsInline(options, "STRICT_PRIMITIVE_OPERATORS", CheckLevel.WARNING)
        setWarningLevelIfExistsInline(options, "STRICT_TYPE_CHECKS", CheckLevel.WARNING)
        setWarningLevelIfExistsInline(options, "STRICT_VARIABLE_CHECKS", CheckLevel.WARNING)
        
        // Apply custom options from project.jshint.options
        if (project.jshint.options) {
            project.jshint.options.each { key, value ->
                switch (key) {
                    case 'undef':
                        setWarningLevelIfExistsInline(options, "STRICT_VARIABLE_CHECKS", 
                            value == 'true' ? CheckLevel.WARNING : CheckLevel.OFF)
                        break
                    case 'unused':
                        setWarningLevelIfExistsInline(options, "UNUSED_LOCAL_VARIABLE", 
                            value == 'true' ? CheckLevel.WARNING : CheckLevel.OFF)
                        break
                    case 'strict':
                        setWarningLevelIfExistsInline(options, "STRICT_MISSING_PROPERTIES", 
                            value == 'true' ? CheckLevel.WARNING : CheckLevel.OFF)
                        break
                    // Add more option mappings as needed
                }
            }
        }
    }
    
    private void setWarningLevelIfExistsInline(CompilerOptions options, String groupName, CheckLevel level) {
        try {
            java.lang.reflect.Field groupField = DiagnosticGroups.class.getField(groupName)
            Object group = groupField.get(null)
            options.setWarningLevel(group as DiagnosticGroup, level)
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Diagnostic group doesn't exist in this version of Closure Compiler, skip it
        }
    }
}
