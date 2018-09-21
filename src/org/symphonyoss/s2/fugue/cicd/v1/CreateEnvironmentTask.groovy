package org.symphonyoss.s2.fugue.cicd.v1

import java.util.Map.Entry

/**
 * Task to create an environment type, creating roles and other prerequisites if necessary.
 * 
 * @author Bruce Skingle
 *
 */
class CreateEnvironmentTask implements Serializable
{
  private def           steps_
  private FuguePipeline pipeLine_
  
  private String  logGroup_
  private String  environmentType_
  private String  environment_
  private String  realm_
  private String  region_
  private String  dockerLabel_      = 'latest'
  private String  awsRegion_        = 'us-east-1'
  private String  cluster_

  public CreateEnvironmentTask(steps, pipeLine, String environmentType, String environment,
    String realm, String region)
  {
      steps_           = steps
      pipeLine_        = pipeLine
      environmentType_ = environmentType
      environment_     = environment
      realm_           = realm
      region_          = region
      cluster_         = 'fugue-' + environmentType_
      
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

  public void execute()
  {
    steps_.echo """
------------------------------------------------------------------------------------------------------------------------
CreateEnvironmentTask

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
    
    steps_.withCredentials([[
      $class:             'AmazonWebServicesCredentialsBinding',
      accessKeyVariable:  'AWS_ACCESS_KEY_ID',
      credentialsId:      accountId,
      secretKeyVariable:  'AWS_SECRET_ACCESS_KEY']])
    {
      logGroup_ = pipeLine_.createLogGroup('fugue')
    
      FugueDeploy deploy = new FugueDeploy(steps_, 'CreateEnvironment',
        logGroup_,
        pipeLine_.aws_identity[accountId].Account,
        cluster_,
        awsRegion_)
          .withConfigGitRepo('SymphonyOSF', 'S2-fugue-config', 'master')
          .withEnvironmentType(environmentType_)
          .withEnvironment(environment_)
          .withRealm(realm_)
          .withRegion(region_)
          .withDockerLabel(dockerLabel_)
          .withRole(roleName)
          .withAccountId(accountId)
        
      deploy.execute()
 
// No environment credentials actually needed.     
//      def creds = steps_.readJSON(text:
//        steps_.sh(returnStdout: true, script: 'aws secretsmanager get-secret-value --region us-east-1 --secret-id fugue-' + environmentType_ + '-' + environment_ + '-root-cred'))
//    
//      def secrets = steps_.readJSON(text: creds."SecretString")
//      
//      secrets.each
//      {
//          name, value ->
//            steps_.echo """
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
    
    steps_.echo """
------------------------------------------------------------------------------------------------------------------------
CreateEnvironmentTask Finished
------------------------------------------------------------------------------------------------------------------------
"""
  }
  
  
}
