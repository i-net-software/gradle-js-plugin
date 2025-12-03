/**
 * Copyright 2011-2012 Eric Wendelin
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

import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.internal.PatternSetFactory
import java.util.zip.GZIPOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class GzipJsTask extends SourceTask {
    private PatternSetFactory _patternSetFactory
    
    @Override
    protected PatternSetFactory getPatternSetFactory() {
        if (_patternSetFactory == null) {
            _patternSetFactory = project.services.get(PatternSetFactory.class)
        }
        return _patternSetFactory
    }
    @OutputFile def dest

    File getDest() {
        project.file(dest)
    }

    @TaskAction
    def run() {
        final File srcFile = source.singleFile
        final File destFile = getDest()
        final File tempGzFile = new File(srcFile.parentFile, "${srcFile.name}.gz")
        
        // Gzip the source file
        srcFile.withInputStream { inputStream ->
            tempGzFile.withOutputStream { outputStream ->
                GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)
                try {
                    byte[] buffer = new byte[8192]
                    int len
                    while ((len = inputStream.read(buffer)) != -1) {
                        gzipOutputStream.write(buffer, 0, len)
                    }
                } finally {
                    gzipOutputStream.close()
                }
            }
        }
        
        // Move the gzipped file to destination
        destFile.parentFile.mkdirs()
        Files.move(tempGzFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
