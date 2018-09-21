package org.symphonyoss.s2.fugue.cicd.v1

import java.util.Map.Entry

/**
 * Task to create an environment type, creating roles and other prerequisites if necessary.
 * 
 * @author Bruce Skingle
 *
 */
class CreateEnvironmentTypeTask implements Serializable
{
  private def           steps_
  private FuguePipeline pipeLine_
  
  private String  logGroup_
  private String  environmentType_
  private String  dockerLabel_      = 'latest'
  private String  awsRegion_        = 'us-east-1'
  private String  cluster_
  private String  clusterArn_
  
  public CreateEnvironmentTypeTask(steps, pipeLine, environmentType)
  {
      steps_           = steps
      pipeLine_        = pipeLine
      environmentType_ = environmentType
      cluster_         = 'fugue-' + environmentType_
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

  public void execute()
  {
    String  accountId = 'fugue-' + environmentType_ + '-root'
    String  roleName  = accountId + '-role'

    steps_.echo """
------------------------------------------------------------------------------------------------------------------------
CreateEnvironmentTypeTask V1

awsRegion_          ${awsRegion_}
environmentType_    ${environmentType_}
accountId           ${accountId}
roleName            ${roleName}
------------------------------------------------------------------------------------------------------------------------
"""
    
    pipeLine_.verifyUserAccess(accountId)
    
    steps_.withCredentials([[
      $class:             'AmazonWebServicesCredentialsBinding',
      accessKeyVariable:  'AWS_ACCESS_KEY_ID',
      credentialsId:      accountId,
      secretKeyVariable:  'AWS_SECRET_ACCESS_KEY']])
    {
      getOrCreateCluster()
      
      logGroup_ = pipeLine_.createLogGroup('fugue')
      pipeLine_.createRole(accountId, 'fugue-' + environmentType_ + "-root-policy", roleName)
    
      FugueDeploy deploy = new FugueDeploy(steps_, 'CreateEnvironmentType',
        logGroup_,
        pipeLine_.aws_identity[accountId].Account,
        cluster_,
        awsRegion_)
          .withConfigGitRepo('SymphonyOSF', 'S2-fugue-config', 'master')
          .withEnvironmentType(environmentType_)
          .withDockerLabel(dockerLabel_)
          .withRole(roleName)
          .withAccountId(accountId)
        
      deploy.execute()
      
      def creds = steps_.readJSON(text:
        steps_.sh(returnStdout: true, script: 'aws secretsmanager get-secret-value --region us-east-1 --secret-id fugue-' + environmentType_ + '-root-cred'))
    
      def secrets = steps_.readJSON(text: creds."SecretString")
      
      secrets.each
      {
          name, value ->
            steps_.echo """
---------------------------------------------------
Store credential

name          ${name}
accessKeyId   ${value."accessKeyId"}
---------------------------------------------------
"""

            CredentialHelper.saveCredential(steps_, name, value."accessKeyId", value."secretAccessKey")
      }
    }
    
    steps_.echo """
------------------------------------------------------------------------------------------------------------------------
CreateEnvironmentTypeTask Finished
------------------------------------------------------------------------------------------------------------------------
"""
  }
  
  public void getOrCreateCluster()
  {
    def clusters = steps_.readJSON(text:
      steps_.sh(returnStdout: true, script: 'aws ecs describe-clusters --region us-east-1 --clusters ' + cluster_ ))

    clusters."clusters" each
    {
      clusterArn, clusterName ->
        if(cluster_.equals(clusterName))
        {
          clusterArn_ = clusterArn
          steps_.echo 'clusterArn_ is ' + clusterArn_
          return
        }
    }
    
    steps_.echo 'Cluster ' + cluster_ + ' does not exist, creating...'
  }
}
