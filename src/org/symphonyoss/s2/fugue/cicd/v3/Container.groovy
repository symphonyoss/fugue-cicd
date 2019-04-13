package org.symphonyoss.s2.fugue.cicd.v3

class Container extends FuguePipelineTask implements Serializable {
  private String                name
  private Tenancy               tenancy         = Tenancy.SINGLE
  private ContainerType         containerType   = ContainerType.SERVICE
  private String                healthCheckPath = '/HealthCheck'
  private String                containerRole
  private String                ecstemplate
  private int                   port
  private int                   memory_ = 1024
  private int                   jvmHeap_ = 512
  private String                containerPath
  private Map<String, String>   envOverride = [:]
  private Map                   pods = [:]
  
  public Container(FuguePipeline pipeLine)
  {
    super(pipeLine)
  }

  public String toString() {
    "    {" +
        "\n      name             =" + name +
        "\n      tenancy          =" + tenancy +
        "\n      containerType    =" + containerType +
        "\n      healthCheckPath  =" + healthCheckPath +
        "\n      containerRole =" + containerRole +
        "\n      ecstemplate      =" + ecstemplate +
        "\n      port             =" + port +
        "\n      memory           =" + memory_ +
        "\n      jvmHeap          =" + jvmHeap_ +
        "\n      containerPath    =" + containerPath +
        "\n      envOverride      =" + envOverride +
        "\n    }"
  }

  public Container withRole(String n) {
    containerRole = n
    return this
  }
  
  String getRole() {
    containerRole == null ? pipeLine_.serviceId_ : containerRole
  }

  public Container withName(String n) {
    name = n
    return this
  }

  public Container withHealthCheckPath(String n) {
    healthCheckPath = n
    return this
  }

  public Container withTenancy(Tenancy t) {
    tenancy = t
    return this
  }

  public Container withContainerType(ContainerType t) {
    containerType = t
    return this
  }
  
  public Container withEcsTemplate(String t) {
      this.ecstemplate = t
      return this
  }
  
  public Container withPort(int p) {
      port = p
      return this
  }
  
  public Container withMemory(int p) {
      memory_ = p
      return this
  }
  
  public Container withJvmHeap(int p) {
      jvmHeap_ = p
      return this
  }
  
  public Container withEnv(String name, String value) {
    envOverride.put(name, value)
    return this
  }
  
  String getPath() {
    containerPath == null ? pipeLine_.servicePath : containerPath
  }
  
  void deployInit(Station station, String podName)
  {
    registerTaskDef(station, podName)
    runTask(station, podName)
  }
  
