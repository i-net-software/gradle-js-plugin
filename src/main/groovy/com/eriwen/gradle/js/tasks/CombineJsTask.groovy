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

import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.util.internal.PatternSetFactory
import org.gradle.internal.service.ServiceRegistry

class CombineJsTask extends SourceTask {
    private PatternSetFactory _patternSetFactory
    
    @Override
    protected PatternSetFactory getPatternSetFactory() {
        if (_patternSetFactory == null) {
            _patternSetFactory = project.services.get(PatternSetFactory.class)
        }
        return _patternSetFactory
    }
    
    @OutputFile def dest

    @Input encoding = System.properties['file.encoding']

    File getDest() {
        project.file(dest)
    }

    @TaskAction
    def run() { 
        final File destFile = getDest()
        destFile.parentFile.mkdirs()
        
        destFile.withWriter(encoding) { writer ->
            source.files.each { sourceFile ->
                logger.info("Adding to fileset: ${sourceFile}")
                sourceFile.withReader(encoding) { reader ->
                    writer << reader
                }
                // Add newline if file doesn't end with one (fixlastline: 'yes' equivalent)
                if (!sourceFile.text.endsWith('\n') && !sourceFile.text.endsWith('\r')) {
                    writer.write('\n')
                }
            }
        }
    }
}
