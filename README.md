# build-commons

This plugin pre-configures Gradle for providing a rich java build. It enables a number of other plugins, including Eclipse, PMD, Checkstyle, Findbugs, BuildDashboard. Also, it provides a signature service, a wrapper task, pom generation and upload on OSSRH

## Usage

```groovy
apply plugin: 'org.danilopianini.build-commons'

buildscript {
    repositories {
        maven { url "https://plugins.gradle.org/m2/" }
    }
    dependencies {
        classpath "org.danilopianini:build-commons:0.1.6"
    }
}
```

## Configuration

The following Gradle properties are required for this plugin in order to work. You can specify them in your project's `gradle.properties` file, your `~/.gradle/gradle.properties`, or wherever else you prefer, as far as Gradle associates them to your project before applying the plugin:
* `groupId` your artifact's group id 
* `artifactId` your artifact id
* `longName` the full name of your project
* `projectDescription` A short description in natural language of what your project is
* `version` the version you are working on. Please note that, if you are using git flow or a similar tool, non master and non release branches will get their version annotated with the current branch and commit hash
* `licenseName` the license you are using
* `licenseUrl` the URL at which the license is available for reading
* `releaseBranchPrefix` the prefix of your release branch. Usually `release`
* `masterBranch` the name of your main branch. Usually `master`
* `scmType` the scm you are using. Usually `scm:git`
* `scmRootUrl` the root of the url where your repo is stored, e.g. `https://github.com/DanySK`
* `scmLogin` the part of the URL that gets appended to clone commands, for instance `git@github.com:DanySK`
* `scmRepoName` the repository name, e.g. `build-commons.git`
* `gradleWrapperVersion` the version of Gradle to be used for the wrapper
* `jdkVersion` the target version of the JDK
* `junitVersion` the JUnit version
* `checkstyleVersion` the Checkstyle version
* `pmdVersion` the PMD version
* `pmdConfigFile` the path of the file where your pmd configuration is stored, relative to the project
* `pmdTargetJdk` the PMD target JDK. Usually `1.7`
* `apivizVersion` The APIViz version to use, usually `1.3.2.GA`
* `checkstyleConfigFile` the path of the checkstyle rule file, relative to the project
* `signArchivesIsEnabled` if `true`, the plugin will attempt at signing the artifacts. In this case, also the `signing.keyId`, `signing.password`, and `signing.secretKeyRingFile` must be set (as per signing Gradle plugin setting). Also, you must have properly configured GPG
* `ossrhUsername` username for OSSRH. Do not write it in clear text
* `ossrhPassword` your OSSRH password. Again, no clear text here
