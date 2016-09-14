package org.danilopianini.gradle.buildcommons

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.maven.MavenDeployment
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.plugins.quality.Pmd;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.jvm.tasks.Jar;;;

class BuildCommons implements Plugin<Project> {
    void apply(Project project) {
        project.apply plugin: 'java'
        project.apply plugin: 'project-report'
        project.apply plugin: 'build-dashboard'
        project.repositories {
            mavenCentral()
        }
        def projectDir = project.projectDir
        project.ext {
            def vfile = new File("${projectDir}/version.info")
            try {
                git = org.ajoberstar.grgit.Grgit.open("${projectDir}")
                branch = git.branch.current.name
                if (!(branch.equals("${project.masterBranch}") || branch.contains("${project.releaseBranchPrefix}"))) {
                    project.version = "${project.version}-${branch}-${git.head().abbreviatedId}"
                }
                vfile.text = project.version
            } catch (Exception ex) {
                println("No Git repository info available, falling back to file")
                if (vfile.exists()) {
                    println("No version file, using project version variable as-is")
                    project.version = vfile.text
                }
            }
        }
        project.sourceCompatibility = project.jdkVersion
        project.targetCompatibility = project.jdkVersion
        project.repositories { mavenCentral() }
        project.configurations {
            doc { transitive false }
            doclet
        }
        project.dependencies {
            testCompile "junit:junit:${project.junitVersion}"
            doclet "org.jboss.apiviz:apiviz:${project.apivizVersion}"
        }
        project.task(type: Wrapper, 'wrapper') << {
            gradleVersion = project.gradleWrapperVersion
        }
        // Tests
        project.test {
            testLogging {
                exceptionFormat = 'full'
            }
        }
        // Artifacts
        project.compileJava.options.encoding = 'UTF-8'
        project.jar {
            manifest {
                attributes 'Implementation-Title': project.artifactId, 'Implementation-Version': project.version
            }
        }
        project.task(type: Javadoc, overwrite: true, 'javadoc') {
            source project.configurations.doc.collect { zipTree(it) }
            def mainSourceSets = project.sourceSets.main
            source mainSourceSets.allJava
            classpath = mainSourceSets.output + mainSourceSets.compileClasspath
            include '**/*.java'
            destinationDir project.file("${project.buildDir}/docs/javadoc/")
            failOnError = false
            def longName = "${project.longName}"
            options {
                showAll()
                addBooleanOption('nopackagediagram', true)
                addStringOption('Xdoclint:none', '-quiet')
                windowTitle "${longName} version ${project.version} Javadoc API"
                docTitle "${longName} ${project.version} reference API"
                links 'http://docs.oracle.com/javase/8/docs/api/'
                links 'http://trove4j.sourceforge.net/javadocs/'
                doclet 'org.jboss.apiviz.APIviz'
                docletpath project.file(project.configurations.doclet.asPath)
            }
        }
        project.task(type: Jar, dependsOn: project.classes, 'sourcesJar') {
            classifier = 'sources'
            from project.sourceSets.main.allSource
        }
        project.task(type: Jar, dependsOn: project.javadoc, 'javadocJar') {
            classifier = 'javadoc'
            from project.javadoc.destinationDir
        }
        project.artifacts {
            archives project.sourcesJar
            archives project.javadocJar
        }
        project.task(type: Jar, 'fatJar') {
            baseName = project.name + '-complete'
            from(project.configurations.compile.collect { it.isDirectory() ? it : zipTree(it) }) {
                exclude "META-INF/*.SF"
                exclude "META-INF/*.DSA"
                exclude "META-INF/*.RSA"
            }
            with project.jar
        }
        // Code quality
        project.apply plugin: 'findbugs'
        project.findbugs {
            ignoreFailures = true
            effort = "max"
            reportLevel = "low"
        }
        project.tasks.withType(FindBugs) {
            reports {
                xml.enabled = false
                html.enabled = true
            }
        }
        project.apply plugin: 'pmd'
        project.dependencies {
            def pmdVersion = project.pmdVersion
            pmd(
                "net.sourceforge.pmd:pmd-core:$pmdVersion",
                "net.sourceforge.pmd:pmd-vm:$pmdVersion",
                "net.sourceforge.pmd:pmd-plsql:$pmdVersion",
                "net.sourceforge.pmd:pmd-jsp:$pmdVersion",
                "net.sourceforge.pmd:pmd-xml:$pmdVersion",
                "net.sourceforge.pmd:pmd-java:$pmdVersion"
            )
        }
        project.pmd {
            ignoreFailures = true
            ruleSets = []
            ruleSetFiles = project.files("${project.rootProject.projectDir}/${project.pmdConfigFile}")
            targetJdk = project.pmdTargetJdk
            toolVersion = project.pmdVersion
        }
        project.tasks.withType(Pmd) {
            reports {
                xml.enabled = false
                html.enabled = true
            }
        }
        project.apply plugin: 'checkstyle'
        project.checkstyle {
            ignoreFailures = true
            configFile = new File("${project.rootProject.projectDir}/${project.checkstyleConfigFile}")
        }
        def xsl = BuildCommons.getClassLoader().getResourceAsStream('checkstyle-noframes-sorted.xsl').text
        project.checkstyleMain << {
            ant.xslt(in: reports.xml.destination,
            out: new File(reports.xml.destination.parent, 'main.html')) {
                style { string(value: xsl) }
            }
        }
        project.checkstyleTest << {
            ant.xslt(in: reports.xml.destination,
            out: new File(reports.xml.destination.parent, 'main.html')) {
                style { string(value: xsl) }
            }
        }
        // Eclipse
        project.apply plugin: 'eclipse'
        project.eclipse {
            classpath {
                downloadJavadoc = true
                downloadSources = true
            }
        }
        // Signing
        project.apply plugin: 'signing'
        project.signing {
            sign project.configurations.archives
        }
        project.signArchives.onlyIf { Boolean.parseBoolean(project.signArchivesIsEnabled) }
        // Maven
        project.apply plugin: 'maven'
        project.uploadArchives {
            repositories {
                mavenDeployer {
                    def user = project.ossrhUsername
                    def pwd = project.ossrhPassword
                    beforeDeployment { MavenDeployment deployment -> signing.signPom(deployment) }
                    repository(url: "https://oss.sonatype.org/service/local/staging/deploy/maven2/") {
                        authentication(userName: user, password: pwd)
                    }
                    snapshotRepository(url: "https://oss.sonatype.org/content/repositories/snapshots/") {
                        authentication(userName: user, password: pwd)
                    }
                    pom.project {
                        name project.artifactId
                        description project.projectDescription
                        def ref = "${project.scmRootUrl}/${project.artifactId}"
                        packaging 'jar'
                        url ref
                        licenses {
                            license {
                                name project.licenseName
                                url project.licenseUrl
                            }
                        }
                        scm {
                            url ref
                            def scmRef = "${project.scmType}:${project.scmLogin}/${project.scmRepoName}"
                            connection scmRef
                            developerConnection scmRef
                        }
                    }
                }
            }
        }
        // Default tasks
        project.defaultTasks 'wrapper', 'clean', 'build', 'check', 'assemble', 'install', 'javadoc', 'buildDashboard', 'fatJar'
    }
}
