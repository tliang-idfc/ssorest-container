#!groovy

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

node("docker_slave_ubuntu") {
    withEnv(['JFROG_CREDENTIALS=4f55ef52-fd7e-4377-b89f-6e015418268e']) {
        for(def osVersion in params.OS_VERSIONS.split(/,/)) {
            for(def nginxVersion in params.NGINX_VERSIONS.split(/,/)) {
                deploy(osVersion, nginxVersion)
            }
        }
     }
}

def deploy(String osVersion, String nginxVersion) {
    def buildInfo = [:]

    try  {
        print "Building for OS - ${osVersion}, nginx - ${nginxVersion}"

        stage("Preparation") {
            checkout([
                $class: 'GitSCM', 
                branches: [[name: '${GIT_TAG}']], 
                doGenerateSubmoduleConfigurations: false, 
                extensions: [[$class: 'CleanCheckout']], 
                submoduleCfg: [], 
                userRemoteConfigs: [[credentialsId: 'bitbucket-git', url: 'git@bitbucket.org:idfconnect/nginxssorestplugin.git']]
            ])

            buildInfo.modVer = String.format("ngx-ssorest-plugin-ngx%s-%s-%s", nginxVersion, osVersion, sh(script: "cat config | perl -pe 'if((\$v)=/([0-9]+([.][0-9]+)+)/){print\"\$v\n\";exit}\$_=\"\"'", returnStdout: true)).replaceAll(~/[\r\n]+/, '')
                
            sh "wget -qO - http://nginx.org/download/nginx-${nginxVersion}.tar.gz | tar zxfv - -C ${WORKSPACE}"
        }
        
        docker.withRegistry("https://idfconnect-docker.jfrog.io", JFROG_CREDENTIALS) {
            docker.image("${osVersion}-nginx-maven").inside {        
                stage("Build") {
                    sh "./build.sh ${nginxVersion} ${osVersion}"
                }
        
                stage("RPM") {
                    fileOperations([fileCreateOperation(fileContent: '''<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.idfconnect.nginx</groupId>
        <artifactId>ngx-ssorest-plugin</artifactId>
        <version>0.8.2</version>
        <name>Maven Recipe: RPM Package</name>

    <properties>        
        <nginx_version>na</nginx_version>
        <os_version>na</os_version>
    </properties>

        <build>
        <plugins>
                        <plugin>
                                <groupId>org.codehaus.mojo</groupId>
                                <artifactId>rpm-maven-plugin</artifactId>
                                <version>2.0.1</version>
                                <executions>
                                        <execution>
                                                <goals>
                                                        <goal>rpm</goal>
                                                </goals>
                                        </execution>
                                </executions>
                                <configuration> 
                                        <copyright>2019, IDF Connect, Inc. All Rights Reserved.</copyright>

                                        <group>Development</group>
                                        <description>Maven Recipe: RPM Package.</description>
                    <name>ngx-ssorest-plugin-ngx${nginx_version}-${os_version}</name>
                    <needarch>noarch</needarch>                 
                    <mappings>
                        <mapping>
                                    <directory>/usr/lib64/nginx/modules</directory>
                                    <sources>
                                        <source>
                                            <location>nginx-${nginx_version}/objs</location>
                                    <includes>
                                    <include>ngx_ssorest_plugin_module_ngx${nginx_version}_${os_version}.so</include>
                                    </includes>
                                        </source>
                                     </sources>
                                </mapping>
                    </mappings>
                    <defineStatements>  
                        <define>_binaries_in_noarch_packages_terminate_build 0</define>
                    </defineStatements>
                                </configuration>
                        </plugin>
        </plugins>
        </build>
</project>''', fileName: 'pom.xml')])
                    sh "./set_version.sh"
                    sh "mvn clean rpm:rpm -Dos_version=${osVersion} -Dnginx_version=${nginxVersion}"
                }
            }   
        }
   
        def server = Artifactory.server "idfconnect"
        server.credentialsId = JFROG_CREDENTIALS
        def artifactInfo
   
        stage("Deploy Artifactory") {
            artifactInfo = Artifactory.newBuildInfo()
            artifactInfo.name = "ngx-ssorest-plugin"
            artifactInfo.number = "${artifactInfo.number}-${osVersion}-${nginxVersion}"
        
            def uploadSpec = """{
                    "files": [{
                            "pattern": "**/*.rpm",
                            "target": "rpm/ngx-ssorest-plugin/${osVersion}/",
                "flat": true
                             }]
                        }"""
                        
            server.upload(spec: uploadSpec, buildInfo: artifactInfo)
            server.publishBuildInfo(artifactInfo)
            
            buildInfo.buildNumber = artifactInfo.number

            if (params.PUBLISH) {
                // Publish file to Bintray
                sleep(3)
                publishToBintray("ngx-ssorest-plugin", buildInfo.buildNumber, buildInfo.modVer, "rpm", "bintray-idfc")
            }
        }
    } catch (e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        //notifySlack(currentBuild.result, buildInfo, osVersion, nginxVersion)
    }
}

