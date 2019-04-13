package org.symphonyoss.s2.fugue.cicd.v3

import java.util.Map.Entry

/**
 * Class to execute FugeDeploy, creating roles and other pre-requisites if necessary.
 * 
 * @author Bruce Skingle
 *
 */
class FugueDeploy extends FuguePipelineTask implements Serializable
{
  private String  action_
  private String  awsRegion_
  private String  dockerLabel_  = ':' + FuguePipeline.FUGUE_VERSION


  private String  awsAccount_
  private String  cluster_
  private String  configGitOrg_
  private String  configGitRepo_
  private String  configGitBranch_

  private String  environmentType_
  private String  environment_
  private String  region_
  private String  servicename_
  private String  podName_
  private boolean primaryEnvironment_
  private boolean primaryRegion_
  private String  configTaskdef_
  private String  role_
  private String  executionRole_  = 'sym-s2-fugue-ecs-execution-role' //'ecsTaskExecutionRole'
  private String  memory_         = '1024'
  private String  cpu_            = '256'
  private String  port_           = '80'
  private String  consulTokenId_
  private String  accountId_
  private boolean fargateLaunch_
  private String  launchType_
  private String  releaseTrack_
  private String  station_
  private String  buildId_
  private String  logGroup_
  
  
  public FugueDeploy(FuguePipeline pipeLine, String task, String awsRegion)
  {
    super(pipeLine)
    
    action_           = task
    awsRegion_      = awsRegion
    
    
    fargateLaunch_     = false
    launchType_        = (fargateLaunch_ ? "FARGATE" : "EC2")
  }
  
  public FugueDeploy withCluster(String n)
  {
      cluster_ = n
      
      return this
  }
  
  public FugueDeploy withServiceName(String n)
  {
      servicename_ = n
      
      return this
  }
  
  public FugueDeploy withConfigGitRepo(String org, String repo, String branch = 'master')
  {
    configGitOrg_ = org
    configGitRepo_ = repo
    configGitBranch_ = branch
    
    return this
  }
  
  public FugueDeploy withBuildId(String s)
  {
    buildId_ = s
    
    return this
  }
  
  public FugueDeploy withLogGroup(String s)
  {
    logGroup_ = s
    
    return this
  }
  
  public FugueDeploy withDockerLabel(String s)
  {
    dockerLabel_ = s
    
    return this
  }
  
  public FugueDeploy withPodName(String s)
  {
    podName_ = s
    
    return this
  }
  
  public FugueDeploy withAccountId(String s)
  {
    accountId_ = s
    awsAccount_     = pipeLine_.aws_identity[accountId_].'Account';
    
    return this
  }
  
  public FugueDeploy withStation(Station s)
  {
    environmentType_    = s.environmentType
    environment_        = s.environment
    region_             = s.region
    primaryEnvironment_ = s.primaryEnvironment
    primaryRegion_      = s.primaryRegion
    role_               = 'sym-s2-' + environmentType_ + '-' + environment_ + '-admin-role'
    consulTokenId_      = 'sym-consul-' + environmentType_
    accountId_          = pipeLine_.globalNamePrefix_ + 'fugue-' + environmentType_ + '-cicd'
    awsAccount_         = pipeLine_.aws_identity[accountId_].'Account';
//    cluster_            = environmentType_ + '-' + environment_ + '-' + region_
    cluster_            = pipeLine_.getEnvironmentTypeConfig(environmentType_).getClusterId()
    station_            = s.name
    logGroup_           = pipeLine_.logGroupName(environmentType_, environment_, podName_, servicename_)
    
    
    return this
  }
  
  public FugueDeploy withTrack(String releaseTrack)
  {
    releaseTrack_       = releaseTrack
    
    return this
  }
  
  public FugueDeploy withEnvironmentType(String s)
  {
    environmentType_    = s
    
    return this
  }
  
  public FugueDeploy withEnvironment(String s)
  {
    environment_    = s
    
    return this
  }
  
  public FugueDeploy withRegion(String s)
  {
    region_    = s
    
    return this
  }

  public FugueDeploy withRole(String s)
  {
    role_    = s
    
    return this
  }
  
  public void pullImageFrom(String env)
  {
    String accountId          = pipeLine_.globalNamePrefix_ + 'fugue-' + env + '-cicd'
    
    pipeLine_.verifyUserAccess(accountId, env)
    
    String pullRepo           = pipeLine_.docker_repo[env]
    String awsAccount         = pipeLine_.aws_identity[accountId].'Account';
    
    String imageName          = '.dkr.ecr.us-east-1.amazonaws.com/' + pipeLine_.globalNamePrefix_ + 'fugue/fugue-deploy:' + FuguePipeline.FUGUE_VERSION
    String srcServiceImage    = awsAccount + imageName
    String tgtServiceImage    = awsAccount_ + imageName
    
    try
    {
        sh "aws --region ${awsRegion_} ecr describe-repositories --repository-names ${pipeLine_.globalNamePrefix_}fugue/fugue-deploy"
    }
    catch(Exception e)
    {
        echo 'Exception ' + e.toString()
        sh "aws --region ${awsRegion_} ecr create-repository --repository-name ${pipeLine_.globalNamePrefix_}fugue/fugue-deploy"
    }
    
    sh """
docker pull ${srcServiceImage}
docker tag ${srcServiceImage} ${tgtServiceImage}
docker push ${tgtServiceImage}
"""
  }

