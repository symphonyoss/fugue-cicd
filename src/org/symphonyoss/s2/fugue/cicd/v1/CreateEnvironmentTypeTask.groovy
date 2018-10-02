package org.symphonyoss.s2.fugue.cicd.v1

import java.util.Map.Entry

/**
 * Task to create an environment type, creating roles and other prerequisites if necessary.
 * 
 * @author Bruce Skingle
 *
 */
class CreateEnvironmentTypeTask extends FuguePipelineTask implements Serializable
{
  private String  logGroup_
  private String  environmentType_
  private String  dockerLabel_      = ':latest'
  private String  awsRegion_        = 'us-east-1'
  private String  cluster_
  private String  clusterArn_
  private String  configGitOrg_
  private String  configGitRepo_ 
  private String  configGitBranch_
  
//  public CreateEnvironmentTypeTask(FuguePipelineTask pipeLine, String environmentType)
//  {
//      super(pipeLine)
//    echo 'TA2a'
//      
//      environmentType_ = environmentType
//  }
  
  public CreateEnvironmentTypeTask(FuguePipeline pipeLine, String environmentType)
  {
      super(pipeLine)
      
      environmentType_ = environmentType
  }
  
  public CreateEnvironmentTypeTask withAwsRegion(String n)
  {
      awsRegion_ = n
      
      return this
  }
  
  public CreateEnvironmentTypeTask withDockerLabel(String s)
  {
    dockerLabel_ = s
    
    return this
  }
  
  public CreateEnvironmentTypeTask withConfigGitRepo(String org, String repo, String branch = 'master')
  {
    configGitOrg_ = org
    configGitRepo_ = repo
    configGitBranch_ = branch
    
    return this
  }

  public void execute()
  {
    String  accountId = 'fugue-' + environmentType_ + '-root'
    String  roleName  = accountId + '-role'

    echo """
------------------------------------------------------------------------------------------------------------------------
CreateEnvironmentTypeTask V2

awsRegion_          ${awsRegion_}
environmentType_    ${environmentType_}
accountId           ${accountId}
roleName            ${roleName}
------------------------------------------------------------------------------------------------------------------------
"""
    verifyUserAccess(accountId, environmentType_)
    
    withCredentials([[
      $class:             'AmazonWebServicesCredentialsBinding',
      accessKeyVariable:  'AWS_ACCESS_KEY_ID',
      credentialsId:      accountId,
      secretKeyVariable:  'AWS_SECRET_ACCESS_KEY']])
    {
      getOrCreateCluster()
      
      logGroup_ = pipeLine_.createLogGroup('fugue')
      pipeLine_.createRole(accountId, 'AmazonECSTaskExecutionRolePolicy', 'ecsTaskExecutionRole')
      pipeLine_.createRole(accountId, 'fugue-' + environmentType_ + "-root-policy", roleName)
      
      FugueDeploy deploy = new FugueDeploy(pipeLine_, 'CreateEnvironmentType',
        logGroup_,
        awsRegion_)
          .withConfigGitRepo(configGitOrg_, configGitRepo_, configGitBranch_)
          .withEnvironmentType(environmentType_)
          .withDockerLabel(dockerLabel_)
          .withRole(roleName)
          .withAccountId(accountId)
          .withCluster(cluster_)
        
      deploy.execute()
      
      def creds = readJSON(text:
        sh(returnStdout: true, script: 'aws secretsmanager get-secret-value --region us-east-1 --secret-id fugue-' + environmentType_ + '-root-cred'))
    
      def secrets = readJSON(text: creds."SecretString")
      
      secrets.each
      {
          name, value ->
            echo """
---------------------------------------------------
Store credential

name          ${name}
accessKeyId   ${value."accessKeyId"}
---------------------------------------------------
"""

            CredentialHelper.saveCredential(steps_, name, value."accessKeyId", value."secretAccessKey")
      }
    }
    
    echo """
------------------------------------------------------------------------------------------------------------------------
CreateEnvironmentTypeTask Finished
------------------------------------------------------------------------------------------------------------------------
"""
  }
  
  public void getOrCreateCluster()
  {
    if(cluster_ == null)
    {
      cluster_ = pipeLine_.getEnvironmentTypeConfig(environmentType_).getClusterId()
      //cluster_         = 'fugue-' + environmentType_
    }
    
    def clusters = readJSON(text:
      sh(returnStdout: true, script: 'aws ecs describe-clusters --region us-east-1 --clusters ' + cluster_ ))

    clusters."clusters".each
    {
      c ->
        if(cluster_.equals(c."clusterName"))
        {
          clusterArn_ = c."clusterArn"
          echo 'clusterArn_ is ' + clusterArn_
        }
    }
    
    if(clusterArn_ == null)
    {
      echo 'Cluster ' + cluster_ + ' does not exist, creating...'
      
      throw new IllegalStateException("Can't create EC2 cluster yet...")
      
      def createdCluster = readJSON(text:
        sh(returnStdout: true, script: 'aws ecs create-cluster --region us-east-1 --cluster-name ' + cluster_ ))
  
      clusterArn_ = createdCluster."cluster"."clusterArn"
      
      echo 'created clusterArn_ ' + clusterArn_
    }
  }
}
