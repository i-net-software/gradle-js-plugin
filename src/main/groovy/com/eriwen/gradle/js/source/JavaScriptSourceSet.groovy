package com.eriwen.gradle.js.source

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet

interface JavaScriptSourceSet extends Named {

    SourceDirectorySet getJs()

    SourceDirectorySet js(Action<SourceDirectorySet> action)

    JavaScriptProcessingChain getProcessing()

    JavaScriptProcessingChain processing(Action<JavaScriptProcessingChain> action)
    
    FileCollection getProcessed()
    
    JavaScriptSourceSet configure(Closure closure)
}
