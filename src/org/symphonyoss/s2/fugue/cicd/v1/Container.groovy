package org.symphonyoss.s2.fugue.cicd.v1

class Container implements Serializable {
  private FuguePipeline  pipeLine
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

  public Container withPipeline(FuguePipeline p) {
    this.pipeLine = p

    return this
  }

  public Container withRole(String n) {
    containerRole = n
    return this
  }
  
  String getRole() {
    containerRole == null ? pipeLine.servicename : containerRole
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
    containerPath == null ? pipeLine.servicePath : containerPath
  }
  
  void deployInit(Station tenantStage, String tenant) {
    
    RunInitContainer deploy = new RunInitContainer(pipeLine, tenantStage, tenant)
      
    deploy.execute()
    
    
    registerTaskDef(tenantStage, tenant)
    
    runTask(tenantStage, tenant)
  }
  
  void runTask(Station tenantStage, String tenant) {
    pipeLine.steps.echo 'runTask tenant=' + tenant + ", tenantStage=" + tenantStage.toString() + ', this=' + toString()
    pipeLine.steps.echo 'envOverride=' + envOverride
    if(tenants[tenantStage.environment][tenant]['ecs-taskdef-family'] != null &&
       tenants[tenantStage.environment][tenant]['ecs-taskdef-revision'] != null) {
        pipeLine.steps.withCredentials([[
          $class: 'AmazonWebServicesCredentialsBinding',
          accessKeyVariable: 'AWS_ACCESS_KEY_ID',
          credentialsId: pipeLine.getCredentialName(tenantStage.environmentType),
          secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
        
        String logGroupName = pipeLine.createLogGroup(tenantStage, tenant);
                         
        String override = '{"containerOverrides": [{"name": "' + serviceFullName(tenantStage, tenant) +
         '","environment": ['
         String comma = ''
         
         pipeLine.steps.echo 'envOverride=' + envOverride
         envOverride.each {
           name, value -> override = override + comma + '{"name": "' + name + '", "value": "' + value + '"}'
           comma = ','
           pipeLine.steps.echo 'envOverride name=' + name + ', value=' + value
        }
        
        override = override + ']}]}'
 
        def taskRun = pipeLine.steps.readJSON(text:
          pipeLine.steps.sh(returnStdout: true, script: 'aws --region us-east-1 ecs run-task --cluster ' + 
            ECSClusterMaps.env_cluster[tenantStage.environment]['name'] +
            ' --task-definition '+serviceFullName(tenantStage, tenant)+
            ' --count 1 --overrides \'' + override + '\''
          )
        )
        
        pipeLine.steps.echo """
Task run
taskArn: ${taskRun.tasks[0].taskArn}
lastStatus: ${taskRun.tasks[0].lastStatus}
"""
        String taskArn = taskRun.tasks[0].taskArn
        String taskId = taskArn.substring(taskArn.lastIndexOf('/')+1)
        
        pipeLine.steps.sh 'aws --region us-east-1 ecs wait tasks-stopped --cluster ' + ECSClusterMaps.env_cluster[tenantStage.environment]['name'] +
        ' --tasks ' + taskArn
        

        pipeLine.steps.sh 'aws --region us-east-1 ecs describe-tasks  --cluster ' + ECSClusterMaps.env_cluster[tenantStage.environment]['name'] +
        ' --tasks ' + taskArn +
        ' > ' + name + '-taskDescription.json'
        
        def taskDescription = pipeLine.steps.readJSON file:name + '-taskDescription.json'
        
        pipeLine.steps.echo """
Task run
taskArn: ${taskDescription.tasks[0].taskArn}
lastStatus: ${taskDescription.tasks[0].lastStatus}
stoppedReason: ${taskDescription.tasks[0].stoppedReason}
exitCode: ${taskDescription.tasks[0].containers[0].exitCode}
"""
        if(taskDescription.tasks[0].containers[0].exitCode != 0) {
          
          pipeLine.steps.sh 'aws --region us-east-1 logs get-log-events --log-group-name ' + logGroupName +
          ' --log-stream-name '+serviceFullName(tenantStage, tenant)+
          '/'+serviceFullName(tenantStage, tenant)+
          '/' + taskId + ' | fgrep "message" | sed -e \'s/ *"message": "//\' | sed -e \'s/",$//\' | sed -e \'s/\\\\t/      /\''
          
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
//      pipeLine.steps.echo 'Failed to start ' + e.toString()
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
        pipeLine.steps.echo """WAITING:
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
        //pipeLine.steps.echo 'Sleeping 15 s'
        pipeLine.steps.sleep 15000
        //pipeLine.steps.sleep time:15 unit:steps.SECONDS
        svcstate = getServiceState(tenantStage, tenant)
    }
    return svcstate
}
  
  private void updateService(Station tenantStage, String tenant) {
    pipeLine.steps.echo 'updateService'
      if(tenants[tenantStage.environment][tenant]['ecs-taskdef-family'] != null &&
         tenants[tenantStage.environment][tenant]['ecs-taskdef-revision'] != null) {
          updateServiceTaskDef(tenantStage, tenant,
                               tenants[tenantStage.environment][tenant]['ecs-taskdef-family']+':'+
                               tenants[tenantStage.environment][tenant]['ecs-taskdef-revision'],
                               1)
      }
  }
  
  private void updateServiceTaskDef(Station tenantStage, String tenant,
    String taskdef=null, int desired=-1) {
    
    pipeLine.steps.echo 'updateServiceTaskDef'
    pipeLine.steps.withCredentials([[
      $class: 'AmazonWebServicesCredentialsBinding',
      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
      credentialsId: pipeLine.getCredentialName(tenantStage.environmentType),
      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']])
    {
      pipeLine.steps.sh 'aws --region us-east-1 ecs update-service --cluster '+ECSClusterMaps.env_cluster[tenantStage.environment]['name']+' --service '+serviceFullName(tenantStage, tenant)+(taskdef!=null?(' --task-definition '+serviceFullName(tenantStage, tenant)):'')+(desired>=0?(' --desired-count '+desired):'')+' > ecs-service-update-'+tenantStage.environment+'-'+tenant+'.json'
    }
  }
  
  private void createService(Station tenantStage, String tenant, int desiredCount = 1) {
    pipeLine.steps.echo 'createService'
      if(tenants[tenantStage.environment][tenant]['ecs-taskdef-family'] != null &&
         tenants[tenantStage.environment][tenant]['ecs-taskdef-revision'] != null) {
          pipeLine.steps.withCredentials([[
            $class: 'AmazonWebServicesCredentialsBinding',
            accessKeyVariable: 'AWS_ACCESS_KEY_ID',
            credentialsId: pipeLine.getCredentialName(tenantStage.environmentType),
            secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
            ]])
          {
              String logGroupName = pipeLine.createLogGroup(tenantStage, tenant);
              
              
              //TODO: this needs to move to DeployConfig and we need to make it work with multiple containers, we
              // need to create a separate ELB for each container I think
//              pipeLine.steps.sh 'aws --region us-east-1 elbv2 create-target-group --name '+serviceFullName(tenantStage, tenant)+' --health-check-path ' + healthCheckPath + ' --protocol HTTP --port '+port+' --vpc-id '+ECSClusterMaps.env_cluster[tenantStage.environment]['vpcid'] + ' | tee aws-ialb-tg-'+tenantStage.environment+'.json'
//              def ialb_tg = pipeLine.steps.readJSON file:'aws-ialb-tg-'+tenantStage.environment+'.json'
//              
//              pipeLine.steps.echo 'ialb_tg=' + ialb_tg
//              
//              def random_prio = ((new java.util.Random()).nextInt(50000)+1)
//              def LB_CONDITIONS='[{\\"Field\\":\\"host-header\\",\\"Values\\":[\\"'+tenant+'.'+ECSClusterMaps.env_cluster[tenantStage.environment]['dns_suffix']+'\\"]},{\\"Field\\":\\"path-pattern\\",\\"Values\\":[\\"'+path+'*\\"]}]'
//              pipeLine.steps.sh 'aws --region us-east-1 elbv2 create-rule --listener-arn '+ECSClusterMaps.env_cluster[tenantStage.environment]['ialb_larn']+' --conditions \"'+LB_CONDITIONS+'\" --priority '+random_prio+' --actions \"Type=forward,TargetGroupArn='+ialb_tg.TargetGroups[0].TargetGroupArn+'\"'
//              // def lb_rule_sh = 'aws-ialb-rule-'+env+'.sh'
//              // writeFile file:lb_rule_sh, text:'aws --region us-east-1 elbv2 create-rule --listener-arn '+ECSClusterMaps.env_cluster[environment]['ialb_larn']+' --conditions "'+LB_CONDITIONS+'" --priority '+3273+' --actions "Type=forward,TargetGroupArn='+ialb_tg.TargetGroups[0].TargetGroupArn+'"'
//              // sh 'ls -alh'
//              // sh 'cat '+lb_rule_sh
//              // sh 'sh '+lb_rule_sh
              pipeLine.steps.sh 'aws --region us-east-1 ecs create-service --cluster ' + ECSClusterMaps.env_cluster[tenantStage.environment]['name'] +
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
        pipeLine.steps.withCredentials([[
          $class: 'AmazonWebServicesCredentialsBinding',
          accessKeyVariable: 'AWS_ACCESS_KEY_ID',
          credentialsId: pipeLine.getCredentialName(tenantStage.environmentType),
          secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
          ]])
        {
            pipeLine.steps.sh 'aws --region us-east-1 ecs describe-services --cluster '+ECSClusterMaps.env_cluster[tenantStage.environment]['name']+' --services '+serviceFullName(tenantStage, tenant)+' > ecs-service-'+tenantStage.environment+'-'+tenant+'.json'
            def svcstate = pipeLine.steps.readJSON file: 'ecs-service-'+tenantStage.environment+'-'+tenant+'.json'
            pipeLine.steps.echo """
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
        pipeLine.steps.echo 'Failed Getting Service State: '+e
    }
    return retval
}
  
  String fugueConfig(Station tenantStage, String tenant)
  {
    pipeLine.steps.echo """
pipeLine.awsRegion = ${pipeLine.awsRegion}
tenantStage.environmentType = ${tenantStage.environmentType}
tenantStage.environment =${tenantStage.environment}
tenantStage.realm = ${tenantStage.realm}
tenantStage.region =${tenantStage.region}
"""
    String prefix = 'https://s3.' + pipeLine.awsRegion + '.amazonaws.com/fugue-' +
      tenantStage.environmentType + '-' + pipeLine.awsRegion +
      '-config/config/' + tenantStage.environmentType + '-' + tenantStage.environment + '-' + tenantStage.realm + '-' + tenantStage.region;
    
    if(tenant != null)
      prefix = prefix  + '-' + tenant;
      
    return prefix + '-' + pipeLine.servicename + ".json"
  }
  
  private void registerTaskDef(Station tenantStage, String tenant)
  {
    if(tenants[tenantStage.environment] == null) tenants[tenantStage.environment] = [:]
    if(tenants[tenantStage.environment][tenant] == null) tenants[tenantStage.environment][tenant] = [:]

    // Check if we were given a template file and allow it to override
    // the default template
    def taskdef_template
    if(ecstemplate != null && pipeLine.steps.fileExists(ecstemplate)) {
        taskdef_template = pipeLine.steps.readFile ecstemplate
    } else {
        taskdef_template = defaultTaskDefTemplate(port>0)
    }
    
    // dev-s2dev3-sym-ac8-s2fwd-init-role
    String baseRoleName = tenant == null ? 
      tenantStage.environmentType + '-' + tenantStage.environment + '-' + pipeLine.servicename + '-' + containerRole :
      tenantStage.environmentType + '-' + tenantStage.environment + '-' + tenant + '-' + pipeLine.servicename + '-' + containerRole
    
    String roleArn = 'arn:aws:iam::' + pipeLine.aws_identity[pipeLine.getCredentialName(tenantStage.environmentType)].Account + ':role/' + baseRoleName + '-role'
    
    def template_args = 
    [
      'ENV':'dev',
      'REGION':'ause1',
      'AWSREGION':pipeLine.awsRegion,
      'FUGUE_ENVIRONMENT_TYPE':tenantStage.environmentType,
      'FUGUE_ENVIRONMENT':tenantStage.environment,
      'FUGUE_REALM':tenantStage.realm,
      'FUGUE_REGION':tenantStage.region,
      'FUGUE_CONFIG':fugueConfig(tenantStage, tenant),
      'FUGUE_SERVICE':pipeLine.servicename,
      'FUGUE_PRIMARY_ENVIRONMENT':tenantStage.primaryEnvironment,
      'FUGUE_PRIMARY_REGION':tenantStage.primaryRegion,
      'TASK_ROLE_ARN':roleArn,
      'LOG_GROUP':'',
      'SERVICE_GROUP':pipeLine.symteam,
      'SERVICE':serviceFullName(tenantStage, tenant),
      'SERVICE_PORT':port,
      'FUGUE_TENANT':tenant,
      'MEMORY':1024,
      'JVM_MEM':1024,
      'PERSIST_POD_ID':tenant+'-'+tenantStage.environment+'-glb-ause1-1',
      'LEGACY_POD_ID':tenant+'-'+tenantStage.environment+'-glb-ause1-1',
      'SERVICE_IMAGE':pipeLine.docker_repo[tenantStage.environmentType]+name + pipeLine.dockerLabel,
      'CONSUL_TOKEN':'',
      'GITHUB_TOKEN':''
    ]
    
    pipeLine.steps.echo 'tenantStage.primaryEnvironment is ' + tenantStage.primaryEnvironment
    pipeLine.steps.echo 'template_args is ' + template_args

    pipeLine.steps.withCredentials([pipeLine.steps.string(credentialsId: 'sym-consul-'+tenantStage.environmentType,
    variable: 'CONSUL_TOKEN')])
    {
      // TODO: CONSUL_TOKEN needs to be moved
      template_args['CONSUL_TOKEN'] = pipeLine.steps.sh(returnStdout:true, script:'echo -n $CONSUL_TOKEN').trim()
    }
    
    pipeLine.steps.withCredentials([pipeLine.steps.string(credentialsId: 'symphonyjenkinsauto-token',
    variable: 'GITHUB_TOKEN')])
    {
      template_args['GITHUB_TOKEN'] = pipeLine.steps.sh(returnStdout:true, script:'echo -n $GITHUB_TOKEN').trim()
    }
    
    pipeLine.steps.withCredentials([[
      $class: 'AmazonWebServicesCredentialsBinding',
      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
      credentialsId: pipeLine.getCredentialName(tenantStage.environmentType),
      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']])
    {

      template_args['LOG_GROUP'] = pipeLine.createLogGroup(tenantStage, tenant)
      
        //def taskdef = (new groovy.text.SimpleTemplateEngine()).createTemplate(taskdef_template).make(template_args).toString()
        def taskdef = (new org.apache.commons.lang3.text.StrSubstitutor(template_args)).replace(taskdef_template)
        //pipeLine.steps.echo taskdef
        def taskdef_file = 'ecs-'+tenantStage.environment+'-'+tenant+'.json'
        pipeLine.steps.writeFile file:taskdef_file, text:taskdef
        
//          pipeLine.steps.echo 'taskdef file is ' + taskdef_file
          pipeLine.steps.echo 'V1 taskdef is ' + taskdef
          pipeLine.steps.echo 'V1 tenantStage.primaryEnvironment is ' + tenantStage.primaryEnvironment
          pipeLine.steps.echo 'V1 template_args is ' + template_args
        
            pipeLine.steps.sh 'aws --region us-east-1 ecs register-task-definition --cli-input-json file://'+taskdef_file+' > ecs-taskdef-out-'+tenantStage.environment+'-'+tenant+'.json'
            def tdefout = pipeLine.steps.readJSON file: 'ecs-taskdef-out-'+tenantStage.environment+'-'+tenant+'.json'
            tenants[tenantStage.environment][tenant]['ecs-taskdef-arn'] = tdefout.taskDefinition.taskDefinitionArn
            tenants[tenantStage.environment][tenant]['ecs-taskdef-family'] = tdefout.taskDefinition.family
            tenants[tenantStage.environment][tenant]['ecs-taskdef-revision'] = tdefout.taskDefinition.revision
            pipeLine.steps.echo """
ECS Task Definition ARN:      ${tenants[tenantStage.environment][tenant]['ecs-taskdef-arn']}
ECS Task Definition Family:   ${tenants[tenantStage.environment][tenant]['ecs-taskdef-family']}
ECS Task Definition Revision: ${tenants[tenantStage.environment][tenant]['ecs-taskdef-revision']}
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
