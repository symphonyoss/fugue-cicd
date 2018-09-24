package org.symphonyoss.s2.fugue.cicd.v1

import java.util.Map.Entry

/**
 * Task to create an environment type, creating roles and other prerequisites if necessary.
 * 
 * @author Bruce Skingle
 *
 */
class CreateEnvironmentTask extends FuguePipelineTask implements Serializable
{
  private String  logGroup_
  private String  environmentType_
  private String  environment_
  private String  realm_
  private String  region_
  private String  dockerLabel_      = ':latest'
  private String  awsRegion_        = 'us-east-1'
  private String  cluster_
  private String  clusterArn_
  private String  configGitOrg_
  private String  configGitRepo_
  private String  configGitBranch_

  public CreateEnvironmentTask(FuguePipeline pipeLine, String environmentType, String environment,
    String realm, String region)
  {
      super(pipeLine)
      
      environmentType_ = environmentType
      environment_     = environment
      realm_           = realm
      region_          = region
  }
  
  public CreateEnvironmentTask withAwsRegion(String n)
  {
      awsRegion_ = n
      
      return this
  }
  
  public CreateEnvironmentTask withDockerLabel(String s)
  {
    dockerLabel_ = s
    
    return this
  }
  
  public CreateEnvironmentTask withConfigGitRepo(String org, String repo, String branch = 'master')
  {
    configGitOrg_ = org
    configGitRepo_ = repo
    configGitBranch_ = branch
    
    return this
  }

  public void execute()
  {
    echo """
------------------------------------------------------------------------------------------------------------------------
CreateEnvironmentTask V2

awsRegion_          ${awsRegion_}
environmentType_    ${environmentType_}
environment_        ${environment_}
realm_              ${realm_}
region_             ${region_}
------------------------------------------------------------------------------------------------------------------------
"""
    String  accountId = 'fugue-' + environmentType_ + '-cicd'
    String  roleName  = 'fugue-' + environmentType_ + '-admin-role'
    
    pipeLine_.verifyUserAccess(accountId)
    
    withCredentials([[
      $class:             'AmazonWebServicesCredentialsBinding',
      accessKeyVariable:  'AWS_ACCESS_KEY_ID',
      credentialsId:      accountId,
      secretKeyVariable:  'AWS_SECRET_ACCESS_KEY']])
    {
      getOrCreateCluster()
      
      logGroup_ = pipeLine_.createLogGroup('fugue')
    
      FugueDeploy deploy = new FugueDeploy(pipeLine_, 'CreateEnvironment',
        logGroup_,
        awsRegion_)
          .withConfigGitRepo(configGitOrg_, configGitRepo_, configGitBranch_)
          .withEnvironmentType(environmentType_)
          .withEnvironment(environment_)
          .withRealm(realm_)
          .withRegion(region_)
          .withDockerLabel(dockerLabel_)
          .withRole(roleName)
          .withAccountId(accountId)
          .withCluster(cluster_)
        
      deploy.execute()
 
// No environment credentials actually needed.     
//      def creds = readJSON(text:
//        sh(returnStdout: true, script: 'aws secretsmanager get-secret-value --region us-east-1 --secret-id fugue-' + environmentType_ + '-' + environment_ + '-root-cred'))
//    
//      def secrets = readJSON(text: creds."SecretString")
//      
//      secrets.each
//      {
//          name, value ->
//            echo """
//---------------------------------------------------
//Store credential
//
//name          ${name}
//accessKeyId   ${value."accessKeyId"}
//---------------------------------------------------
//"""
//
//            CredentialHelper.saveCredential(steps_, name, value."accessKeyId", value."secretAccessKey")
//      }
    }
    
    echo """
------------------------------------------------------------------------------------------------------------------------
CreateEnvironmentTask Finished
------------------------------------------------------------------------------------------------------------------------
"""
  }
  
  public void getOrCreateCluster()
  {
    if(cluster_ == null)
      {
        cluster_ = pipeLine_.getEnvironmentTypeConfig(environmentType_).getClusterId()
        //      cluster_         = environmentType_ + '-' + environment_ + '-' + region_
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