  void runTask(Station station, String podName)
  {
    String clusterId = pipeLine_.getEnvironmentTypeConfig(station.environmentType).getClusterId();
    
    echo 'runTask podName=' + podName + ", station=" + station.toString() + ', this=' + toString()
    echo 'envOverride=' + envOverride
    echo """
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
station.environment = ${station.environment}
podName=${podName}

pods[station.environment][podName]['ecs-taskdef-family']  = ${pods[station.environment][podName]['ecs-taskdef-family']}
pods[station.environment][podName]['ecs-taskdef-revision'] = ${pods[station.environment][podName]['ecs-taskdef-revision']}
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

"""
//    if(pods[station.environment][podName]['ecs-taskdef-family'] != null &&
//       pods[station.environment][podName]['ecs-taskdef-revision'] != null) {
//        withCredentials([[
//          $class: 'AmazonWebServicesCredentialsBinding',
//          accessKeyVariable: 'AWS_ACCESS_KEY_ID',
//          credentialsId: pipeLine_.getCredentialName(station.environmentType),
//          secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
//        
//        String logGroupName = pipeLine_.createLogGroup(station, podName);
//                         
//        String override = '{"containerOverrides": [{"name": "' + serviceFullName(station, podName) +
//         '","environment": ['
//         String comma = ''
//         
//         echo 'envOverride=' + envOverride
//         envOverride.each {
//           name, value -> override = override + comma + '{"name": "' + name + '", "value": "' + value + '"}'
//           comma = ','
//           echo 'envOverride name=' + name + ', value=' + value
//        }
//        
//        override = override + comma + '{"name": "FUGUE_DRY_RUN", "value": "' + pipeLine_.fugueDryRun_ + '"}'
//        comma = ','
//        override = override + comma + '{"name": "FUGUE_CREATE", "value": "' + pipeLine_.fugueCreate_ + '"}'
//        override = override + comma + '{"name": "FUGUE_DELETE", "value": "' + pipeLine_.fugueDelete_ + '"}'
//        override = override + comma + '{"name": "FUGUE_DELETE_POD", "value": "' + pipeLine_.fugueDeletePod_ + '"}'
//        
//        override = override + ']}]}'
// 
//        def taskRun = readJSON(text:
//          sh(returnStdout: true, script: 'aws --region us-east-1 ecs run-task --cluster ' + 
//            clusterId +
//            ' --task-definition '+serviceFullName(station, podName)+
//            ' --count 1 --overrides \'' + override + '\''
//          )
//        )
//        
//        echo """
//Task run
//taskArn: ${taskRun.tasks[0].taskArn}
//lastStatus: ${taskRun.tasks[0].lastStatus}
//"""
//        String taskArn = taskRun.tasks[0].taskArn
//        String taskId = taskArn.substring(taskArn.lastIndexOf('/')+1)
//        
//        sh 'aws --region us-east-1 ecs wait tasks-stopped --cluster ' + clusterId +
//        ' --tasks ' + taskArn
//        
//
//        sh 'aws --region us-east-1 ecs describe-tasks  --cluster ' + clusterId +
//        ' --tasks ' + taskArn +
//        ' > ' + name + '-taskDescription.json'
//        
//        def taskDescription = readJSON file:name + '-taskDescription.json'
//        
//        echo """
//Task run
//taskArn: ${taskDescription.tasks[0].taskArn}
//lastStatus: ${taskDescription.tasks[0].lastStatus}
//stoppedReason: ${taskDescription.tasks[0].stoppedReason}
//exitCode: ${taskDescription.tasks[0].containers[0].exitCode}
//"""
//        sh 'aws --region us-east-1 logs get-log-events --log-group-name ' + logGroupName +
//        ' --log-stream-name '+serviceFullName(station, podName)+
//        '/'+serviceFullName(station, podName)+
//        '/' + taskId + ' | fgrep "message" | sed -e \'s/ *"message": "//\' | sed -e \'s/",$//\' | sed -e \'s/\\\\t/      /\''
//
//        
//        if(taskDescription.tasks[0].containers[0].exitCode != 0) {
//          
//          
//          throw new IllegalStateException('Init task ' + name + ' failed with exit code ' + taskDescription.tasks[0].containers[0].exitCode)
//        }
//      }
//    }
  }
  
  private void updateService(Station station, String podName) {
    echo 'updateService'
      if(pods[station.environment][podName]['ecs-taskdef-family'] != null &&
         pods[station.environment][podName]['ecs-taskdef-revision'] != null) {
          updateServiceTaskDef(station, podName,
                               pods[station.environment][podName]['ecs-taskdef-family']+':'+
                               pods[station.environment][podName]['ecs-taskdef-revision'],
                               1)
      }
  }
  
