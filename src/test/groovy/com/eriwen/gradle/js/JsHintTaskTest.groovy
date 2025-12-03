package com.eriwen.gradle.js

import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class JsHintTaskTest extends Specification {
    @Rule TemporaryFolder dir = new TemporaryFolder()

    Project project = ProjectBuilder.builder().build()
    def task
    def src
    def dest

    def setup() {
        dir.create() // Ensure temporary folder is created
        project.apply(plugin: JsPlugin)
        project.repositories.mavenCentral()
        task = project.tasks.jshint
        src = dir.newFolder()
        dest = dir.newFile()
        task.source = src
        task.dest = dest
    }

    def "build ignores result by default"() {
        given:
        addValidFile()
        addInvalidFile()

        when:
        task.run()

        then:
        notThrown GradleException
    }

    def "build passes with only valid files"() {
        given:
        task.ignoreExitCode = false
        addValidFile()

        when:
        task.run()

        then:
        notThrown GradleException
    }

    def "build fails with invalid files"() {
        given:
        task.ignoreExitCode = false
        addValidFile()
        addInvalidFile()

        when:
        task.run()

        then:
        GradleException e = thrown()
    }

    def "build writes to stdout and accepts options"() {
        given:
        task.ignoreExitCode = false
        task.outputToStdOut = true
        project.jshint.options = [undef: "true", unused: "true"]

        addValidFile()

        when:
        task.run()

        then:
        notThrown GradleException
    }

    def "jshint processes many files"() {
        given:
        task.ignoreExitCode = false
        addFile("valid.js", "var a = 5;")
        addFile("valid2.js", "var b = 5;")
        addFile("valid3.js", "var c = 5;")
        addFile("valid4.js", "var d = 5;")

        when:
        task.run()

        then:
        notThrown GradleException
    }

    def "does not generate checkstyle report when disabled"() {
        given:
        task.reporter = ''
        addFile("invalid.js", "var b = 5")

        when:
        task.run()

        then:
        def contents = new File(dest as String).text
        assert ! (contents =~ "<checkstyle")
    }

    def "generates checkstyle report when enabled"() {
        given:
        task.reporter = 'checkstyle'
        addFile("invalid.js", "var b = 5")

        when:
        task.run()

        then:
        def contents = new File(dest as String).text
        assert contents =~ "<checkstyle"
    }

    def "fails with undefined variable"() {
        given:
        task.ignoreExitCode = false
        task.reporter = 'checkstyle'
        project.jshint.options = [undef: "true"]
        addFile("invalidWithUndef.js", "var b = someUndefinedVar;")

        when:
        task.run()

        then:
        GradleException e = thrown()
    }

    def "passes with defined variable"() {
        given:
        task.ignoreExitCode = false
        task.reporter = 'checkstyle'
        project.jshint.options = [undef: "true"]
        addFile("validWithDef.js", "var someVar = 5; var b = someVar;")

        when:
        task.run()

        then:
        notThrown GradleException
    }

    def "detects unused variables"() {
        given:
        task.ignoreExitCode = false
        project.jshint.options = [unused: "true"]
        addFile("unused.js", "var unusedVar = 5;")

        when:
        task.run()

        then:
        GradleException e = thrown()
    }

    def "detects strict mode violations"() {
        given:
        task.ignoreExitCode = false
        project.jshint.options = [strict: "true"]
        addFile("strict.js", "function test() { this.prop = 5; }")

        when:
        task.run()

        then:
        GradleException e = thrown()
    }

    def addValidFile() {
        addFile("valid.js", "var a = 5;")
    }

    def addInvalidFile() {
        // Missing semicolon and using undefined variable
        addFile("invalid.js", "var a = 5\nvar b = undefinedVar")
    }

    def addFile(name, contents) {
        def file = new File(src as String, name)
        file << contents
    }
}


