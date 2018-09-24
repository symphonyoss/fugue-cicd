package org.symphonyoss.s2.fugue.cicd.v1

class Container extends FuguePipelineTask implements Serializable {
  private String                name
  private Tenancy               tenancy         = Tenancy.SingleTenant
  private ContainerType         containerType   = ContainerType.Runtime
  private String                healthCheckPath = '/HealthCheck'
  private String                containerRole
  private String                ecstemplate
  private int                   port
  private String                containerPath
  private Map<String, String>   envOverride = [:]
  
  private Map tenants = [:]
  
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
        "\n      containerPath =" + containerPath +
        "\n      envOverride      =" + envOverride +
        "\n    }"
  }

  public Container withRole(String n) {
    containerRole = n
    return this
  }
  
  String getRole() {
    containerRole == null ? pipeLine_.servicename : containerRole
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
  
  public Container withEnv(String name, String value) {
    envOverride.put(name, value)
    return this
  }
  
  String getPath() {
    containerPath == null ? pipeLine_.servicePath : containerPath
  }
  
  void deployInit(Station tenantStage, String tenant) {
    
//    RunInitContainer deploy = new RunInitContainer(pipeLine_, tenantStage, tenant)
//      
//    deploy.execute()
//    
    echo 'T1 Init MULTI '
    registerTaskDef(tenantStage, tenant)
    
    echo 'T2 Init MULTI '
    runTask(tenantStage, tenant)
    echo 'T3 Init MULTI '
  }
  
  void runTask(Station tenantStage, String tenant)
  {
    String clusterId = pipeLine_.getEnvironmentTypeConfig(tenantStage.environmentType).getClusterId();
    
    echo 'runTask tenant=' + tenant + ", tenantStage=" + tenantStage.toString() + ', this=' + toString()
    echo 'envOverride=' + envOverride
    echo """
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
tenantStage.environment = ${tenantStage.environment}
tenant=${tenant}

tenants[tenantStage.environment][tenant]['ecs-taskdef-family']  = ${tenants[tenantStage.environment][tenant]['ecs-taskdef-family']}
tenants[tenantStage.environment][tenant]['ecs-taskdef-revision'] = ${tenants[tenantStage.environment][tenant]['ecs-taskdef-revision']}
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

"""
    if(tenants[tenantStage.environment][tenant]['ecs-taskdef-family'] != null &&
       tenants[tenantStage.environment][tenant]['ecs-taskdef-revision'] != null) {
        withCredentials([[
          $class: 'AmazonWebServicesCredentialsBinding',
          accessKeyVariable: 'AWS_ACCESS_KEY_ID',
          credentialsId: pipeLine_.getCredentialName(tenantStage.environmentType),
          secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
        
        String logGroupName = pipeLine_.createLogGroup(tenantStage, tenant);
                         
        String override = '{"containerOverrides": [{"name": "' + serviceFullName(tenantStage, tenant) +
         '","environment": ['
         String comma = ''
         
         echo 'envOverride=' + envOverride
         envOverride.each {
           name, value -> override = override + comma + '{"name": "' + name + '", "value": "' + value + '"}'
           comma = ','
           echo 'envOverride name=' + name + ', value=' + value
        }
        
        override = override + ']}]}'
 
        def taskRun = readJSON(text:
          sh(returnStdout: true, script: 'aws --region us-east-1 ecs run-task --cluster ' + 
            clusterId +
            ' --task-definition '+serviceFullName(tenantStage, tenant)+
            ' --count 1 --overrides \'' + override + '\''
          )
        )
        
        echo """
Task run
taskArn: ${taskRun.tasks[0].taskArn}
lastStatus: ${taskRun.tasks[0].lastStatus}
"""
        String taskArn = taskRun.tasks[0].taskArn
        String taskId = taskArn.substring(taskArn.lastIndexOf('/')+1)
        
        sh 'aws --region us-east-1 ecs wait tasks-stopped --cluster ' + clusterId +
        ' --tasks ' + taskArn
        

        sh 'aws --region us-east-1 ecs describe-tasks  --cluster ' + clusterId +
        ' --tasks ' + taskArn +
        ' > ' + name + '-taskDescription.json'
        
        def taskDescription = readJSON file:name + '-taskDescription.json'
        
        echo """
Task run
taskArn: ${taskDescription.tasks[0].taskArn}
lastStatus: ${taskDescription.tasks[0].lastStatus}
stoppedReason: ${taskDescription.tasks[0].stoppedReason}
exitCode: ${taskDescription.tasks[0].containers[0].exitCode}
"""
        sh 'aws --region us-east-1 logs get-log-events --log-group-name ' + logGroupName +
        ' --log-stream-name '+serviceFullName(tenantStage, tenant)+
        '/'+serviceFullName(tenantStage, tenant)+
        '/' + taskId + ' | fgrep "message" | sed -e \'s/ *"message": "//\' | sed -e \'s/",$//\' | sed -e \'s/\\\\t/      /\''

        
        if(taskDescription.tasks[0].containers[0].exitCode != 0) {
          
          
          throw new IllegalStateException('Init task ' + name + ' failed with exit code ' + taskDescription.tasks[0].containers[0].exitCode)
        }
      }
    }
  }
  
  void deployService(Station tenantStage, String tenant) {
    registerTaskDef(tenantStage, tenant)
    
//    def svcstate = getServiceState(tenantStage, tenant)
//    if(svcstate == null || svcstate.status == 'INACTIVE' || svcstate.status == 'DRAINING') {
//        createService(tenantStage, tenant)
//    } else {
//        updateService(tenantStage, tenant)
//    }
//    try {
//        waitServiceStable(tenantStage, tenant)
//    } catch(Exception e) {
//      echo 'Failed to start ' + e.toString()
//
//              updateServiceTaskDef(tenantStage, tenant, null, 0)
//        throw e
//    }
  }

  private def waitServiceStable(Station tenantStage, String tenant, int maxseconds = 300) {
    def svcstate = getServiceState(tenantStage, tenant)
    while(!(svcstate.runningCount > 0
            && svcstate.deployments.size() == 1
            && svcstate.runningCount == svcstate.desiredCount
            && svcstate.events.size() >= 1
            && svcstate.events[0].message.contains('reached a steady state'))
          && !(svcstate.runningCount > 0
               && svcstate.deployments.size() >= 1
               && svcstate.runningCount > svcstate.desiredCount
               && svcstate.events.size() >= 1
               && svcstate.events[0].message.contains('has begun draining connections'))) {
        echo """WAITING:
RUNNING:     ${svcstate.runningCount}
DEPLOYMENTS: ${svcstate.deployments.size()}
EXPECTED:    ${svcstate.runningCount} == ${svcstate.desiredCount}
STABLE:      ${svcstate.events.size()>=1?svcstate.events[0].message.contains('reached a steady state'):false}
DRAINING:    ${svcstate.events.size()>=1?svcstate.events[0].message.contains('has begun draining connections'):false}
"""
        maxseconds -= 15
        if(maxseconds<0) {
            if(!(svcstate.runningCount > 0)) {
                throw new Exception('Timeout Waiting for stable - running count')
            } else if(svcstate.deployments.size() != 1) {
                throw new Exception('Timeout Waiting for stable - deployments: '+svcstate.deployments.size())
            } else if (svcstate.runningCount < svcstate.desiredCount) {
                throw new Exception('Timeout Waiting for stable - desired count')
            } else if (svcstate.events.size()<1 || !svcstate.events[0].message.contains('reached a steady state')) {
                throw new Exception('Timeout Waiting for stable - steady state')
            } else {
                throw new Exception('Timeout Waiting for stable')
            }
        }
        //echo 'Sleeping 15 s'
        sleep 15000
        //sleep time:15 unit:steps.SECONDS
        svcstate = getServiceState(tenantStage, tenant)
    }
    return svcstate
}
  
  private void updateService(Station tenantStage, String tenant) {
    echo 'updateService'
      if(tenants[tenantStage.environment][tenant]['ecs-taskdef-family'] != null &&
         tenants[tenantStage.environment][tenant]['ecs-taskdef-revision'] != null) {
          updateServiceTaskDef(tenantStage, tenant,
                               tenants[tenantStage.environment][tenant]['ecs-taskdef-family']+':'+
                               tenants[tenantStage.environment][tenant]['ecs-taskdef-revision'],
                               1)
      }
  }
  
  private void updateServiceTaskDef(Station tenantStage, String tenant,
    String taskdef=null, int desired=-1)
  {
    String clusterId = pipeLine_.getEnvironmentTypeConfig(tenantStage.environmentType).getClusterId();
    
    echo 'updateServiceTaskDef'
    withCredentials([[
      $class: 'AmazonWebServicesCredentialsBinding',
      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
      credentialsId: pipeLine_.getCredentialName(tenantStage.environmentType),
      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']])
    {
      sh 'aws --region us-east-1 ecs update-service --cluster '+clusterId+' --service '+serviceFullName(tenantStage, tenant)+(taskdef!=null?(' --task-definition '+serviceFullName(tenantStage, tenant)):'')+(desired>=0?(' --desired-count '+desired):'')+' > ecs-service-update-'+tenantStage.environment+'-'+tenant+'.json'
    }
  }
  
  private void createService(Station tenantStage, String tenant, int desiredCount = 1)
  {
    String clusterId = pipeLine_.getEnvironmentTypeConfig(tenantStage.environmentType).getClusterId();
 
       echo 'createService'
      if(tenants[tenantStage.environment][tenant]['ecs-taskdef-family'] != null &&
         tenants[tenantStage.environment][tenant]['ecs-taskdef-revision'] != null) {
          withCredentials([[
            $class: 'AmazonWebServicesCredentialsBinding',
            accessKeyVariable: 'AWS_ACCESS_KEY_ID',
            credentialsId: pipeLine_.getCredentialName(tenantStage.environmentType),
            secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
            ]])
          {
              String logGroupName = pipeLine_.createLogGroup(tenantStage, tenant);
              
              
              //TODO: this needs to move to DeployConfig and we need to make it work with multiple containers, we
              // need to create a separate ELB for each container I think
//              sh 'aws --region us-east-1 elbv2 create-target-group --name '+serviceFullName(tenantStage, tenant)+' --health-check-path ' + healthCheckPath + ' --protocol HTTP --port '+port+' --vpc-id '+ECSClusterMaps.env_cluster[tenantStage.environment]['vpcid'] + ' | tee aws-ialb-tg-'+tenantStage.environment+'.json'
//              def ialb_tg = readJSON file:'aws-ialb-tg-'+tenantStage.environment+'.json'
//              
//              echo 'ialb_tg=' + ialb_tg
//              
//              def random_prio = ((new java.util.Random()).nextInt(50000)+1)
//              def LB_CONDITIONS='[{\\"Field\\":\\"host-header\\",\\"Values\\":[\\"'+tenant+'.'+ECSClusterMaps.env_cluster[tenantStage.environment]['dns_suffix']+'\\"]},{\\"Field\\":\\"path-pattern\\",\\"Values\\":[\\"'+path+'*\\"]}]'
//              sh 'aws --region us-east-1 elbv2 create-rule --listener-arn '+ECSClusterMaps.env_cluster[tenantStage.environment]['ialb_larn']+' --conditions \"'+LB_CONDITIONS+'\" --priority '+random_prio+' --actions \"Type=forward,TargetGroupArn='+ialb_tg.TargetGroups[0].TargetGroupArn+'\"'
//              // def lb_rule_sh = 'aws-ialb-rule-'+env+'.sh'
//              // writeFile file:lb_rule_sh, text:'aws --region us-east-1 elbv2 create-rule --listener-arn '+ECSClusterMaps.env_cluster[environment]['ialb_larn']+' --conditions "'+LB_CONDITIONS+'" --priority '+3273+' --actions "Type=forward,TargetGroupArn='+ialb_tg.TargetGroups[0].TargetGroupArn+'"'
//              // sh 'ls -alh'
//              // sh 'cat '+lb_rule_sh
//              // sh 'sh '+lb_rule_sh
              sh 'aws --region us-east-1 ecs create-service --cluster ' + clusterId +
                ' --service-name ' + serviceFullName(tenantStage, tenant) +
                ' --task-definition '+serviceFullName(tenantStage, tenant)+
                ' --desired-count ' + desiredCount
// put this back when loadbalance is fixed                + ' --role ecsServiceRole --load-balancers "targetGroupArn='+ialb_tg.TargetGroups[0].TargetGroupArn+',containerName='+serviceFullName(tenantStage, tenant)+',containerPort='+port+'"'

          }
      }
  }
  
  private def getServiceState(Station tenantStage, String tenant) {
    def retval
    try {
        withCredentials([[
          $class: 'AmazonWebServicesCredentialsBinding',
          accessKeyVariable: 'AWS_ACCESS_KEY_ID',
          credentialsId: pipeLine_.getCredentialName(tenantStage.environmentType),
          secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
          ]])
        {
            String clusterId = pipeLine_.getEnvironmentTypeConfig(tenantStage.environmentType).getClusterId();
    
            sh 'aws --region us-east-1 ecs describe-services --cluster '+clusterId+' --services '+serviceFullName(tenantStage, tenant)+' > ecs-service-'+tenantStage.environment+'-'+tenant+'.json'
            def svcstate = readJSON file: 'ecs-service-'+tenantStage.environment+'-'+tenant+'.json'
            echo """
ECS Service State:    ${svcstate.services[0].status}
ECS Service Running:  ${svcstate.services[0].runningCount}
ECS Service Pending:  ${svcstate.services[0].pendingCount}
ECS Service Desired:  ${svcstate.services[0].desiredCount}
ECS Service Event[0]: ${svcstate.services[0].events.size()>=1?svcstate.services[0].events[0].message:''}
ECS Service Event[1]: ${svcstate.services[0].events.size()>=2?svcstate.services[0].events[1].message:''}
ECS Service Event[2]: ${svcstate.services[0].events.size()>=3?svcstate.services[0].events[2].message:''}
"""
            retval = svcstate.services[0]
        }
    } catch(Exception e) {
        echo 'Failed Getting Service State: '+e
    }
    return retval
}
  
  String fugueConfig(Station tenantStage, String tenant)
  {
    echo """
pipeLine_.awsRegion = ${pipeLine_.awsRegion}
tenantStage.environmentType = ${tenantStage.environmentType}
tenantStage.environment =${tenantStage.environment}
tenantStage.realm = ${tenantStage.realm}
tenantStage.region =${tenantStage.region}
"""
    String prefix = 'https://s3.' + pipeLine_.awsRegion + '.amazonaws.com/fugue-' +
      tenantStage.environmentType + '-' + pipeLine_.awsRegion +
      '-config/config/' + tenantStage.environmentType + '-' + tenantStage.environment + '-' + tenantStage.realm + '-' + tenantStage.region;
    
    if(tenant != null)
      prefix = prefix  + '-' + tenant;
      
    return prefix + '-' + pipeLine_.servicename + ".json"
  }
  
  private void registerTaskDef(Station tenantStage, String tenant)
  {
    if(tenants[tenantStage.environment] == null) tenants[tenantStage.environment] = [:]
    if(tenants[tenantStage.environment][tenant] == null) tenants[tenantStage.environment][tenant] = [:]

    // Check if we were given a template file and allow it to override
    // the default template
    def taskdef_template
    if(ecstemplate != null && fileExists(ecstemplate)) {
        taskdef_template = readFile ecstemplate
    } else {
        taskdef_template = defaultTaskDefTemplate(port>0)
    }
    
    // dev-s2dev3-sym-ac8-s2fwd-init-role
    String baseRoleName = tenant == null ? 
      tenantStage.environmentType + '-' + tenantStage.environment + '-' + pipeLine_.servicename + '-' + containerRole :
      tenantStage.environmentType + '-' + tenantStage.environment + '-' + tenant + '-' + pipeLine_.servicename + '-' + containerRole
    
    String roleArn = 'arn:aws:iam::' + pipeLine_.aws_identity[pipeLine_.getCredentialName(tenantStage.environmentType)].Account + ':role/' + baseRoleName + '-role'
    
    def template_args = 
    [
      'ENV':'dev',
      'REGION':'ause1',
      'AWSREGION':pipeLine_.awsRegion,
      'FUGUE_ENVIRONMENT_TYPE':tenantStage.environmentType,
      'FUGUE_ENVIRONMENT':tenantStage.environment,
      'FUGUE_REALM':tenantStage.realm,
      'FUGUE_REGION':tenantStage.region,
      'FUGUE_CONFIG':fugueConfig(tenantStage, tenant),
      'FUGUE_SERVICE':pipeLine_.servicename,
      'FUGUE_PRIMARY_ENVIRONMENT':tenantStage.primaryEnvironment,
      'FUGUE_PRIMARY_REGION':tenantStage.primaryRegion,
      'TASK_ROLE_ARN':roleArn,
      'LOG_GROUP':'',
      'SERVICE_GROUP':pipeLine_.symteam,
      'SERVICE':serviceFullName(tenantStage, tenant),
      'SERVICE_PORT':port,
      'FUGUE_TENANT':tenant,
      'MEMORY':1024,
      'JVM_MEM':1024,
      'PERSIST_POD_ID':tenant+'-'+tenantStage.environment+'-glb-ause1-1',
      'LEGACY_POD_ID':tenant+'-'+tenantStage.environment+'-glb-ause1-1',
      'SERVICE_IMAGE':pipeLine_.docker_repo[tenantStage.environmentType]+name + pipeLine_.dockerLabel,
      'CONSUL_TOKEN':'',
      'GITHUB_TOKEN':''
    ]
    
    echo 'tenantStage.primaryEnvironment is ' + tenantStage.primaryEnvironment
    echo 'template_args is ' + template_args

    withCredentials([string(credentialsId: 'sym-consul-'+tenantStage.environmentType,
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
      credentialsId: pipeLine_.getCredentialName(tenantStage.environmentType),
      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']])
    {

      template_args['LOG_GROUP'] = pipeLine_.createLogGroup(tenantStage, tenant)
      
        //def taskdef = (new groovy.text.SimpleTemplateEngine()).createTemplate(taskdef_template).make(template_args).toString()
        def taskdef = (new org.apache.commons.lang3.text.StrSubstitutor(template_args)).replace(taskdef_template)
        //echo taskdef
        def taskdef_file = 'ecs-'+tenantStage.environment+'-'+tenant+'.json'
        writeFile file:taskdef_file, text:taskdef
        
//          echo 'taskdef file is ' + taskdef_file
          echo 'V1 taskdef is ' + taskdef
          echo 'V1 tenantStage.primaryEnvironment is ' + tenantStage.primaryEnvironment
          echo 'V1 template_args is ' + template_args
        
            sh 'aws --region us-east-1 ecs register-task-definition --cli-input-json file://'+taskdef_file+' > ecs-taskdef-out-'+tenantStage.environment+'-'+tenant+'.json'
            def tdefout = readJSON file: 'ecs-taskdef-out-'+tenantStage.environment+'-'+tenant+'.json'
            tenants[tenantStage.environment][tenant]['ecs-taskdef-arn'] = tdefout.taskDefinition.taskDefinitionArn
            tenants[tenantStage.environment][tenant]['ecs-taskdef-family'] = tdefout.taskDefinition.family
            tenants[tenantStage.environment][tenant]['ecs-taskdef-revision'] = tdefout.taskDefinition.revision
            echo """
ECS Task Definition ARN:      ${tenants[tenantStage.environment][tenant]['ecs-taskdef-arn']}
ECS Task Definition Family:   ${tenants[tenantStage.environment][tenant]['ecs-taskdef-family']}
ECS Task Definition Revision: ${tenants[tenantStage.environment][tenant]['ecs-taskdef-revision']}
"""
            
echo """
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
tenantStage.environment = ${tenantStage.environment}
tenant=${tenant}

tenants[tenantStage.environment][tenant]['ecs-taskdef-family']  = ${tenants[tenantStage.environment][tenant]['ecs-taskdef-family']}
tenants[tenantStage.environment][tenant]['ecs-taskdef-revision'] = ${tenants[tenantStage.environment][tenant]['ecs-taskdef-revision']}
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

"""
      
    }
  }
  
  private String serviceFullName(Station tenantStage, String tenant)
  {
    return tenantStage.environmentType + '-' + tenantStage.environment + '-' + tenantStage.realm + '-' + tenantStage.region + '-' +
      (tenant == null ? name : tenant + '-' + name)
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
                    "awslogs-stream-prefix": "${SERVICE}"
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
                    "name": "FUGUE_REALM",
                    "value": "${FUGUE_REALM}"
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
                    "value": "-Xms${JVM_MEM}m -Xmx${JVM_MEM}m -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=50 -XX:+ScavengeBeforeFullGC -XX:+CMSScavengeBeforeRemark -XX:+PrintGCDateStamps -verbose:gc -XX:+PrintGCDetails -Dcom.sun.management.jmxremote.port=10483 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
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
                    "awslogs-stream-prefix": "${SERVICE}"
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
                    "name": "FUGUE_REALM",
                    "value": "${FUGUE_REALM}"
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
                    "value": "-Xms${JVM_MEM}m -Xmx${JVM_MEM}m -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=50 -XX:+ScavengeBeforeFullGC -XX:+CMSScavengeBeforeRemark -XX:+PrintGCDateStamps -verbose:gc -XX:+PrintGCDetails -Dcom.sun.management.jmxremote.port=10483 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
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
