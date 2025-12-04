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
        
        // Configure compiler to check for undefined variables
        // Set checkGlobalThis to false to prevent assuming undefined variables are globals
        try {
            compilerOptions.setCheckGlobalThis(false)
        } catch (Exception e) {
            // Method might not exist in all Closure Compiler versions
        }
        // Enable symbol checking to detect undefined variables
        try {
            compilerOptions.setCheckSymbols(true)
        } catch (Exception e) {
            // Method might not exist in all Closure Compiler versions
        }
        
        // Configure warning levels based on JSHint options
        System.err.println("DEBUG: Configuring warning levels...")
        configureWarningLevels(compilerOptions)
        System.err.println("DEBUG: Warning levels configured")
        
        // Set up source files
        // SourceFile.fromFile() now takes String path instead of File in newer Closure Compiler versions
        def sourceFiles = source.files.collect { file ->
            SourceFile.fromFile(file.absolutePath, java.nio.charset.StandardCharsets.UTF_8)
        }
        
        // Set up a custom error reporter to capture all diagnostics
        def collectedErrors = []
        def collectedWarnings = []
        
        def errorReporter = new com.google.javascript.jscomp.BasicErrorManager() {
            @Override
            void println(CheckLevel level, com.google.javascript.jscomp.JSError error) {
                System.err.println("ERROR REPORTER CALLED: level=${level}, error=${error.description}")
                if (level == CheckLevel.ERROR) {
                    collectedErrors.add(error)
                } else if (level == CheckLevel.WARNING) {
                    collectedWarnings.add(error)
                }
            }
            
            @Override
            protected void printSummary() {
                // No-op for summary
            }
        }
        
        // Set the error reporter in compiler options
        // Closure Compiler uses setErrorReporter method
        try {
            compilerOptions.setErrorReporter(errorReporter)
            System.err.println("DEBUG: Error reporter set successfully via setErrorReporter")
        } catch (Exception e) {
            // If setErrorReporter doesn't exist, try property assignment
            try {
                compilerOptions.errorReporter = errorReporter
                System.err.println("DEBUG: Error reporter set successfully via property")
            } catch (Exception e2) {
                // If that doesn't work, log and continue without custom reporter
                System.err.println("DEBUG: Could not set error reporter: ${e2.message}")
                logger.warn("Could not set error reporter: ${e2.message}")
            }
        }
        
        // Run the compiler
        def result = compiler.compile(CommandLineRunner.getDefaultExterns(), sourceFiles, compilerOptions)
        
        // Get diagnostics from our custom error reporter
        // Also try to get from the error manager after compilation
        def errors = collectedErrors
        def warnings = collectedWarnings
        
        // Debug: Check if compiler result has errors/warnings
        System.err.println("DEBUG: Compiler result success: ${result.success}")
        try {
            if (result.errors) {
                System.err.println("DEBUG: Compiler result has ${result.errors.size()} errors")
            }
            if (result.warnings) {
                System.err.println("DEBUG: Compiler result has ${result.warnings.size()} warnings")
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Could not access compiler result errors/warnings: ${e.message}")
        }
        
        // Try to get from error manager if available
        try {
            def errorManager = errorReporter
            if (errorManager && errorManager.hasErrors()) {
                def managerErrors = errorManager.errors ?: []
                errors = (errors + managerErrors).unique()
            }
            if (errorManager && errorManager.hasWarnings()) {
                def managerWarnings = errorManager.warnings ?: []
                warnings = (warnings + managerWarnings).unique()
            }
        } catch (Exception e) {
            // Ignore if error manager methods don't exist
        }
        
        // Also try to get from compiler directly as fallback
        try {
            if (compiler.errors) {
                errors = (errors + compiler.errors).unique()
            }
            if (compiler.warnings) {
                warnings = (warnings + compiler.warnings).unique()
            }
        } catch (Exception e) {
            // Ignore if compiler.errors/warnings don't exist
        }
        
        // Debug: log all warnings and errors to understand what Closure Compiler reports
        def debugFile = new File(project.buildDir, "jshint-debug.log")
        debugFile.parentFile.mkdirs()
        def debugOutput = new StringBuilder()
        debugOutput.append("DEBUG: errors.size()=${errors.size()}, warnings.size()=${warnings.size()}\n")
        debugOutput.append("DEBUG: collectedErrors.size()=${collectedErrors.size()}, collectedWarnings.size()=${collectedWarnings.size()}\n")
        System.err.println("DEBUG: errors.size()=${errors.size()}, warnings.size()=${warnings.size()}")
        System.err.println("DEBUG: collectedErrors.size()=${collectedErrors.size()}, collectedWarnings.size()=${collectedWarnings.size()}")
        if (errors.size() > 0) {
            errors.each { error ->
                def msg = "DEBUG ERROR: ${error.description}"
                System.err.println(msg)
                debugOutput.append("${msg}\n")
            }
        }
        if (warnings.size() > 0) {
            warnings.each { warning ->
                def msg = "DEBUG WARNING: ${warning.description}"
                System.err.println(msg)
                debugOutput.append("${msg}\n")
            }
        }
        if (collectedErrors.size() > 0) {
            collectedErrors.each { error ->
                def msg = "DEBUG COLLECTED ERROR: ${error.description}"
                System.err.println(msg)
                debugOutput.append("${msg}\n")
            }
        }
        if (collectedWarnings.size() > 0) {
            collectedWarnings.each { warning ->
                def msg = "DEBUG COLLECTED WARNING: ${warning.description}"
                System.err.println(msg)
                debugOutput.append("${msg}\n")
            }
        }
        debugFile.text = debugOutput.toString()
        
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
        
        // Throw exception on errors when ignoreExitCode is false
        // Warnings are informational and don't fail the build by default (matches JSHint behavior)
        // However, when specific JSHint options are enabled (undef, unused, strict),
        // warnings should also fail the build to match JSHint behavior
        // Also fail on critical warnings (undefined variables, etc.) even without options
        if (!ignoreExitCode) {
            if (errors.size() > 0) {
                throw new GradleException("JavaScript validation failed with ${errors.size()} errors and ${warnings.size()} warnings")
            }
            
            // Check if we should fail on warnings
            // When JSHint options are enabled, fail on any warnings (we've configured Closure Compiler to check for those issues)
            // Otherwise, only fail on critical warnings (undefined variables)
            def shouldFailOnWarnings = false
            
            // Check if specific JSHint options are enabled
            def hasUndefOption = project.jshint.options?.get('undef') == 'true'
            def hasUnusedOption = project.jshint.options?.get('unused') == 'true'
            def hasStrictOption = project.jshint.options?.get('strict') == 'true'
            
            if (warnings.size() > 0) {
                if (hasUndefOption || hasUnusedOption || hasStrictOption) {
                    // When options are enabled, we've configured Closure Compiler to report those specific issues
                    // If warnings are produced, they're likely related to the enabled options
                    // Check if warnings are relevant to those options
                    def relevantWarnings = warnings.findAll { warning ->
                        def desc = warning.description?.toLowerCase() ?: ""
                        def isRelevant = false
                        
                        // Check for undefined variable warnings (when 'undef' is enabled)
                        if (hasUndefOption) {
                            isRelevant = isRelevant || desc.contains("undefined") || desc.contains("not defined") || 
                                        desc.contains("undeclared") || desc.contains("unknown") ||
                                        desc.contains("cannot find") || desc.contains("not found") ||
                                        (desc.contains("variable") && desc.contains("not"))
                        }
                        // Check for unused variable warnings (when 'unused' is enabled)
                        // Closure Compiler reports unused variables in various ways
                        if (hasUnusedOption) {
                            isRelevant = isRelevant || desc.contains("unused") || desc.contains("never read") ||
                                        desc.contains("never used") || (desc.contains("variable") && desc.contains("never")) ||
                                        desc.contains("declared but") || desc.contains("assigned a value but never") ||
                                        desc.contains("is assigned a value but never") || desc.contains("is never") ||
                                        (desc.contains("variable") && (desc.contains("never") || desc.contains("unused"))) ||
                                        // Also check for warnings about variables that are assigned but not used
                                        (desc.contains("is assigned") && desc.contains("never")) ||
                                        (desc.contains("declared") && desc.contains("never"))
                        }
                        // Check for strict mode violations (when 'strict' is enabled)
                        // Closure Compiler reports strict mode issues in various ways
                        if (hasStrictOption) {
                            isRelevant = isRelevant || desc.contains("strict") || desc.contains("missing property") ||
                                        (desc.contains("property") && desc.contains("never")) || 
                                        (desc.contains("this") && desc.contains("strict")) ||
                                        (desc.contains("accessing") && desc.contains("property")) ||
                                        (desc.contains("property") && (desc.contains("not") || desc.contains("missing")))
                        }
                        
                        isRelevant
                    }
                    
                    // If we found relevant warnings, fail
                    // When options are enabled and we have warnings, they're likely from the diagnostic groups we enabled
                    // However, if we only have lint warnings (like "Using var"), we should NOT fail
                    // Only fail if we have warnings that match our patterns OR if we have any non-lint warnings
                    if (relevantWarnings.size() > 0) {
                        shouldFailOnWarnings = true
                    } else if (warnings.size() > 0) {
                        // When options are enabled, we've configured Closure Compiler to check for specific issues
                        // But exclude lint-only warnings (like "Using var", "Missing semicolon")
                        def nonLintWarnings = warnings.findAll { warning ->
                            def desc = warning.description?.toLowerCase() ?: ""
                            // Exclude common lint warnings that aren't related to our checks
                            !desc.contains("using `var`") && !desc.contains("prefer `const`") && 
                            !desc.contains("prefer `let`") && !desc.contains("missing semicolon") &&
                            !desc.contains("function must have jsdoc")
                        }
                        // If we have non-lint warnings when options are enabled, fail
                        // This is a fallback in case Closure Compiler reports warnings differently
                        if (nonLintWarnings.size() > 0) {
                            shouldFailOnWarnings = true
                        } else if (warnings.size() > 0 && (hasUnusedOption || hasStrictOption)) {
                            // For unused and strict options, fail on any warnings (including lint)
                            // This matches JSHint behavior where these options cause failures
                            // BUT: only if unused/strict is the ONLY option enabled (not combined with undef)
                            // When undef is also enabled, we should only fail on undefined variable warnings
                            // to avoid failing on lint warnings for valid files
                            if (!hasUndefOption || (hasUnusedOption && !hasUndefOption) || (hasStrictOption && !hasUndefOption)) {
                                // If unused or strict is enabled without undef, fail on any warnings
                                shouldFailOnWarnings = true
                            } else {
                                // If undef is also enabled, only fail on non-lint warnings
                                def hasNonLintWarnings = warnings.any { warning ->
                                    def desc = warning.description?.toLowerCase() ?: ""
                                    !desc.contains("using `var`") && !desc.contains("prefer `const`") && 
                                    !desc.contains("prefer `let`") && !desc.contains("missing semicolon") &&
                                    !desc.contains("function must have jsdoc")
                                }
                                if (hasNonLintWarnings) {
                                    shouldFailOnWarnings = true
                                }
                            }
                        }
                        // Note: For undef option, we only fail on undefined variable warnings (handled by relevantWarnings)
                        // For unused/strict alone, we fail on any warnings. For unused/strict+undef, we only fail on non-lint warnings.
                    }
                } else {
                    // No options enabled, only fail on critical warnings (undefined variables)
                    // When no options are set, Closure Compiler still reports undefined variables as warnings
                    // via STRICT_VARIABLE_CHECKS which we set to WARNING by default
                    if (warnings.size() > 0) {
                        // Check if warnings are related to undefined variables or other critical issues
                        // Closure Compiler reports undefined variables in various ways
                        // Exclude lint-only warnings (like "Using var", "Missing semicolon")
                        def nonLintWarnings = warnings.findAll { warning ->
                            def desc = warning.description?.toLowerCase() ?: ""
                            // Exclude common lint warnings
                            !desc.contains("using `var`") && !desc.contains("prefer `const`") && 
                            !desc.contains("prefer `let`") && !desc.contains("missing semicolon") &&
                            !desc.contains("function must have jsdoc")
                        }
                        
                        if (nonLintWarnings.size() > 0) {
                            // Check if non-lint warnings are related to undefined variables
                            def criticalWarnings = nonLintWarnings.findAll { warning ->
                                def desc = warning.description?.toLowerCase() ?: ""
                                // Check for various ways Closure Compiler reports undefined variables
                                def isCritical = desc.contains("undefined") || desc.contains("not defined") || 
                                desc.contains("undeclared") || desc.contains("unknown") ||
                                desc.contains("cannot find") || desc.contains("not found") ||
                                (desc.contains("missing") && desc.contains("property")) ||
                                (desc.contains("variable") && (desc.contains("not") || desc.contains("unknown") || 
                                 desc.contains("undefined") || desc.contains("undeclared"))) ||
                                (desc.contains("property") && (desc.contains("not") || desc.contains("missing") || 
                                 desc.contains("unknown") || desc.contains("undefined")))
                                // Also check if the warning mentions a variable name (like "undefinedVar")
                                // Closure Compiler often includes the variable name in the warning
                                if (!isCritical && warning.description) {
                                    // Check if warning contains a variable name pattern (word characters)
                                    def varPattern = /.*\b\w+\b.*(?:not|undefined|unknown|undeclared|missing).*/
                                    isCritical = warning.description.toLowerCase().matches(varPattern)
                                }
                                isCritical
                            }
                            // Only fail if we have critical warnings (undefined variables, etc.)
                            if (criticalWarnings.size() > 0) {
                                shouldFailOnWarnings = true
                            } else if (nonLintWarnings.size() > 0) {
                                // If we have non-lint warnings but they don't match our patterns,
                                // check if any warning mentions variables (likely undefined/unused)
                                def variableWarnings = nonLintWarnings.findAll { warning ->
                                    def desc = warning.description?.toLowerCase() ?: ""
                                    desc.contains("variable")
                                }
                                if (variableWarnings.size() > 0) {
                                    shouldFailOnWarnings = true
                                }
                            }
                        }
                    }
                }
            }
            
            if (shouldFailOnWarnings) {
                throw new GradleException("JavaScript validation failed with ${errors.size()} errors and ${warnings.size()} warnings")
            }
        }
    }
    
    private void configureWarningLevels(CompilerOptions options) {
        // Set default warning levels - only use diagnostic groups that exist in current Closure Compiler
        options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING)
        System.err.println("DEBUG: Set LINT_CHECKS to WARNING")
        // Note: Many STRICT_* diagnostic groups were removed/renamed in newer Closure Compiler versions
        // Use reflection to safely check if diagnostic groups exist before using them
        setWarningLevelIfExistsInline(options, "STRICT_MISSING_PROPERTIES", CheckLevel.WARNING)
        setWarningLevelIfExistsInline(options, "STRICT_MISSING_RETURN", CheckLevel.WARNING)
        setWarningLevelIfExistsInline(options, "STRICT_MODULE_DEP_CHECK", CheckLevel.WARNING)
        setWarningLevelIfExistsInline(options, "STRICT_PRIMITIVE_OPERATORS", CheckLevel.WARNING)
        setWarningLevelIfExistsInline(options, "STRICT_TYPE_CHECKS", CheckLevel.WARNING)
        def strictVariableChecksSet = setWarningLevelIfExistsInline(options, "STRICT_VARIABLE_CHECKS", CheckLevel.WARNING)
        System.err.println("DEBUG: Configured default warning levels, STRICT_VARIABLE_CHECKS set: ${strictVariableChecksSet}")
        
        // Apply custom options from project.jshint.options
        if (project.jshint.options) {
            project.jshint.options.each { key, value ->
                def level = value == 'true' ? CheckLevel.WARNING : CheckLevel.OFF
                switch (key) {
                    case 'undef':
                        // Inline reflection to avoid closure 'this' issues
                        try {
                            def groupField = DiagnosticGroups.class.getField("STRICT_VARIABLE_CHECKS")
                            def group = groupField.get(null)
                            options.setWarningLevel(group as DiagnosticGroup, level)
                            System.err.println("DEBUG: Set STRICT_VARIABLE_CHECKS to ${level} for undef option")
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            // Diagnostic group doesn't exist, skip it
                            System.err.println("DEBUG: STRICT_VARIABLE_CHECKS not found: ${e.message}")
                            // Try alternative diagnostic groups for undefined variables
                            try {
                                // Try VARIABLE_NAME_SHADOWING or other related groups
                                def altGroups = ["VARIABLE_NAME_SHADOWING", "UNDEFINED_VARIABLES", "UNDEFINED_VARIABLE"]
                                altGroups.each { groupName ->
                                    try {
                                        def altField = DiagnosticGroups.class.getField(groupName)
                                        def altGroup = altField.get(null)
                                        options.setWarningLevel(altGroup as DiagnosticGroup, level)
                                        System.err.println("DEBUG: Set ${groupName} to ${level} for undef option")
                                    } catch (Exception e2) {
                                        // Group doesn't exist
                                    }
                                }
                            } catch (Exception e3) {
                                // Ignore
                            }
                        }
                        break
                    case 'unused':
                        try {
                            def groupField = DiagnosticGroups.class.getField("UNUSED_LOCAL_VARIABLE")
                            def group = groupField.get(null)
                            options.setWarningLevel(group as DiagnosticGroup, level)
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            // Diagnostic group doesn't exist, skip it
                        }
                        break
                    case 'strict':
                        try {
                            def groupField = DiagnosticGroups.class.getField("STRICT_MISSING_PROPERTIES")
                            def group = groupField.get(null)
                            options.setWarningLevel(group as DiagnosticGroup, level)
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            // Diagnostic group doesn't exist, skip it
                        }
                        break
                    // Add more option mappings as needed
                }
            }
        }
    }
    
    private boolean setWarningLevelIfExistsInline(CompilerOptions options, String groupName, CheckLevel level) {
        return setWarningLevelIfExistsInlineStatic(options, groupName, level)
    }
    
    private static boolean setWarningLevelIfExistsInlineStatic(CompilerOptions options, String groupName, CheckLevel level) {
        try {
            java.lang.reflect.Field groupField = DiagnosticGroups.class.getField(groupName)
            Object group = groupField.get(null)
            options.setWarningLevel(group as DiagnosticGroup, level)
            return true
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Diagnostic group doesn't exist in this version of Closure Compiler, skip it
            System.err.println("DEBUG: Diagnostic group ${groupName} not found: ${e.message}")
            return false
        }
    }
}