  public void execute()
  {
    String taskRoleArn      = 'arn:aws:iam::' + awsAccount_ + ':role/' + role_
    String executionRoleArn = 'arn:aws:iam::' + awsAccount_ + ':role/' + executionRole_
    String serviceImage     = awsAccount_ + '.dkr.ecr.us-east-1.amazonaws.com/' + pipeLine_.globalNamePrefix_ + 'fugue/fugue-deploy' + dockerLabel_
    String taskDefFamily    = pipeLine_.globalNamePrefix_ + 'fugue-deploy-' + environmentType_
    
    if(environment_ != null)
      taskDefFamily = taskDefFamily + '-' + environment_
      
    String consulToken
    String gitHubToken
    
    if(consulTokenId_ != null)
    {
      withCredentials([string(credentialsId: consulTokenId_, variable: 'CONSUL_TOKEN')])
      {
        consulToken = pipeLine_.env_.CONSUL_TOKEN.trim()
      }
    }
    
    withCredentials([string(credentialsId: 'symphonyjenkinsauto-token', variable: 'GITHUB_TOKEN')])
    {
      gitHubToken = pipeLine_.env_.GITHUB_TOKEN.trim()
    }
    
    echo """
------------------------------------------------------------------------------------------------------------------------
FugeDeploy V3 execute start

fargateLaunch   ${fargateLaunch_}
launchType      ${launchType_}

action          ${action_}
dryRun          ${pipeLine_.fugueDryRun_}
environmentType ${environmentType_}
environment     ${environment_}
region          ${region_}
podName         ${podName_}

serviceImage    ${serviceImage}
taskDefFamily   ${taskDefFamily}
logGroup        ${logGroup_}
------------------------------------------------------------------------------------------------------------------------
"""   
    String networkMode = fargateLaunch_ ? 'awsvpc' : 'bridge'
    
    configTaskdef_ =
    """{

    "executionRoleArn": "${executionRoleArn}",
    "taskRoleArn": "${taskRoleArn}",
    "family": "${taskDefFamily}",
    "networkMode": "${networkMode}", 
    "memory": "${memory_}",
    "cpu": "${cpu_}", 
    "requiresCompatibilities": [
        "${launchType_}"
    ], 
    "containerDefinitions": [
        {
            "name": "${taskDefFamily}",
            "image": "${serviceImage}",
            "essential": true,
            "portMappings": [
                {
                    "containerPort": ${port_},
                    "protocol": "tcp"
                }
            ],
            "logConfiguration": {
                "logDriver": "awslogs",
                "options": {
                    "awslogs-group": "${logGroup_}",
                    "awslogs-region": "${awsRegion_}",
                    "awslogs-stream-prefix": "fugue-deploy"
                }
            },
            "environment": [
                {
                    "name": "AWS_REGION",
                    "value": "${awsRegion_}"
                }"""
                    
    addIfNotNull("FUGUE_ENVIRONMENT_TYPE", environmentType_)
    addIfNotNull("FUGUE_ENVIRONMENT", environment_)
    addIfNotNull("FUGUE_REGION", region_)
    addIfNotNull("FUGUE_SERVICE", servicename_)
    addIfNotNull("FUGUE_ACTION", action_)
    addIfNotNull("FUGUE_DRY_RUN", pipeLine_.fugueDryRun_)
    addIfNotNull("FUGUE_POD_NAME", podName_)
    addIfNotNull("FUGUE_PRIMARY_ENVIRONMENT", primaryEnvironment_)
    addIfNotNull("FUGUE_PRIMARY_REGION", primaryRegion_)
    addIfNotNull("FUGUE_TRACK", releaseTrack_)
    addIfNotNull("FUGUE_STATION", station_)
    addIfNotNull("FUGUE_BUILD_ID", buildId_)
    
    if(environmentType_.equals('smoke'))
      addIfNotNull("CONSUL_URL", "https://consul-dev.symphony.com:8080")
//    else if(environmentType_.equals('stage'))
//      addIfNotNull("CONSUL_URL", "https://sym-ic-consul-stage-cmi-us-east-1.is.isym.io")
    else
      addIfNotNull("CONSUL_URL", "https://consul-" + environmentType_ + ".symphony.com:8080")
      
    addIfNotNull("CONSUL_TOKEN", consulToken)
    addIfNotNull("GITHUB_TOKEN", gitHubToken)
    addIfNotNull("GITHUB_ORG", configGitOrg_)
    addIfNotNull("GITHUB_REPO", configGitRepo_)
    addIfNotNull("GITHUB_BRANCH", configGitBranch_)
    
    configTaskdef_ = configTaskdef_ + '''
            ]
        }
    ]
}'''


    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
      credentialsId: accountId_,
      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']])
    {
      pipeLine_.createLogGroup(logGroup_)
      
      deleteOldTaskDefs(taskDefFamily)
      
      def taskdef_file = 'ecs-' + environment_ + '-' + podName_ + '.json'
      writeFile file:taskdef_file, text:configTaskdef_
      
      sh 'aws --region us-east-1 ecs register-task-definition --cli-input-json file://'+taskdef_file+' > ecs-taskdef-out-' + environment_ + '-' + podName_ + '.json'

      sh 'aws sts get-caller-identity'
      
      String runCommand = 'aws --region us-east-1 ecs run-task --cluster ' + cluster_ +
        ' --launch-type ' + launchType_
      
      if(fargateLaunch_)
      {
        runCommand = runCommand +
          ' --network-configuration "awsvpcConfiguration={subnets=' + pipeLine_.environmentTypeConfig[environmentType_].loadBalancerSubnets_ +
             ',securityGroups=' + pipeLine_.environmentTypeConfig[environmentType_].loadBalancerSecurityGroups_ +
             ',assignPublicIp=ENABLED}"'
      }
           
      runCommand = "${runCommand} --task-definition ${taskDefFamily} --count 1"
        
        
      def taskRun = readJSON(text:
        sh(returnStdout: true, script: runCommand
        )
      )
      echo "taskRun: ${taskRun}"
      echo """
Task run

taskArn: ${taskRun.tasks[0].taskArn}
lastStatus: ${taskRun.tasks[0].lastStatus}
"""
      String taskArn = taskRun.tasks[0].taskArn
      String taskId = taskArn.substring(taskArn.lastIndexOf('/')+1)
      String nextToken = ""
      int    limit=30
      while(limit-- > 0)
      {
        try
        {
          def logEvents = readJSON(text:
            sh(returnStdout: true, script: 'sh -x ; aws --region us-east-1 logs get-log-events --log-group-name ' + logGroup_ +
          ' --log-stream-name fugue-deploy/' + taskDefFamily + '/' + taskId + 
          nextToken
            )
          )
          
          nextToken = ' --next-token "' + logEvents."nextForwardToken" + '"'
          String l = ""
          
          for(def event : logEvents."events")
          {
            l = l + event."message" + '\n'
          }
          echo l
        }
        catch(Exception e)
        {
          echo 'No logs yet...'
        }
  
        def taskDescription = readJSON(text:
          sh(returnStdout: true, script: 'aws --region us-east-1 ecs describe-tasks  --cluster ' + 
            cluster_ +
            ' --tasks ' + taskArn
          )
        )
        
        if("STOPPED".equals(taskDescription.tasks[0].lastStatus))
        {
          echo """
  Task run
  taskArn: ${taskDescription.tasks[0].taskArn}
  lastStatus: ${taskDescription.tasks[0].lastStatus}
  stoppedReason: ${taskDescription.tasks[0].stoppedReason}
  exitCode: ${taskDescription.tasks[0].containers[0].exitCode}
  """
          if(taskDescription.tasks[0].containers[0].exitCode != 0)
          {
            echo """
  ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
  Failed Task Description
  ${taskDescription}
  ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
  """
            throw new IllegalStateException('Init task fugue-deploy failed with exit code ' + taskDescription.tasks[0].containers[0].exitCode)
          }
          
          return
        }
        sleep 10000
      }
      throw new IllegalStateException('Timed out waiting for task fugue-deploy')
    }
    
    
    echo """
------------------------------------------------------------------------------------------------------------------------
FugeDeploy execute finish
------------------------------------------------------------------------------------------------------------------------
"""
  }
  
  private void deleteOldTaskDefs(String taskDefFamily)
  {
    def versions = readJSON(text:
      sh(returnStdout: true, script: "aws ecs list-task-definitions --region us-east-1 --family-prefix ${taskDefFamily} --sort DESC --status ACTIVE" ))

    int cnt=3
    
    versions."taskDefinitionArns".each
    {
      v ->
        cnt--
        if(cnt<=0)
        {
          sh "aws ecs deregister-task-definition --region us-east-1 --task-definition ${v} > /dev/null"
        }
        else
        {
          echo "Retaining tashDef ${v}"
        }
    }
  }
  
  private void addIfNotNull(String name, Object value)
  {
    //echo 'name=' + name + ', value=' + value
    
    if(value != null)
    {
      configTaskdef_ = configTaskdef_ + ''',
                {
                    "name": "''' + name + '''",
                    "value": "''' + value + '''"
                }'''
    }
  }
}