def notifySlack(String buildStatus = "STARTED", buildInfo, String osVersion, String nginxVersion) {
    // Build status of null means success.
    buildStatus = buildStatus ?: "SUCCESS"

    def color

    if (buildStatus == "STARTED") {
        color = "#D4DADF"
    } else if (buildStatus == "SUCCESS") {
        color = "#BDFFC3"
    } else if (buildStatus == 'UNSTABLE') {
        color = "#FFFE89"
    } else {
        color = "#FF9FA1"
    }

    def publishedStr = params.PUBLISH ? ", published" : ""
    def msg = String.format("`#${env.BUILD_NUMBER}` ${buildStatus}: `${env.JOB_NAME}` - ${buildInfo.modVer}${publishedStr}\n${env.BUILD_URL}")
    if(buildStatus == "SUCCESS" || buildStatus == "UNSTABLE") {
        msg += String.format("""
        ```YUM INSTALLATION GUIDE
sudo wget -O /etc/yum.repos.d/idfconnect.repo https://mars.idfconnect.com/rpm/idfconnect.repo --no-check-certificate

sudo yum update

sudo yum install ngx-ssorest-plugin-ngx${nginxVersion}-${osVersion}
```
           """)
    }
    
    JSONArray attachments = new JSONArray();
    JSONObject attachment = new JSONObject();
    
    attachment.put('text', msg);
    attachment.put('fallback', msg);
    attachment.put('color', color);
    
    if (! params.PUBLISH) {
        if(buildStatus == 'SUCCESS' || buildStatus == 'UNSTABLE') {
            JSONArray actions = new JSONArray();
            JSONObject action = new JSONObject();
            
            def url = String.format('https://mars.idfconnect.com/jenkins/job/idfconnect/job/publish_bintray/buildWithParameters?token=yujL2XOQxM&ARTIFACTORY_BUILD_NAME=%s&ARTIFACTORY_BUILD_NUMBER=%s&ARTIFACTORY_BUILD_VERSION=%s&SOURCE_REPOS=%s', "ngx-ssorest-plugin", buildInfo.buildNumber, java.net.URLEncoder.encode(buildInfo.modVer, "UTF-8"), "rpm")
            print url

            action.put('type', 'button');
            action.put('text', 'Distribute to Bintray');
            action.put('url', url);
            actions.add(action);
            attachment.put('actions', actions)
        }
    }
    
    attachments.add(attachment);
    slackSend(color: color, attachments: attachments.toString())
}

def publishToBintray(String buildName, String buildNumber, String buildVersion, String sourceRepos, String targetRepo) {
    def buildInfo = [:]

    def server = Artifactory.server "idfconnect"
    server.credentialsId = JFROG_CREDENTIALS
    buildInfo.buildName = buildName
    buildInfo.buildNumber = buildNumber
    buildInfo.buildVersion = buildVersion
    buildInfo.sourceRepos = sourceRepos.split(',')
    buildInfo.targetRepo = targetRepo
    
    echo String.format("Publishing #%s %s %s from %s to Bintray (%s)", buildInfo.buildNumber, buildInfo.buildName, buildInfo.buildVersion, buildInfo.sourceRepos.join("-"), buildInfo.targetRepo)
    
    def distributionConfig = [
            "buildName"             : buildInfo.buildName,
            "buildNumber"           : buildInfo.buildNumber,
            "targetRepo"            : buildInfo.targetRepo,
            "publish"               : true,
            "overrideExistingFiles" : true,
            "sourceRepos"           : buildInfo.sourceRepos,
            "dryRun"                : false
            ]
    
    server.distribute distributionConfig
}