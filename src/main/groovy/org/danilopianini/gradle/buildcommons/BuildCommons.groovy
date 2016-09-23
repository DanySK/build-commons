package org.danilopianini.gradle.buildcommons

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.maven.MavenDeployment
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.jvm.tasks.Jar

class BuildCommons implements Plugin<Project> {
    void apply(Project project) {
        project.apply plugin: 'java'
        project.apply plugin: 'project-report'
        project.apply plugin: 'build-dashboard'
        project.apply plugin: "org.ajoberstar.grgit"
        project.repositories {
            mavenCentral()
        }
        def projectDir = project.projectDir
        project.ext {
            def vfile = new File("${projectDir}/version.info")
            try {
                def currentSha = grgit.head().id
                def branchMap = [:]
                if (grgit.branch.current.name.equals('HEAD')) {
                    /* 
                     * We are on a detached head. In this case, check if any of the branches
                     * heads have a matching hash, and in case pick the branch name.
                     * Otherwise, mark as detached.
                     */
                    def branches = grgit.branch.list()
                    branches.each {
                        try {
                            grgit.checkout(branch: it.name)
                            branchMap[grgit.head().id] = it.name
                        } catch (org.ajoberstar.grgit.exception.GrgitException e) {
                            println "Could not check out ${it.name}"
                        }
                    }
                    println "checking out ${currentSha}"
                    grgit.checkout(branch: currentSha)
                } else {
                    branchMap[currentSha] = grgit.branch.current.name
                }
                println "at ${grgit.head().id} on branch ${grgit.branch.current.name}"
                branch = branchMap.get(currentSha, 'detached')
                println "Current branch is $branch"
                if (!(branch.equals('master') || branch.contains('release'))) {
                    project.version = "${project.version}-${branch}-${grgit.head().abbreviatedId}".take(20)
                }
                vfile.text = project.version
            } catch (Exception ex) {
                ex.printStackTrace()
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
                    beforeDeployment { MavenDeployment deployment -> project.signing.signPom(deployment) }
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
    }
}
