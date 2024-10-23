package io.orkes.conductor.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.plugins.signing.SigningPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.authentication.aws.AwsImAuthentication


class PublishConfigPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.withType(MavenPublishPlugin) {
            publishingConfig(project)
        }
        project.plugins.withType(SigningPlugin) {
            signingConfig(project)
        }
    }

    def publishingConfig(Project project) {
        project.publishing {
            publications(publicationConfig(project))
            repositories(repositoriesConfig(project))
        }
    }

    def signingConfig(Project project) {
        project.signing {
            def signingKeyId = project.findProperty('signingKeyId')
            if (signingKeyId) {
                def signingKey = project.findProperty('signingKey')
                def signingPassword = project.findProperty('signingPassword')
                if (signingKeyId && signingKey && signingPassword) {
                    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
                }
                sign project.publishing.publications
            }
        }
    }

    def publicationConfig(Project project) {
        return {
            mavenJava(MavenPublication) {
                if (project.hasProperty('artifactId')) {
                    artifactId = project.findProperty('artifactId')
                }

                from project.components.java
                pom {
                    name = project.findProperty('artifactName')
                    description = project.findProperty('artifactDescription')
                    url = 'https://github.com/conductor-oss/conductor.git'
                    scm {
                        connection = 'scm:git:git://github.com/conductor-oss/conductor.git'
                        developerConnection = 'scm:git:ssh://github.com/conductor-oss/conductor.git'
                        url = 'https://github.com/conductor-oss/conductor.git'
                    }
                    licenses {
                        license {
                            name = 'The Apache License, Version 2.0'
                            url = 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                        }
                    }
                    developers {
                        developer {
                            organization = 'Orkes'
                            organizationUrl = 'https://orkes.io'
                            name = 'Orkes Development Team'
                            email = 'developers@orkes.io'
                        }
                    }
                }
            }
        }
    }

    def repositoriesConfig(Project project) {
        return {
            maven {
                if (project.hasProperty("mavenCentral")) {
                    url = getMavenRepoUrl(project)
                    credentials {
                        username = project.properties['username']
                        password = project.properties['password']
                    }
                } else {
                    url = getS3BucketUrl(project)
                    authentication {
                        awsIm(AwsImAuthentication)
                    }
                }
            }
        }
    }

    static String getS3BucketUrl(Project project) {
        return "s3://orkes-artifacts-repo/${project.version.endsWith('-SNAPSHOT') ? 'snapshots' : 'releases'}"
    }

    static String getMavenRepoUrl(Project project) {
        return "https://s01.oss.sonatype.org/${project.version.endsWith('-SNAPSHOT') ? 'content/repositories/snapshots/' : 'service/local/staging/deploy/maven2/'}"
    }
}
