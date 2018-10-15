package org.danilopianini.gradle.buildcommons

import org.gradle.api.Project
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.maven.MavenDeployment
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.Sign

class BuildCommons implements Plugin<Project> {
    void apply(Project project) {
        project.apply plugin: 'java'
        project.apply plugin: 'project-report'
        project.apply plugin: 'build-dashboard'
        project.apply plugin: "org.ajoberstar.grgit"
        project.apply plugin: 'jacoco'
        project.apply plugin: 'maven' // For supporting install
        def isParent = project.parent == null
        project.repositories {
            mavenCentral()
        }
        def projectDir = project.projectDir
        project.ext {
            def vfile = new File("${projectDir}/version.info")
            try {
                def currentSha = grgit.head().id
                def branchMap = [:]
                /*
                 * Check if it is a tagged version
                 */
                def tags = grgit.tag.list()
                def tag = tags.find() { it.commit.id == currentSha }
                if (tag == null) {
                    project.version = "${project.version}-${grgit.head().abbreviatedId}".take(20)
                } else if (tag.name == project.version){
                    println "This is tagged as the official version ${project.version}"
                } else {
                    project.version = "${project.version}-${tag.name}-${grgit.head().abbreviatedId}".take(20)
                }
                println "Due to your git repo status, the project version is detected as ${project.version}"
                vfile.text = project.version
            } catch (Exception ex) {
                ex.printStackTrace()
                println("No Git repository info available, falling back to file")
                if (vfile.exists()) {
                    println("No version file, using project version variable as-is")
                    version = vfile.text
                }
            }
        }
        project.sourceCompatibility = project.jdkVersion
        project.targetCompatibility = project.jdkVersion
        project.compileJava.options.encoding = 'UTF-8'
        project.repositories { mavenCentral() }
        project.configurations {
            doc { transitive false }
            doclet
        }
        if (isParent) {
            project.wrapper {
                gradleVersion = project.gradleWrapperVersion
            }
        }
        // Tests
        project.test {
            testLogging {
                exceptionFormat = 'full'
            }
        }
        project.jacocoTestReport {
            reports {
                xml.enabled false
                csv.enabled false
                html.enabled true
            }
        }
        project.jacocoTestReport.dependsOn project.tasks.getByName('test')
        // Docs
        project.javadoc {
            destinationDir project.file("${project.buildDir}/docs/javadoc/")
            failOnError = false
            def longName = "${project.longName}"
            options {
                showAll()
                if (JavaVersion.current().isJava8Compatible()) {
                    addStringOption('Xdoclint:none', '-quiet')
                }
                windowTitle "${longName} version ${project.version} Javadoc API"
                docTitle "${longName} ${project.version} reference API"
                links 'http://docs.oracle.com/javase/8/docs/api/'
                links 'http://trove4j.sourceforge.net/javadocs/'
                doclet 'org.jboss.apiviz.APIviz'
                def home = "${System.properties['user.home']}/"
                def gradleCache = ".gradle/caches/modules-2/files-2.1/org.jboss.apiviz/"
                def searchFolder = "${home}${gradleCache}".replace('/',"${File.separator}")
                def fileList = []
                def apivizVersion = BuildCommons.getClassLoader().getResourceAsStream('apiviz.version').text
                def targetName = "apiviz-${apivizVersion}.jar".toString()
                new File(searchFolder).eachFileRecurse {
                    if(it.name.equals(targetName)) {
                        docletpath project.file(it.absolutePath)
                    }
                }
                addBooleanOption('nopackagediagram', true)
            }
        }
        // Artifacts
        project.compileJava.options.encoding = 'UTF-8'
        project.jar {
            manifest {
                attributes 'Implementation-Title': project.artifactId, 'Implementation-Version': project.version
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
        // Code quality
        project.apply plugin: 'findbugs'
        project.findbugs {
            if (JavaVersion.current().isJava6()) {
                toolVersion = "2.0.3"
            }
            ignoreFailures = true
            effort = "max"
            reportLevel = "low"
            def excludeFile = new File("${project.rootProject.projectDir}/findbugsExcludes.xml");
            if (excludeFile.exists()) {
                excludeFilterConfig = project.resources.text.fromFile(excludeFile)
            }
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
            toolVersion = project.checkstyleVersion
            configDir = project.rootDir
        }
        def xsl = BuildCommons.getClassLoader().getResourceAsStream('checkstyle-noframes-sorted.xsl').text
        project.checkstyleMain {
            doLast {
                ant.xslt(
                    in: reports.xml.destination,
                    out: new File(reports.xml.destination.parent, 'main.html')) {
                        style { string(value: xsl) }
                    }
            }
        }
        project.checkstyleTest {
            doLast {
                ant.xslt(
                    in: reports.xml.destination,
                    out: new File(reports.xml.destination.parent, 'main.html')) {
                        style { string(value: xsl) }
                    }
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
        // Maven Publishing
        project.apply plugin: 'maven-publish'
        project.publishing {
            publications {
                main(MavenPublication) {
                    from project.components.java
                    artifact project.sourcesJar
                    artifact project.javadocJar
                    pom {
                        name = project.artifactId
                        description = project.projectDescription
                        def ref = "${project.scmRootUrl}/${project.artifactId}".toString()
                        packaging = 'jar'
                        url = ref
                        licenses {
                            license {
                                name = project.licenseName
                                url = project.licenseUrl
                            }
                        }
                        scm {
                            url = ref
                            def scmRef = "${project.scmType}:${project.scmLogin}/${project.scmRepoName}".toString()
                            connection = scmRef
                            developerConnection = scmRef
                        }
                    }
                }
            }
            repositories {
                maven {
                    url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
                    credentials {
                        username = project.ossrhUsername
                        password = project.ossrhPassword
                    }
                }
            }
        }
        // Signing
        project.apply plugin: 'signing'
        project.signing {
            sign project.publishing.publications.main
        }
        project.tasks.withType(Sign) {
            onlyIf { Boolean.parseBoolean(project.signArchivesIsEnabled) }
        }
        // Default tasks
        makeDependency(project, 'buildDashboard', 'jacocoTestReport')
        makeDependency(project, 'buildDashboard', 'check')
        makeDependency(project, 'jacocoTestReport', 'check')
        project.defaultTasks 'clean', 'build', 'check', 'assemble', 'install', 'javadoc', 'buildDashboard'
    }
    
    void makeDependency(project, target, source) {
        project.tasks.getByName(target).dependsOn project.tasks.getByName(source)
    }
}