  private void updateServiceTaskDef(Station station, String podName,
    String taskdef=null, int desired=-1)
  {
    String clusterId = pipeLine_.getEnvironmentTypeConfig(station.environmentType).getClusterId();
    
    echo 'updateServiceTaskDef'
    withCredentials([[
      $class: 'AmazonWebServicesCredentialsBinding',
      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
      credentialsId: pipeLine_.getCredentialName(station.environmentType),
      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']])
    {
      sh 'aws --region us-east-1 ecs update-service --cluster '+clusterId+' --service '+serviceFullName(station, podName)+(taskdef!=null?(' --task-definition '+serviceFullName(station, podName)):'')+(desired>=0?(' --desired-count '+desired):'')+' > ecs-service-update-'+station.environment+'-'+podName+'.json'
    }
  }
  
  private void createService(Station station, String podName, int desiredCount = 1)
  {
    String clusterId = pipeLine_.getEnvironmentTypeConfig(station.environmentType).getClusterId();
 
       echo 'createService'
      if(pods[station.environment][podName]['ecs-taskdef-family'] != null &&
         pods[station.environment][podName]['ecs-taskdef-revision'] != null) {
          withCredentials([[
            $class: 'AmazonWebServicesCredentialsBinding',
            accessKeyVariable: 'AWS_ACCESS_KEY_ID',
            credentialsId: pipeLine_.getCredentialName(station.environmentType),
            secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
            ]])
          {
              String logGroupName = pipeLine_.createLogGroup(station, podName);
              
              
              //TODO: this needs to move to DeployConfig and we need to make it work with multiple containers, we
              // need to create a separate ELB for each container I think
//              sh 'aws --region us-east-1 elbv2 create-target-group --name '+serviceFullName(station, podName)+' --health-check-path ' + healthCheckPath + ' --protocol HTTP --port '+port+' --vpc-id '+ECSClusterMaps.env_cluster[station.environment]['vpcid'] + ' | tee aws-ialb-tg-'+station.environment+'.json'
//              def ialb_tg = readJSON file:'aws-ialb-tg-'+station.environment+'.json'
//              
//              echo 'ialb_tg=' + ialb_tg
//              
//              def random_prio = ((new java.util.Random()).nextInt(50000)+1)
//              def LB_CONDITIONS='[{\\"Field\\":\\"host-header\\",\\"Values\\":[\\"'+podName+'.'+ECSClusterMaps.env_cluster[station.environment]['dns_suffix']+'\\"]},{\\"Field\\":\\"path-pattern\\",\\"Values\\":[\\"'+path+'*\\"]}]'
//              sh 'aws --region us-east-1 elbv2 create-rule --listener-arn '+ECSClusterMaps.env_cluster[station.environment]['ialb_larn']+' --conditions \"'+LB_CONDITIONS+'\" --priority '+random_prio+' --actions \"Type=forward,TargetGroupArn='+ialb_tg.TargetGroups[0].TargetGroupArn+'\"'
//              // def lb_rule_sh = 'aws-ialb-rule-'+env+'.sh'
//              // writeFile file:lb_rule_sh, text:'aws --region us-east-1 elbv2 create-rule --listener-arn '+ECSClusterMaps.env_cluster[environment]['ialb_larn']+' --conditions "'+LB_CONDITIONS+'" --priority '+3273+' --actions "Type=forward,TargetGroupArn='+ialb_tg.TargetGroups[0].TargetGroupArn+'"'
//              // sh 'ls -alh'
//              // sh 'cat '+lb_rule_sh
//              // sh 'sh '+lb_rule_sh
              sh 'aws --region us-east-1 ecs create-service --cluster ' + clusterId +
                ' --service-name ' + serviceFullName(station, podName) +
                ' --task-definition '+serviceFullName(station, podName)+
                ' --desired-count ' + desiredCount
// put this back when loadbalance is fixed                + ' --role ecsServiceRole --load-balancers "targetGroupArn='+ialb_tg.TargetGroups[0].TargetGroupArn+',containerName='+serviceFullName(station, podName)+',containerPort='+port+'"'

          }
      }
  }
  
//  private def getServiceState(Station station, String podName) {
//    def retval
//    try {
//        withCredentials([[
//          $class: 'AmazonWebServicesCredentialsBinding',
//          accessKeyVariable: 'AWS_ACCESS_KEY_ID',
//          credentialsId: pipeLine_.getCredentialName(station.environmentType),
//          secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
//          ]])
//        {
//            String clusterId = pipeLine_.getEnvironmentTypeConfig(station.environmentType).getClusterId();
//    
//            sh 'aws --region us-east-1 ecs describe-services --cluster '+clusterId+' --services '+serviceFullName(station, podName)+' > ecs-service-'+station.environment+'-'+podName+'.json'
//            def svcstate = readJSON file: 'ecs-service-'+station.environment+'-'+podName+'.json'
//            echo """
//ECS Service State:    ${svcstate.services[0].status}
//ECS Service Running:  ${svcstate.services[0].runningCount}
//ECS Service Pending:  ${svcstate.services[0].pendingCount}
//ECS Service Desired:  ${svcstate.services[0].desiredCount}
//ECS Service Event[0]: ${svcstate.services[0].events.size()>=1?svcstate.services[0].events[0].message:''}
//ECS Service Event[1]: ${svcstate.services[0].events.size()>=2?svcstate.services[0].events[1].message:''}
//ECS Service Event[2]: ${svcstate.services[0].events.size()>=3?svcstate.services[0].events[2].message:''}
//"""
//            retval = svcstate.services[0]
//        }
//    } catch(Exception e) {
//        echo 'Failed Getting Service State: '+e
//    }
//    return retval
//}
  
  String fugueConfig(Station station, String podName)
  {
    echo """
pipeLine_.awsRegion = ${pipeLine_.awsRegion}
station.environmentType = ${station.environmentType}
station.environment =${station.environment}
station.region =${station.region}
"""
    String prefix = 'https://s3.' + pipeLine_.awsRegion + '.amazonaws.com/sym-s2-fugue-' +
      station.environmentType + '-' + pipeLine_.awsRegion +
      '-config/config/sym-s2-' + station.environmentType + '-' + station.environment;
    
    if(podName != null)
      prefix = prefix  + '-' + podName;
      
    return prefix + '-' + pipeLine_.serviceId_ + ".json"
  }
  
  private void registerTaskDef(Station station, String podName)
  {
    if(pods[station.environment] == null) pods[station.environment] = [:]
    if(pods[station.environment][podName] == null) pods[station.environment][podName] = [:]

    // Check if we were given a template file and allow it to override
    // the default template
    def taskdef_template
    if(ecstemplate != null && fileExists(ecstemplate)) {
        taskdef_template = readFile ecstemplate
    } else {
        taskdef_template = defaultTaskDefTemplate(port>0)
    }
    
    // dev-s2dev3-sym-ac8-s2fwd-init-role
    String baseRoleName = podName == null ? 
      'sym-s2-' + station.environmentType + '-' + station.environment + '-' + pipeLine_.serviceId_ + '-' + containerRole :
      'sym-s2-' + station.environmentType + '-' + station.environment + '-' + podName + '-' + pipeLine_.serviceId_ + '-' + containerRole
    
    String roleArn = 'arn:aws:iam::' + pipeLine_.aws_identity[pipeLine_.getCredentialName(station.environmentType)].Account + ':role/' + baseRoleName + '-role'
    
    def template_args = 
    [
      'ENV':'dev',
      'REGION':'ause1',
      'AWSREGION':pipeLine_.awsRegion,
      'FUGUE_ENVIRONMENT_TYPE':station.environmentType,
      'FUGUE_ENVIRONMENT':station.environment,
      'FUGUE_REGION':station.region,
      'FUGUE_CONFIG':fugueConfig(station, podName),
      'FUGUE_SERVICE':pipeLine_.serviceId_,
      'FUGUE_PRIMARY_ENVIRONMENT':station.primaryEnvironment,
      'FUGUE_PRIMARY_REGION':station.primaryRegion,
      'TASK_ROLE_ARN':roleArn,
      'LOG_GROUP':'',
      'SERVICE_ID':pipeLine_.serviceId_,
      'SERVICE':serviceFullName(station, podName),
      'SERVICE_PORT':port,
      'FUGUE_POD_NAME':podName,
      'MEMORY':memory_,
      'JVM_HEAP':jvmHeap_,
//      'PERSIST_POD_ID':podName+'-'+station.environment+'-glb-ause1-1',
//      'LEGACY_POD_ID':podName+'-'+station.environment+'-glb-ause1-1',
      'SERVICE_IMAGE':pipeLine_.docker_repo[station.environmentType]+name + pipeLine_.dockerLabel,
      'CONSUL_TOKEN':'',
      'GITHUB_TOKEN':''
    ]
    
    echo 'station.primaryEnvironment is ' + station.primaryEnvironment
    echo 'template_args is ' + template_args

    withCredentials([string(credentialsId: 'sym-consul-'+station.environmentType,
    variable: 'CONSUL_TOKEN')])
    {
      // TODO: CONSUL_TOKEN needs to be moved
      template_args['CONSUL_TOKEN'] = sh(returnStdout:true, script:'echo -n $CONSUL_TOKEN').trim()
    }
    
    withCredentials([string(credentialsId: 'symphonyjenkinsauto-token',
    variable: 'GITHUB_TOKEN')])
    {
      template_args['GITHUB_TOKEN'] = sh(returnStdout:true, script:'echo -n $GITHUB_TOKEN').trim()
    }
    
    withCredentials([[
      $class: 'AmazonWebServicesCredentialsBinding',
      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
      credentialsId: pipeLine_.getCredentialName(station.environmentType),
      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']])
    {

      template_args['LOG_GROUP'] = pipeLine_.createLogGroup(station, podName)
      
        //def taskdef = (new groovy.text.SimpleTemplateEngine()).createTemplate(taskdef_template).make(template_args).toString()
        def taskdef = (new org.apache.commons.lang3.text.StrSubstitutor(template_args)).replace(taskdef_template)
        //echo taskdef
        def taskdef_file = 'ecs-'+station.environment+'-'+podName+'.json'
        writeFile file:taskdef_file, text:taskdef
        
//          echo 'taskdef file is ' + taskdef_file
          echo 'V1 taskdef is ' + taskdef
          echo 'V1 station.primaryEnvironment is ' + station.primaryEnvironment
          echo 'V1 template_args is ' + template_args
        
            sh 'aws --region us-east-1 ecs register-task-definition --cli-input-json file://'+taskdef_file+' > ecs-taskdef-out-'+station.environment+'-'+podName+'.json'
            def tdefout = readJSON file: 'ecs-taskdef-out-'+station.environment+'-'+podName+'.json'
            pods[station.environment][podName]['ecs-taskdef-arn'] = tdefout.taskDefinition.taskDefinitionArn
            pods[station.environment][podName]['ecs-taskdef-family'] = tdefout.taskDefinition.family
            pods[station.environment][podName]['ecs-taskdef-revision'] = tdefout.taskDefinition.revision
            echo """
ECS Task Definition ARN:      ${pods[station.environment][podName]['ecs-taskdef-arn']}
ECS Task Definition Family:   ${pods[station.environment][podName]['ecs-taskdef-family']}
ECS Task Definition Revision: ${pods[station.environment][podName]['ecs-taskdef-revision']}
"""
            
echo """
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
station.environment = ${station.environment}
podName=${podName}

pods[station.environment][podName]['ecs-taskdef-family']  = ${pods[station.environment][podName]['ecs-taskdef-family']}
pods[station.environment][podName]['ecs-taskdef-revision'] = ${pods[station.environment][podName]['ecs-taskdef-revision']}
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

"""
      
    }
  }
  
  private String serviceFullName(Station station, String podName)
  {
    String podNameIdStr = (podName == null) ? "" : "-${podName}"
    
    return "sym-s2-${station.environmentType}-${station.environment}${podNameIdStr}-${pipeLine_.serviceId_}-${name}"
//      (podName == null ? pipeLine_.serviceId_ + '-' + name : podName + '-' + pipeLine_.serviceId_ + '-' + name)
  }

  

    /////////////////////////////////////////////
    // Taskdef Templates
    private static final String taskdef_template_https =
        '''{
    "taskRoleArn": "${TASK_ROLE_ARN}",
    "family": "${SERVICE}",
    "containerDefinitions": [
        {
            "name": "${SERVICE}",
            "image": "${SERVICE_IMAGE}",
            "memory": ${MEMORY},
            "portMappings": [
                {
                    "hostPort": 0,
                    "protocol": "tcp",
                    "containerPort": ${SERVICE_PORT}
                }
            ],
            "essential": true,
            "logConfiguration": {
                "logDriver": "awslogs",
                "options": {
                    "awslogs-group": "${LOG_GROUP}",
                    "awslogs-region": "${AWSREGION}",
                    "awslogs-stream-prefix": "${SERVICE_ID}"
                }
            },
            "environment": [
                {
                    "name": "FUGUE_ENVIRONMENT_TYPE",
                    "value": "${FUGUE_ENVIRONMENT_TYPE}"
                },
                {
                    "name": "FUGUE_ENVIRONMENT",
                    "value": "${FUGUE_ENVIRONMENT}"
                },
                {
                    "name": "FUGUE_REGION",
                    "value": "${FUGUE_REGION}"
                },
                {
                    "name": "FUGUE_CONFIG",
                    "value": "${FUGUE_CONFIG}"
                },
                {
                    "name": "FUGUE_SERVICE",
                    "value": "${FUGUE_SERVICE}"
                },
                {
                    "name": "FUGUE_TENANT",
                    "value": "${FUGUE_TENANT}"
                },
                {
                    "name": "FUGUE_PRIMARY_ENVIRONMENT",
                    "value": "${FUGUE_PRIMARY_ENVIRONMENT}"
                },
                {
                    "name": "FUGUE_PRIMARY_REGION",
                    "value": "${FUGUE_PRIMARY_REGION}"
                },
                {
                    "name": "FUGUE_JAVA_ARGS",
                    "value": "-Xms${JVM_HEAP}m -Xmx${JVM_HEAP}m"
                },
                
                
                
                {
                    "name": "CONSUL_URL",
                    "value": "https://consul-${ENV}.symphony.com:8080"
                },
                {
                    "name": "CONSUL_TOKEN",
                    "value": "${CONSUL_TOKEN}"
                },
                {
                    "name": "GITHUB_TOKEN",
                    "value": "${GITHUB_TOKEN}"
                },
                
                
                
                {
                    "name": "AWS_REGION",
                    "value": "${AWSREGION}"
                },
                {
                    "name": "INFRA_NAME",
                    "value": "${PERSIST_POD_ID}"
                },
                {
                    "name": "POD_NAME",
                    "value": "${LEGACY_POD_ID}"
                },
                {
                    "name": "SYM_ENV",
                    "value": "${ENV}"
                },
                {
                    "name": "SYM_ES_JAVA_ARGS",
                    "value": "-Xms${JVM_HEAP}m -Xmx${JVM_HEAP}m -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=50 -XX:+ScavengeBeforeFullGC -XX:+CMSScavengeBeforeRemark -XX:+PrintGCDateStamps -verbose:gc -XX:+PrintGCDetails -Dcom.sun.management.jmxremote.port=10483 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
                }
            ]
        }
    ]
}'''
    private static final String taskdef_template_nohttps =
        '''{
    "taskRoleArn": "${TASK_ROLE_ARN}",
    "family": "${SERVICE}",
    "containerDefinitions": [
        {
            "name": "${SERVICE}",
            "image": "${SERVICE_IMAGE}",
            "memory": ${MEMORY},
            "portMappings": [
                {
                    "hostPort": 0,
                    "protocol": "tcp",
                    "containerPort": ${SERVICE_PORT}
                }
            ],
            "essential": true,
            "logConfiguration": {
                "logDriver": "awslogs",
                "options": {
                    "awslogs-group": "${LOG_GROUP}",
                    "awslogs-region": "${AWSREGION}",
                    "awslogs-stream-prefix": "${SERVICE_ID}"
                }
            },
            "environment": [
                {
                    "name": "FUGUE_ENVIRONMENT_TYPE",
                    "value": "${FUGUE_ENVIRONMENT_TYPE}"
                },
                {
                    "name": "FUGUE_ENVIRONMENT",
                    "value": "${FUGUE_ENVIRONMENT}"
                },
                {
                    "name": "FUGUE_REGION",
                    "value": "${FUGUE_REGION}"
                },
                {
                    "name": "FUGUE_CONFIG",
                    "value": "${FUGUE_CONFIG}"
                },
                {
                    "name": "FUGUE_SERVICE",
                    "value": "${FUGUE_SERVICE}"
                },
                {
                    "name": "FUGUE_TENANT",
                    "value": "${FUGUE_TENANT}"
                },
                {
                    "name": "FUGUE_PRIMARY_ENVIRONMENT",
                    "value": "${FUGUE_PRIMARY_ENVIRONMENT}"
                },
                {
                    "name": "FUGUE_PRIMARY_REGION",
                    "value": "${FUGUE_PRIMARY_REGION}"
                },
                {
                    "name": "FUGUE_JAVA_ARGS",
                    "value": "-Xms${JVM_HEAP}m -Xmx${JVM_HEAP}m"
                },
                
                
                {
                    "name": "CONSUL_URL",
                    "value": "https://consul-${ENV}.symphony.com:8080"
                },
                {
                    "name": "CONSUL_TOKEN",
                    "value": "${CONSUL_TOKEN}"
                },
                {
                    "name": "GITHUB_TOKEN",
                    "value": "${GITHUB_TOKEN}"
                },
                
                
                
                {
                    "name": "AWS_REGION",
                    "value": "${AWSREGION}"
                },
                {
                    "name": "INFRA_NAME",
                    "value": "${PERSIST_POD_ID}"
                },
                {
                    "name": "POD_NAME",
                    "value": "${LEGACY_POD_ID}"
                },
                {
                    "name": "SYM_ENV",
                    "value": "${ENV}"
                },
                {
                    "name": "SYM_ES_JAVA_ARGS",
                    "value": "-Xms${JVM_HEAP}m -Xmx${JVM_HEAP}m -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=50 -XX:+ScavengeBeforeFullGC -XX:+CMSScavengeBeforeRemark -XX:+PrintGCDateStamps -verbose:gc -XX:+PrintGCDetails -Dcom.sun.management.jmxremote.port=10483 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
                }
            ]
        }
    ]
}'''
    private final String defaultTaskDefTemplate(boolean exposeHTTPS)
    {
        if(exposeHTTPS) {
            return taskdef_template_https
        } else {
            return taskdef_template_nohttps
        }
    }
}
